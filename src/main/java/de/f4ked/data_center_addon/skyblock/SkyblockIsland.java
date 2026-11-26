package de.f4ked.data_center_addon.skyblock;

import de.f4ked.data_center_addon.Data_center_addon;
import com.example.datacenter.StarterKit;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import org.slf4j.Logger;

import java.util.Optional;

/**
 * The "Data Center SkyBlock" world type ({@code worldgen/world_preset/skyblock.json}): a void overworld
 * whose only biome is the add-on's {@code skyblock} biome. On the first start of such a world the
 * starter island ({@code structure/skyblock_island.nbt}, built by {@code tools/generate-skyblock.py}) is
 * placed with its middle at 0, 0, the world spawn is set on its grass and the spawn radius to 0, so
 * nobody spawns over the void. The island is placed once per world. Every player gets Data Center's
 * starter kit on the first login.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public final class SkyblockIsland {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "skyblock"));
    static final ResourceLocation STRUCTURE = ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "skyblock_island");
    /** Where the structure's corner goes, and the spawn on the grass relative to it. */
    static final BlockPos ORIGIN = new BlockPos(-7, 60, -5);
    static final BlockPos SPAWN = ORIGIN.offset(5, 5, 8);

    private SkyblockIsland() {
    }

    /** Whether the overworld is of the SkyBlock world type. */
    public static boolean isSkyblock(ServerLevel overworld) {
        return overworld.getChunkSource().getGenerator() instanceof FlatLevelSource flat
                && flat.settings().getBiome().is(BIOME);
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (!isSkyblock(overworld)) {
            return;
        }
        State state = State.get(server);
        if (state.placed) {
            return;
        }
        Optional<StructureTemplate> template = server.getStructureManager().get(STRUCTURE);
        if (template.isEmpty()) {
            LOGGER.error("SkyBlock: the starter island {} is missing", STRUCTURE);
            return;
        }
        template.get().placeInWorld(overworld, ORIGIN, ORIGIN, new StructurePlaceSettings(), overworld.getRandom(),
                Block.UPDATE_CLIENTS);
        overworld.setDefaultSpawnPos(SPAWN, -90f);
        overworld.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, server);
        state.placed = true;
        state.setDirty();
        LOGGER.info("SkyBlock: starter island placed at {}", ORIGIN);
    }

    /**
     * Each player's first login on the island: Data Center's starter kit (Data Center itself hands it out
     * only by command or for a SkyWorlds island; {@code grant} gives it once per player, the add-on adds
     * the Hosting Manager), and a greeting.
     */
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isSkyblock(player.serverLevel().getServer().overworld())) {
            return;
        }
        if (com.example.datacenter.Config.GIVE_STARTER_KIT.get()) {
            StarterKit.grant(player);
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean("data_center_addon_skyblock_greeted")) {
            return;
        }
        data.putBoolean("data_center_addon_skyblock_greeted", true);
        player.sendSystemMessage(Component.translatable("generator.data_center_addon.skyblock").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(": ").withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("data_center_addon.skyblock.welcome").withStyle(ChatFormatting.GRAY)));
    }

    /** Whether the island of this world has been placed. Stored with the overworld. */
    static final class State extends SavedData {
        private static final String NAME = "data_center_addon_skyblock";
        boolean placed;

        static State get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load), NAME);
        }

        private static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.placed = tag.getBoolean("placed");
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("placed", placed);
            return tag;
        }
    }
}
