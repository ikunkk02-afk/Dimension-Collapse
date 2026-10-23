package com.shouyun.dimensioncollapse.gametest;

import java.nio.file.Files;
import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.block.SuppressionBlockEntity;
import com.shouyun.dimensioncollapse.collapse.CollapseData;
import com.shouyun.dimensioncollapse.collapse.CollapseManager;
import com.shouyun.dimensioncollapse.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
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

/** Opt-in client regression probe. Excluded from the release JAR. Never touches the normal run folder. */
@EventBusSubscriber(modid = DimensionCollapse.MODID, value = Dist.CLIENT)
public final class ClientSmokeCheck {
    private static boolean started;
    private static int ticks;
    private static volatile int cowId = -1;
    private static boolean verified;
    private static final StringBuilder REPORT = new StringBuilder();

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("dimensioncollapse.smoke")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false;
            mc.setScreen(new TitleScreen());
        }
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            mc.options.renderDistance().set(6);
            mc.options.simulationDistance().set(5);
            mc.createWorldOpenFlows().createFreshLevel("smoke-" + System.currentTimeMillis(),
                    new LevelSettings("Dimension Collapse smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        if (ticks == 20) mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var level = server.overworld();
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setDayTime(6000);
            player.teleportTo(level, 2.5, -54, 9.5, 180, 32);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            var cow = EntityType.COW.create(level);
            cow.moveTo(2.5, -55, 1.5, 20, 0);
            cow.setNoGravity(true); cow.setNoAi(true);
            level.addFreshEntity(cow); cowId = cow.getId();
            var suppressorPos = new BlockPos(9, -60, 0);
            level.setBlock(suppressorPos, ModBlocks.EXPANSION_SUPPRESSOR.get().defaultBlockState(), 3);
            ((SuppressionBlockEntity)level.getBlockEntity(suppressorPos)).addFuel();
            level.setBlock(new BlockPos(9, -60, 2), ModBlocks.TRANSFER_RECEIVER.get().defaultBlockState(), 3);
            player.getInventory().setItem(0, new ItemStack(ModItems.DUAL_VECTOR_FOIL.get()));
            player.getInventory().setItem(1, new ItemStack(ModItems.FOIL_LAUNCHER.get()));
            player.getInventory().setItem(2, new ItemStack(ModItems.EXPANSION_SUPPRESSOR.get()));
            player.getInventory().setItem(3, new ItemStack(ModItems.RESOURCE_TRANSMITTER.get()));
            player.getInventory().setItem(4, new ItemStack(ModItems.TRANSFER_RECEIVER.get()));
            player.getInventory().setItem(5, new ItemStack(ModItems.FLAT_MATTER.get(), 64));
        });
        if (ticks == 60) mc.getSingleplayerServer().execute(() ->
                CollapseManager.start(mc.getSingleplayerServer().overworld(), new BlockPos(0, -60, 0), 6, 0.15f, 0.04f));
        if (ticks == 115) Screenshot.grab(mc.gameDirectory, "effects-client.png", mc.getMainRenderTarget(), message -> {});
        if (ticks == 120) mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            // Removing the source leaves the real sheet and flattened entity; inspect without refraction obscuring them.
            CollapseData.get(level).clear();
            CollapseManager.broadcast(level);
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.teleportTo(level, 2.5, -55, 6.5, 180, 53);
            player.getAbilities().flying = true; player.onUpdateAbilities();
        });
        if (ticks >= 125 && ticks <= 155) { mc.player.setYRot(180); mc.player.setXRot(53); }
        if (ticks == 150) {
            var cow = mc.level.getEntity(cowId);
            boolean good = cow != null && CollapseManager.isFlattened(cow) && Math.abs(cow.getY() + 59.9375) < 1e-6 && cow.yOld == cow.getY();
            record(mc, "CLIENT_FLATTEN_POSITION=" + good + (cow == null ? " missing entity" : " y=" + cow.getY() + " oldY=" + cow.yOld));
            int sounds = 0;
            for (var entry : ModSounds.SOUNDS.getEntries()) {
                if (mc.getSoundManager().getSoundEvent(entry.getId()) != null) sounds++;
            }
            record(mc, "REGISTERED_CLIENT_SOUNDS=" + sounds);
            verified = good && sounds == 17;
            mc.setScreen(null);
            Screenshot.grab(mc.gameDirectory, "flattened-client.png", mc.getMainRenderTarget(), message -> {});
        }
        if (ticks == 180) mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.teleportTo(server.overworld(), 9.5, -58, 7, 180, 25);
            player.getAbilities().flying = true; player.onUpdateAbilities();
        });
        if (ticks >= 190 && ticks <= 235) { mc.player.setYRot(180); mc.player.setXRot(25); }
        if (ticks == 230) Screenshot.grab(mc.gameDirectory, "devices-client.png", mc.getMainRenderTarget(), message -> {});
        if (ticks == 260) {
            record(mc, "SMOKE_RESULT=" + (verified ? "PASS" : "FAIL"));
            mc.stop();
        }
    }

    private static void record(Minecraft mc, String line) {
        DimensionCollapse.LOGGER.info("SMOKE {}", line);
        REPORT.append(line).append('\n');
        try { Files.writeString(mc.gameDirectory.toPath().resolve("smoke-report.txt"), REPORT); }
        catch (java.io.IOException ex) { throw new RuntimeException(ex); }
    }
}
