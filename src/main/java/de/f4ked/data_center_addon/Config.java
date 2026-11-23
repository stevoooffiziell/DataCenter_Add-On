package de.f4ked.data_center_addon;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;

/**
 * Common config, stored in {@code config/data_center_addon-common.toml}.
 * <p>
 * Part definitions are data-pack registry entries, so changes to the part options only take effect
 * the next time a world is loaded (leave the world and join it again).
 */
public final class Config {
    /** Shop categories whose original Data Center parts are hidden by default when hiding is on. */
    public static final List<String> DEFAULT_HIDDEN_CATEGORIES = List.of(
            "CPU_AMD", "CPU_INTEL", "CPU_SERVER",
            "GPU_NVIDIA", "GPU_AMD", "GPU_DATACENTER", "GPU_MINING",
            "RAM", "STORAGE", "PSU", "MAINBOARD", "MAINBOARD_MINING");

    /**
     * The default up to version 1.1.0, when the add-on had no real board for every socket yet. A config
     * that still holds exactly this list is moved to the new default once ({@link #onLoad}).
     */
    private static final List<String> OLD_DEFAULT_HIDDEN_CATEGORIES = List.of(
            "CPU_AMD", "CPU_INTEL", "CPU_SERVER",
            "GPU_NVIDIA", "GPU_AMD", "GPU_DATACENTER", "GPU_MINING",
            "RAM", "STORAGE", "PSU");

    private static final List<String> ALL_PART_CATEGORIES = List.of(
            "CPU_AMD", "CPU_INTEL", "CPU_SERVER",
            "GPU_NVIDIA", "GPU_AMD", "GPU_DATACENTER", "GPU_MINING",
            "RAM", "STORAGE", "PSU", "MAINBOARD", "MAINBOARD_MINING", "FAN");

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue HIDE_ORIGINAL_PARTS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_CATEGORIES;
    private static final ModConfigSpec.DoubleValue HOSTING_STORAGE_BOOST;
    private static final ModConfigSpec.BooleanValue HIDE_ORIGINAL_NETWORK;
    private static final ModConfigSpec.BooleanValue SWITCHES_NEED_POWER;
    private static final ModConfigSpec.BooleanValue CABLE_REACH_LIMITS;
    private static final ModConfigSpec.BooleanValue INSTALL_QUESTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Real computer parts added by this add-on.").push("parts");
        HIDE_ORIGINAL_PARTS = builder
                .comment("Hide Data Center's own (fictional brand) parts from the Shop, so only the real parts",
                        "of this add-on are for sale. Parts you already own keep working.",
                        "Takes effect the next time a world is loaded.")
                .define("hideOriginalParts", false);
        HIDDEN_CATEGORIES = builder
                .comment("Shop categories in which the original parts are hidden when hideOriginalParts is true.",
                        "Fans are kept by default.",
                        "Allowed: " + String.join(", ", ALL_PART_CATEGORIES))
                .defineListAllowEmpty("hiddenCategories", DEFAULT_HIDDEN_CATEGORIES, () -> "CPU_AMD",
                        o -> o instanceof String s && ALL_PART_CATEGORIES.contains(s));
        builder.pop();

        builder.comment("Hosting racks.").push("hosting");
        HOSTING_STORAGE_BOOST = builder
                .comment("Capacity multiplier for this add-on's storage parts while their server runs in a Hosting Rack",
                        "(more storage to rent out in dedicated mode, more shared capacity in shared mode).",
                        "1.0 turns the boost off.")
                .defineInRange("storageBoost", 1.10, 1.0, 2.0);
        builder.pop();

        builder.comment("Network cables (CAT, fibre) and switches added by this add-on.").push("network");
        HIDE_ORIGINAL_NETWORK = builder
                .comment("Hide Data Center's own cables and switches: its switch leaves the Shop and its cable recipes",
                        "are disabled. Cables and switches already placed keep working.",
                        "Takes effect the next time a world is loaded.")
                .define("hideOriginalNetwork", false);
        SWITCHES_NEED_POWER = builder
                .comment("Switches need power (FE, their real wattage). A switch without power cuts its network.")
                .define("switchesNeedPower", true);
        CABLE_REACH_LIMITS = builder
                .comment("A cable run longer than the cable's real reach (e.g. 100 blocks for CAT5e, 30 for CAT8)",
                        "falls back to a tenth of its speed. Switches start a new run.")
                .define("cableReachLimits", true);
        builder.pop();

        builder.comment("FTB Quests guide and release calendar.").push("quests");
        INSTALL_QUESTS = builder
                .comment("Add the add-on's chapters (a guide and a release calendar) to the FTB Quests quest book when a",
                        "world starts. They are only rewritten when the add-on brings a new version of them; other",
                        "chapters are never touched. Turn this off to keep your own edits of these chapters.")
                .define("installQuests", true);
        builder.pop();

        SPEC = builder.build();
    }

    private Config() {
    }

    /**
     * Configs written by 1.1.0 and older kept Data Center's boards. If the list is still that old default
     * (the player never changed it), it becomes the new default, which hides the original boards too.
     */
    public static void onLoad(ModConfigEvent.Loading event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() != SPEC) {
            return;
        }
        if (new HashSet<>(HIDDEN_CATEGORIES.get()).equals(new HashSet<>(OLD_DEFAULT_HIDDEN_CATEGORIES))) {
            HIDDEN_CATEGORIES.set(DEFAULT_HIDDEN_CATEGORIES);
            SPEC.save();
        }
    }

    public static boolean hidesOriginal(String category) {
        if (!SPEC.isLoaded()) {
            return false;
        }
        return HIDE_ORIGINAL_PARTS.get() && HIDDEN_CATEGORIES.get().contains(category);
    }

    public static boolean hidesOriginalNetwork() {
        return SPEC.isLoaded() && HIDE_ORIGINAL_NETWORK.get();
    }

    public static boolean switchesNeedPower() {
        return !SPEC.isLoaded() || SWITCHES_NEED_POWER.get();
    }

    public static boolean cableReachLimits() {
        return !SPEC.isLoaded() || CABLE_REACH_LIMITS.get();
    }

    public static boolean installQuests() {
        return !SPEC.isLoaded() || INSTALL_QUESTS.get();
    }

    public static double hostingStorageBoost() {
        return SPEC.isLoaded() ? HOSTING_STORAGE_BOOST.get() : 1.0;
    }
}
