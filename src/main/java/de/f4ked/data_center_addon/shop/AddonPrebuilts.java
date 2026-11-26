package de.f4ked.data_center_addon.shop;

import com.example.datacenter.catalog.PartPacks;
import com.example.datacenter.component.FanSpec;
import com.example.datacenter.component.MainboardSpec;
import com.example.datacenter.component.ProcessorKind;
import com.example.datacenter.component.ProcessorSpec;
import com.example.datacenter.component.PsuSpec;
import com.example.datacenter.component.RamSpec;
import com.example.datacenter.component.StorageSpec;
import com.example.datacenter.hosting.HostingModeRates;
import com.example.datacenter.hosting.ServerResources;
import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.registry.ModItems;
import com.example.datacenter.server.ServerLayout;
import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopCategory;
import com.example.datacenter.shop.ShopEntry;
import com.example.datacenter.shop.ShopPricing;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The add-on's pre-built servers, in place of Data Center's own (which {@code PrebuiltServersMixin} and
 * {@code EarlyServersMixin} no longer register).
 * <p>
 * Every quarter from 1998 on brings one model of each {@link PrebuiltLine}, composed from the parts on
 * the shelf at that date (released in the last {@link #SHELF_YEARS} years): the shop's pre-built tab
 * changes with the game date. A model only sells until the next quarter's model of its line; when the
 * best build of a line stays the same, the model simply sells on.
 * <p>
 * The builds are made for hosting: the hosting lines pick CPU and board for the most customers per dollar
 * (or per watt) with the hosting rack's own capacity formula at that date, and give them as much RAM and
 * disk as that compute can use. Parts from data packs (the add-on's real parts) count as well as Data
 * Center's; parts a data pack hides do not.
 * <p>
 * The servers are composed whenever the data-pack parts change ({@code ShopCatalogMixin}), on the server
 * and on every client from the same parts, so both get the same list.
 */
public final class AddonPrebuilts {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final LocalDate FIRST = LocalDate.of(1998, 1, 1);
    static final LocalDate LAST = LocalDate.of(2026, 1, 1);
    static final int WINDOW_MONTHS = 3;
    static final int SHELF_YEARS = 3;
    static final double PSU_HEADROOM = 1.25;
    private static final double BIG = 1e15;
    private static final String DUMP_PROPERTY = "data_center_addon.dumpPrebuilts";

    private static volatile Map<ShopEntry, Build> builds = Map.of();
    @Nullable
    private static volatile ShopEntry chassis;

    record Part(ShopEntry entry, int count) {
    }

    record Build(PrebuiltLine line, List<Part> parts, LocalDate until) {
    }

    /** A shop part with what the composer needs of it. */
    record Item(ShopEntry entry, Object spec, long price, LocalDate date, boolean early, int ramMb) {
        ProcessorSpec proc() {
            return (ProcessorSpec) spec;
        }

        MainboardSpec board() {
            return (MainboardSpec) spec;
        }
    }

    /** A complete build and how well it does what its line is for. */
    private record Candidate(List<Part> parts, long cost, double score) {
    }

    private AddonPrebuilts() {
    }

    public static boolean owns(ShopEntry entry) {
        return builds.containsKey(entry);
    }

    public static boolean onSale(ShopEntry entry, LocalDate today) {
        Build build = builds.get(entry);
        return build != null && !today.isBefore(entry.releaseDate()) && today.isBefore(build.until());
    }

    /** Today's price: the chassis and the parts at today's shop prices, with the line's markup. */
    public static long priceUsdCents(ShopEntry entry, Level level) {
        Build build = builds.get(entry);
        if (build == null) {
            return entry.priceUsdCents();
        }
        long total = chassisEntry().map(e -> ShopPricing.currentPriceUsdCents(e, level)).orElse(0L);
        for (Part part : build.parts()) {
            total += ShopPricing.currentPriceUsdCents(part.entry(), level) * part.count();
        }
        return Math.max(1L, Math.round(total * build.line().markup));
    }

    private static Optional<ShopEntry> chassisEntry() {
        ShopEntry c = chassis;
        if (c == null) {
            for (ShopEntry e : ShopCatalog.staticEntries()) {
                if (e.partId() == null && e.category() != ShopCategory.USED && e.category() != ShopCategory.PREBUILT
                        && e.createStack().getItem() == ModItems.SERVER.get()) {
                    chassis = c = e;
                    break;
                }
            }
        }
        return Optional.ofNullable(c);
    }

    /**
     * The data-pack shop lines with the pre-built servers added (Data Center appends both to its static
     * entries). Composes the servers anew from the static parts and these lines.
     */
    public static List<ShopEntry> withPrebuilts(List<ShopEntry> lines) {
        try {
            Map<ShopEntry, Build> next = new IdentityHashMap<>();
            List<ShopEntry> entries = compose(lines, next);
            List<ShopEntry> out = new ArrayList<>(lines.size() + entries.size());
            out.addAll(lines);
            out.addAll(entries);
            builds = Collections.unmodifiableMap(next);
            return out;
        } catch (RuntimeException e) {
            LOGGER.error("Shop: the add-on's pre-built servers could not be composed", e);
            builds = Map.of();
            return lines;
        }
    }

    // ------------------------------------------------------------------ composing

    private static final class Planned {
        final PrebuiltLine line;
        final List<Part> parts;
        final LocalDate from;
        LocalDate until;

        Planned(PrebuiltLine line, List<Part> parts, LocalDate from, LocalDate until) {
            this.line = line;
            this.parts = parts;
            this.from = from;
            this.until = until;
        }
    }

    private static List<ShopEntry> compose(List<ShopEntry> lines, Map<ShopEntry, Build> out) {
        List<Item> catalog = catalog(lines);
        long chassisCents = chassisEntry().map(ShopEntry::priceUsdCents).orElse(0L);
        List<Planned> planned = new ArrayList<>();
        Map<PrebuiltLine, Planned> last = new EnumMap<>(PrebuiltLine.class);
        int windows = 0;
        for (LocalDate from = FIRST; !from.isAfter(LAST); from = from.plusMonths(WINDOW_MONTHS)) {
            LocalDate until = from.equals(LAST) || from.plusMonths(WINDOW_MONTHS).isAfter(LAST) ? LocalDate.MAX
                    : from.plusMonths(WINDOW_MONTHS);
            Shelf shelf = new Shelf(catalog, from, chassisCents);
            windows++;
            for (PrebuiltLine line : PrebuiltLine.values()) {
                Candidate best = line == PrebuiltLine.STORAGE ? storage(shelf) : hosting(line, shelf);
                if (best == null) {
                    continue;
                }
                Planned prev = last.get(line);
                if (prev != null && prev.until.equals(from) && prev.parts.equals(best.parts())) {
                    prev.until = until;
                    continue;
                }
                Planned p = new Planned(line, best.parts(), from, until);
                planned.add(p);
                last.put(line, p);
            }
        }

        boolean dump = Boolean.getBoolean(DUMP_PROPERTY);
        List<ShopEntry> entries = new ArrayList<>(planned.size());
        for (Planned p : planned) {
            long launch = chassisCents;
            for (Part part : p.parts) {
                launch += part.entry().priceUsdCents() * part.count();
            }
            Build build = new Build(p.line, List.copyOf(p.parts), p.until);
            ShopEntry entry = new ShopEntry(ShopCategory.PREBUILT, () -> stack(build),
                    Math.max(1L, Math.round(launch * p.line.markup)), p.from);
            out.put(entry, build);
            entries.add(entry);
            if (dump) {
                LOGGER.info("Pre-built {} {} until {}: ${} {}", p.from, p.line, p.until, launch / 100,
                        describe(p.parts));
            }
        }
        LOGGER.info("Shop: {} pre-built servers of the add-on over {} quarters ({} parts considered).",
                entries.size(), windows, catalog.size());
        return entries;
    }

    private static String describe(List<Part> parts) {
        StringBuilder s = new StringBuilder();
        for (Part part : parts) {
            if (!s.isEmpty()) {
                s.append(", ");
            }
            s.append(part.count()).append("x ").append(part.entry().createStack().getHoverName().getString());
        }
        return s.toString();
    }

    /** Every part the shop sells new, with its spec. */
    private static List<Item> catalog(List<ShopEntry> lines) {
        List<Item> items = new ArrayList<>();
        List<ShopEntry> all = new ArrayList<>(ShopCatalog.staticEntries());
        all.addAll(lines);
        for (ShopEntry e : all) {
            ShopCategory c = e.category();
            if (c == ShopCategory.USED || c == ShopCategory.PREBUILT || c == ShopCategory.FPGA
                    || c == ShopCategory.ASIC || c == ShopCategory.MAINBOARD_MINING || c == ShopCategory.GPU_MINING
                    || c.isVendor() || PartPacks.hidden(e) || ShopCatalog.retired(e)) {
                continue;
            }
            ItemStack stack;
            Object spec;
            try {
                stack = e.createStack();
                spec = PartPacks.spec(stack);
            } catch (RuntimeException ex) {
                continue;
            }
            if (stack.isEmpty() || !(spec instanceof ProcessorSpec || spec instanceof MainboardSpec
                    || spec instanceof RamSpec || spec instanceof StorageSpec || spec instanceof PsuSpec
                    || spec instanceof FanSpec)) {
                continue;
            }
            if (spec instanceof ProcessorSpec p && p.getKind() != ProcessorKind.CPU && p.getKind() != ProcessorKind.GPU) {
                continue;
            }
            int ramMb = spec instanceof RamSpec ? ServerResources.ramMb(stack) : 0;
            items.add(new Item(e, spec, Math.max(1L, e.priceUsdCents()), e.releaseDate(), ShopCatalog.early(e), ramMb));
        }
        return items;
    }

    /** The parts on sale in one quarter, cheapest first. */
    private static final class Shelf {
        final LocalDate date;
        final long chassis;
        final List<Item> cpus, boards, rams, disks, psus, fans, gpus;

        Shelf(List<Item> catalog, LocalDate date, long chassis) {
            this.date = date;
            this.chassis = chassis;
            boolean early = date.isBefore(MiningEra.START_DATE);
            List<Item> pool = new ArrayList<>();
            for (Item i : catalog) {
                if (early || !i.early()) {
                    pool.add(i);
                }
            }
            cpus = on(pool, i -> i.spec() instanceof ProcessorSpec p && p.getKind() == ProcessorKind.CPU
                    && p.getComputePerTick() > 0 && p.getSocket() != null);
            boards = on(pool, i -> i.spec() instanceof MainboardSpec b && i.entry().category() == ShopCategory.MAINBOARD
                    && b.getCpuSlots() > 0 && b.getRamSlots() > 0 && b.getStorageSlots() > 0);
            rams = on(pool, i -> i.spec() instanceof RamSpec && i.ramMb() > 0);
            disks = on(pool, i -> i.spec() instanceof StorageSpec s && s.getCapacityGb() > 0);
            psus = on(pool, i -> i.spec() instanceof PsuSpec p && !p.isTwelveVoltOnly() && p.getMaxPowerThroughput() > 0);
            fans = on(pool, i -> i.spec() instanceof FanSpec);
            gpus = on(pool, i -> i.spec() instanceof ProcessorSpec p && p.getKind() == ProcessorKind.GPU
                    && p.isGeneralPurpose() && p.getComputePerTick() > 0);
        }

        /** The parts of a kind released in the last years; if there are none, the newest one released. */
        private List<Item> on(List<Item> pool, Predicate<Item> kind) {
            List<Item> out = new ArrayList<>();
            Item newest = null;
            for (Item i : pool) {
                if (i.date().isAfter(date) || !kind.test(i)) {
                    continue;
                }
                if (date.isBefore(i.date().plusYears(SHELF_YEARS))) {
                    out.add(i);
                }
                if (newest == null || i.date().isAfter(newest.date())
                        || i.date().equals(newest.date()) && i.price() > newest.price()) {
                    newest = i;
                }
            }
            if (out.isEmpty() && newest != null) {
                out.add(newest);
            }
            out.sort(Comparator.comparingLong(Item::price).thenComparing(Item::date));
            return out;
        }
    }

    private static boolean fits(Item cpu, Item board) {
        return cpu.proc().getSocket().fits(board.board().getSocket());
    }

    private static double capacity(PrebuiltLine line, LocalDate date, double compute, double ramMb, double diskGb) {
        return switch (line) {
            case VPS -> HostingModeRates.vpsCapacity(date, compute, ramMb, diskGb);
            case GAME -> HostingModeRates.gameCapacity(date, compute, ramMb, diskGb);
            default -> HostingModeRates.sharedCapacity(date, compute, ramMb, diskGb);
        };
    }

    /** The cheapest complete server of the quarter, the yardstick for the early-game lines' price cap. */
    private static long cheapest(Shelf w) {
        long best = Long.MAX_VALUE;
        Item disk = w.disks.isEmpty() ? null : w.disks.get(0);
        if (disk == null) {
            return best;
        }
        for (Item cpu : w.cpus) {
            for (Item board : w.boards) {
                if (!fits(cpu, board)) {
                    continue;
                }
                MainboardSpec b = board.board();
                Item ram = w.rams.stream().filter(r -> b.supportsRam(((RamSpec) r.spec()).getGeneration())).findFirst().orElse(null);
                if (ram == null) {
                    continue;
                }
                List<Part> psu = supplies(w, b, cpu.proc().getPowerDrawPerTick());
                if (psu == null) {
                    continue;
                }
                long cost = w.chassis + board.price() + cpu.price() + ram.price() + disk.price() + cost(psu)
                        + (b.getFanSlots() > 0 && !w.fans.isEmpty() ? w.fans.get(0).price() : 0);
                best = Math.min(best, cost);
            }
        }
        return best;
    }

    @Nullable
    private static Candidate hosting(PrebuiltLine line, Shelf w) {
        long cap = Long.MAX_VALUE;
        if (line.costCap > 0) {
            long cheapest = cheapest(w);
            if (cheapest == Long.MAX_VALUE) {
                return null;
            }
            cap = Math.round(cheapest * line.costCap);
        }
        Candidate best = null;
        for (Item cpu : w.cpus) {
            for (Item board : w.boards) {
                if (!fits(cpu, board)) {
                    continue;
                }
                int most = Math.min(line.sockets, Math.min(2, board.board().getCpuSlots()));
                for (int sockets = 1; sockets <= most; sockets++) {
                    Candidate c = fill(line, w, cpu, board, sockets);
                    if (c == null || c.cost() > cap) {
                        continue;
                    }
                    if (best == null || c.score() > best.score() || c.score() == best.score() && c.cost() < best.cost()) {
                        best = c;
                    }
                }
            }
        }
        return best;
    }

    /** A hosting build around one CPU and board, with RAM and disks to match the compute. */
    @Nullable
    private static Candidate fill(PrebuiltLine line, Shelf w, Item cpuItem, Item boardItem, int sockets) {
        ProcessorSpec cpu = cpuItem.proc();
        MainboardSpec board = boardItem.board();
        double bandwidth = cpu.getMemoryBandwidthMultiplier() > 0 ? cpu.getMemoryBandwidthMultiplier() : 1.0;
        double compute = cpu.getComputePerTick() * sockets * bandwidth;
        double dcCompute = cpu.isDatacenterGrade() ? compute : 0;
        int draw = cpu.getPowerDrawPerTick() * sockets;
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(boardItem.entry(), 1));
        parts.add(new Part(cpuItem.entry(), sockets));
        long cost = w.chassis + boardItem.price() + cpuItem.price() * sockets;

        if (line == PrebuiltLine.DEDICATED) {
            int slots = Math.min(4, board.getNativeGpuSlots());
            if (slots > 0) {
                double worth = compute + 2 * dcCompute;
                Item card = null;
                double cardValue = worth / cost;
                for (Item g : w.gpus) {
                    ProcessorSpec p = g.proc();
                    double bw = p.getMemoryBandwidthMultiplier() > 0 ? p.getMemoryBandwidthMultiplier() : 1.0;
                    double v = p.getComputePerTick() * bw * (p.isDatacenterGrade() ? 3 : 1) / g.price();
                    if (v > cardValue) {
                        cardValue = v;
                        card = g;
                    }
                }
                if (card != null) {
                    ProcessorSpec p = card.proc();
                    double bw = p.getMemoryBandwidthMultiplier() > 0 ? p.getMemoryBandwidthMultiplier() : 1.0;
                    double add = p.getComputePerTick() * bw * slots;
                    compute += add;
                    if (p.isDatacenterGrade()) {
                        dcCompute += add;
                    }
                    draw += p.getPowerDrawPerTick() * slots;
                    parts.add(new Part(card.entry(), slots));
                    cost += card.price() * slots;
                }
            }
        }

        // RAM: the dedicated line wants the best compute multiplier, the others as much as the compute can use
        int ramSlots = Math.min(16, board.getRamSlots());
        List<Item> rams = new ArrayList<>();
        for (Item r : w.rams) {
            if (board.supportsRam(((RamSpec) r.spec()).getGeneration())) {
                rams.add(r);
            }
        }
        if (rams.isEmpty()) {
            return null;
        }
        Item ram;
        int sticks;
        if (line == PrebuiltLine.DEDICATED) {
            sticks = Math.min(2, ramSlots);
            Item bestRam = null;
            double bestValue = -1;
            for (Item r : rams) {
                double v = compute * Math.max(1f, ((RamSpec) r.spec()).getComputeMultiplier()) / (cost + r.price() * sticks);
                if (v > bestValue) {
                    bestValue = v;
                    bestRam = r;
                }
            }
            ram = bestRam;
        } else {
            double units = capacity(line, w.date, compute, BIG, BIG);
            double need = units / Math.max(1e-12, capacity(line, w.date, BIG, 1, BIG));
            Item[] pick = new Item[1];
            sticks = matching(rams, need, ramSlots, Item::ramMb, pick);
            ram = pick[0];
        }
        float multiplier = Math.max(1f, ((RamSpec) ram.spec()).getComputeMultiplier());
        compute *= multiplier;
        dcCompute *= multiplier;
        parts.add(new Part(ram.entry(), sticks));
        cost += ram.price() * sticks;
        double ramMb = (double) ram.ramMb() * sticks;

        // disks
        int bays = Math.min(12, board.getStorageSlots());
        Item disk;
        int drives;
        if (line == PrebuiltLine.DEDICATED) {
            disk = w.disks.isEmpty() ? null : w.disks.get(0);
            drives = 1;
        } else {
            double units = capacity(line, w.date, compute, ramMb, BIG);
            double need = units / Math.max(1e-12, capacity(line, w.date, BIG, BIG, 1));
            Item[] pick = new Item[1];
            drives = matching(w.disks, need, bays, i -> ((StorageSpec) i.spec()).getCapacityGb(), pick);
            disk = pick[0];
        }
        if (disk == null) {
            return null;
        }
        parts.add(new Part(disk.entry(), drives));
        cost += disk.price() * drives;
        double diskGb = (double) ((StorageSpec) disk.spec()).getCapacityGb() * drives;

        // cooling and power
        int fanCount = Math.min(board.getFanSlots(), line.earlyGame() ? 1 : 2);
        if (fanCount > 0 && !w.fans.isEmpty()) {
            parts.add(new Part(w.fans.get(0).entry(), fanCount));
            cost += w.fans.get(0).price() * fanCount;
        }
        List<Part> psu = supplies(w, board, draw);
        if (psu == null) {
            return null;
        }
        parts.addAll(psu);
        cost += cost(psu);

        double score;
        if (line == PrebuiltLine.DEDICATED) {
            score = (compute + 2 * dcCompute) / cost;
        } else {
            double units = capacity(line, w.date, compute, ramMb, diskGb);
            if (units <= 0) {
                return null;
            }
            score = switch (line) {
                case ECO -> units / Math.max(1, draw);
                // dense: many customers in one server (rack space is short), still paid for per dollar
                case WEB -> units * units / cost;
                default -> units / cost;
            };
        }
        return new Candidate(parts, cost, score);
    }

    /**
     * The cheapest way to get {@code need} (MB, GB) from one kind of part in {@code slots} slots; if no
     * kind gets there, the kind that gets furthest. Returns the count, the part in {@code pick[0]}.
     */
    private static int matching(List<Item> options, double need, int slots, java.util.function.ToDoubleFunction<Item> size,
                                Item[] pick) {
        if (slots <= 0) {
            return 0;
        }
        Item bestCovering = null;
        int coveringCount = 0;
        long coveringCost = Long.MAX_VALUE;
        Item bestShort = null;
        int shortCount = 0;
        double shortSize = -1;
        for (Item i : options) {
            double each = size.applyAsDouble(i);
            if (each <= 0) {
                continue;
            }
            int n = (int) Math.max(1, Math.min(slots, Math.ceil(need / each - 1e-9)));
            if (n * each >= need) {
                long c = i.price() * n;
                if (c < coveringCost) {
                    coveringCost = c;
                    bestCovering = i;
                    coveringCount = n;
                }
            } else if (n * each > shortSize) {
                shortSize = n * each;
                bestShort = i;
                shortCount = n;
            }
        }
        if (bestCovering != null) {
            pick[0] = bestCovering;
            return coveringCount;
        }
        pick[0] = bestShort;
        return shortCount;
    }

    /** Storage rent: the board with the most bays per dollar, filled with the drives that give the most GB per dollar. */
    @Nullable
    private static Candidate storage(Shelf w) {
        Item disk = null;
        double perDollar = 0;
        for (Item d : w.disks) {
            double v = (double) ((StorageSpec) d.spec()).getCapacityGb() / d.price();
            if (v > perDollar) {
                perDollar = v;
                disk = d;
            }
        }
        if (disk == null) {
            return null;
        }
        Candidate best = null;
        for (Item board : w.boards) {
            MainboardSpec b = board.board();
            Item cpu = w.cpus.stream().filter(c -> fits(c, board)).findFirst().orElse(null);
            Item ram = w.rams.stream().filter(r -> b.supportsRam(((RamSpec) r.spec()).getGeneration())).findFirst().orElse(null);
            if (cpu == null || ram == null) {
                continue;
            }
            int bays = Math.min(12, b.getStorageSlots());
            int sticks = Math.min(2, b.getRamSlots());
            List<Part> parts = new ArrayList<>();
            parts.add(new Part(board.entry(), 1));
            parts.add(new Part(cpu.entry(), 1));
            parts.add(new Part(ram.entry(), sticks));
            parts.add(new Part(disk.entry(), bays));
            long cost = w.chassis + board.price() + cpu.price() + ram.price() * sticks + disk.price() * bays;
            int fans = Math.min(2, b.getFanSlots());
            if (fans > 0 && !w.fans.isEmpty()) {
                parts.add(new Part(w.fans.get(0).entry(), fans));
                cost += w.fans.get(0).price() * fans;
            }
            List<Part> psu = supplies(w, b, cpu.proc().getPowerDrawPerTick());
            if (psu == null) {
                continue;
            }
            parts.addAll(psu);
            cost += cost(psu);
            double score = (double) ((StorageSpec) disk.spec()).getCapacityGb() * bays / cost;
            if (best == null || score > best.score()) {
                best = new Candidate(parts, cost, score);
            }
        }
        return best;
    }

    /**
     * Power supplies for {@code draw} with some headroom: the cheapest one that covers it, or as many of
     * the biggest as the board takes. Null if the board needs a supply and none is on sale.
     */
    @Nullable
    private static List<Part> supplies(Shelf w, MainboardSpec board, int draw) {
        int slots = Math.min(4, board.getPsuSlots());
        if (slots <= 0) {
            return List.of();
        }
        if (w.psus.isEmpty()) {
            return null;
        }
        int needed = (int) Math.ceil(draw * PSU_HEADROOM);
        for (Item p : w.psus) {
            if (((PsuSpec) p.spec()).getMaxPowerThroughput() >= needed) {
                return List.of(new Part(p.entry(), 1));
            }
        }
        Item biggest = w.psus.stream()
                .max(Comparator.comparingInt(p -> ((PsuSpec) p.spec()).getMaxPowerThroughput())).orElseThrow();
        int each = ((PsuSpec) biggest.spec()).getMaxPowerThroughput();
        return List.of(new Part(biggest.entry(), Math.max(1, Math.min(slots, (needed + each - 1) / each))));
    }

    private static long cost(List<Part> parts) {
        long c = 0;
        for (Part p : parts) {
            c += Math.max(1L, p.entry().priceUsdCents()) * p.count();
        }
        return c;
    }

    // ------------------------------------------------------------------ the item

    /** The server item: Data Center's server with the parts fitted, named after its line and main part. */
    static ItemStack stack(Build build) {
        NonNullList<ItemStack> slots = NonNullList.withSize(ServerLayout.TOTAL_SLOTS, ItemStack.EMPTY);
        Component cpuName = null;
        Component diskName = null;
        int cpu = 0, gpu = 0, ram = 0, psu = 0, drive = 0, fan = 0;
        for (Part part : build.parts()) {
            for (int n = 0; n < part.count(); n++) {
                ItemStack stack = part.entry().createStack();
                Object spec = PartPacks.spec(stack);
                if (spec instanceof MainboardSpec) {
                    slots.set(ServerLayout.SLOT_MAINBOARD, stack);
                } else if (spec instanceof ProcessorSpec p && p.getKind() == ProcessorKind.CPU) {
                    if (cpu < ServerLayout.MAX_CPU) {
                        cpuName = stack.getHoverName();
                        slots.set(ServerLayout.cpuSlot(cpu++), stack);
                    }
                } else if (spec instanceof ProcessorSpec) {
                    if (gpu < ServerLayout.MAX_GPU) {
                        slots.set(ServerLayout.gpuSlot(gpu++), stack);
                    }
                } else if (spec instanceof RamSpec) {
                    if (ram < ServerLayout.MAX_RAM) {
                        slots.set(ServerLayout.ramSlot(ram++), stack);
                    }
                } else if (spec instanceof PsuSpec) {
                    if (psu < ServerLayout.MAX_PSU) {
                        slots.set(ServerLayout.psuSlot(psu++), stack);
                    }
                } else if (spec instanceof StorageSpec) {
                    if (drive < ServerLayout.MAX_STORAGE) {
                        diskName = stack.getHoverName();
                        slots.set(ServerLayout.storageSlot(drive++), stack);
                    }
                } else if (spec instanceof FanSpec) {
                    if (fan < ServerLayout.MAX_FAN) {
                        slots.set(ServerLayout.fanSlot(fan++), stack);
                    }
                }
            }
        }
        ItemStack server = new ItemStack(ModItems.SERVER.get());
        server.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
        Component main = build.line() == PrebuiltLine.STORAGE && diskName != null
                ? Component.literal(drive + "x ").append(diskName)
                : cpu > 1 && cpuName != null ? Component.literal(cpu + "x ").append(cpuName)
                : cpuName != null ? cpuName : Component.empty();
        server.set(DataComponents.ITEM_NAME, Component.translatableWithFallback(build.line().nameKey(), build.line().fallback(), main));
        server.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(build.line().loreKey())
                .withStyle(s -> s.withColor(build.line().earlyGame() ? ChatFormatting.GREEN : ChatFormatting.GRAY).withItalic(false)))));
        return server;
    }
}
