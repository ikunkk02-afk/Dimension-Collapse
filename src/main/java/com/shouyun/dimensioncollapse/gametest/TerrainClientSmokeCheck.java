package com.shouyun.dimensioncollapse.gametest;

import java.nio.file.Files;
import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.collapse.CollapseData;
import com.shouyun.dimensioncollapse.collapse.CollapseManager;
import com.shouyun.dimensioncollapse.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Compare server and client terrain outside simulation distance in a disposable world. */
@EventBusSubscriber(modid = DimensionCollapse.MODID, value = Dist.CLIENT)
public final class TerrainClientSmokeCheck {
    private static final BlockPos PROBE = new BlockPos(128, -60, 0);
    private static boolean started;
    private static boolean finished;
    private static int ticks;
    private static volatile boolean requested;
    private static volatile boolean outsideSimulation;
    private static volatile boolean serverFlat;
    private static volatile String fullStatus;
    private static final StringBuilder REPORT = new StringBuilder();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("dimensioncollapse.terrainSmoke") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false;
            mc.setScreen(new TitleScreen());
        }
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            mc.options.renderDistance().set(12);
            mc.options.simulationDistance().set(5);
            mc.options.guiScale().set(2);
            mc.createWorldOpenFlows().createFreshLevel("terrain-" + System.currentTimeMillis(),
                    new LevelSettings("Terrain sync smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), mc.screen);
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        var server = mc.getSingleplayerServer();
        ticks++;
        if (ticks == 20) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.teleportTo(server.overworld(), 0.5, -48, 0.5, -90, 12);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        if (ticks > 40 && !requested && mc.level.hasChunk(8, 0)) {
            server.execute(() -> {
                if (requested) return;
                var level = server.overworld();
                var chunk = level.getChunkSource().getChunkNow(8, 0);
                if (chunk == null || chunk.getFullStatus() == null) return;
                // FullChunkStatus is not the simulation-distance test: view tickets can keep distant
                // chunks BLOCK_TICKING. The player remains at chunk 0 with simulation distance 5.
                outsideSimulation = server.getPlayerList().getSimulationDistance() < 8;
                fullStatus = chunk.getFullStatus().name();
                requested = true;
                CollapseManager.start(level, new BlockPos(0, -60, 0), 140, 0, 0);
            });
        }
        if (requested) server.execute(() -> {
            var chunk = server.overworld().getChunkSource().getChunkNow(8, 0);
            serverFlat = chunk != null && chunk.getBlockState(PROBE).is(ModBlocks.FLATTENED);
        });
        boolean clientFlat = mc.level.getBlockState(PROBE).is(ModBlocks.FLATTENED);
        if ((serverFlat && clientFlat) || ticks >= 800) {
            finished = true;
            record(mc, "OUTSIDE_SIMULATION=" + outsideSimulation);
            record(mc, "PROBE_FULL_STATUS=" + fullStatus);
            record(mc, "SERVER_FLATTENED=" + serverFlat);
            record(mc, "CLIENT_FLATTENED=" + clientFlat);
            record(mc, "TERRAIN_SYNC_SMOKE=" + (outsideSimulation && serverFlat && clientFlat ? "PASS" : "FAIL"));
            Screenshot.grab(mc.gameDirectory, "terrain-sync.png", mc.getMainRenderTarget(), message -> {});
            server.execute(() -> CollapseData.get(server.overworld()).clear());
            mc.stop();
        }
    }

    private static void record(Minecraft mc, String line) {
        DimensionCollapse.LOGGER.info("TERRAIN_SMOKE {}", line);
        REPORT.append(line).append('\n');
        try { Files.writeString(mc.gameDirectory.toPath().resolve("terrain-smoke-report.txt"), REPORT); }
        catch (java.io.IOException ex) { throw new RuntimeException(ex); }
    }
}
