package de.f4ked.data_center_addon.industry.manager;

import com.example.datacenter.api.DataCenterApi;
import de.f4ked.data_center_addon.industry.Commodity;
import de.f4ked.data_center_addon.industry.IndustryStats;
import de.f4ked.data_center_addon.industry.Product;
import de.f4ked.data_center_addon.industry.PurchaseTerminalBlockEntity;
import de.f4ked.data_center_addon.industry.SalesMarket;
import de.f4ked.data_center_addon.industry.SalesTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Server side of the Factory Manager: finds the factory machines and the viewer's terminals within
 * {@link #RADIUS} blocks and builds the {@link FactorySnapshot} with the factory's money of the last 30 days.
 */
public final class FactoryManagerService {
    /** How far around the manager machines and terminals are found, in blocks. */
    public static final int RADIUS = 32;
    private static final ResourceLocation NOTHING = ResourceLocation.withDefaultNamespace("air");

    private FactoryManagerService() {
    }

    public static FactorySnapshot snapshot(ServerLevel level, BlockPos manager, UUID account) {
        long budget = 0;
        int purchase = 0, sales = 0;
        List<FactorySnapshot.Machine> machines = new ArrayList<>();
        for (BlockEntity be : around(level, manager)) {
            if (be instanceof PurchaseTerminalBlockEntity t && account.equals(t.account())) {
                budget += t.budgetCents();
                purchase++;
            } else if (be instanceof SalesTerminalBlockEntity t && account.equals(t.account())) {
                sales++;
            } else if (be instanceof FactoryMachine m) {
                ItemStack out = m.factoryOutput();
                machines.add(new FactorySnapshot.Machine(be.getBlockPos(), BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()),
                        m.factoryState().ordinal(), out.isEmpty() ? NOTHING : BuiltInRegistries.ITEM.getKey(out.getItem()),
                        m.factoryEnergy()));
            }
        }
        machines.sort(Comparator.comparing((FactorySnapshot.Machine m) -> m.block().getPath())
                .thenComparingLong(m -> m.pos().asLong()));

        LocalDate date = DataCenterApi.currentDate(level);
        long today = date.toEpochDay();
        IndustryStats stats = IndustryStats.get(level.getServer());
        IndustryStats.Totals month = stats.totals(account, today, IndustryStats.DAYS);
        IndustryStats.Totals day = stats.totals(account, today, 1);
        SalesMarket market = SalesMarket.get(level.getServer());
        List<FactorySnapshot.Sale> saleRows = new ArrayList<>();
        for (Product p : Product.values()) {
            double demand = market.demand(account, p, today);
            saleRows.add(new FactorySnapshot.Sale(p.ordinal(), month.soldUnits(p), month.soldCents(p),
                    Math.round(p.baseCents(date) * demand), (int) Math.round(demand * 1000)));
        }
        List<FactorySnapshot.Purchase> buyRows = new ArrayList<>();
        for (Commodity c : Commodity.values()) {
            buyRows.add(new FactorySnapshot.Purchase(c.ordinal(), month.boughtUnits(c), month.boughtCents(c), c.priceCents(date)));
        }
        return new FactorySnapshot(budget, purchase, sales, month.spent(), month.revenue(), day.spent(), day.revenue(),
                List.copyOf(machines), List.copyOf(saleRows), List.copyOf(buyRows));
    }

    /** Block entities in loaded chunks within {@link #RADIUS} blocks of the manager. */
    private static List<BlockEntity> around(ServerLevel level, BlockPos center) {
        List<BlockEntity> out = new ArrayList<>();
        int minX = (center.getX() - RADIUS) >> 4, maxX = (center.getX() + RADIUS) >> 4;
        int minZ = (center.getZ() - RADIUS) >> 4, maxZ = (center.getZ() + RADIUS) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - center.getX()) <= RADIUS && Math.abs(p.getY() - center.getY()) <= RADIUS
                            && Math.abs(p.getZ() - center.getZ()) <= RADIUS) {
                        out.add(be);
                    }
                }
            }
        }
        return out;
    }
}
