package com.shouyun.dimensioncollapse.gametest;

import java.nio.file.Files;
import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.client.EffectsConfig;
import com.shouyun.dimensioncollapse.network.CollapseClientState;
import com.shouyun.dimensioncollapse.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Real use/release packets in a disposable world; excluded from the distributed JAR. */
@EventBusSubscriber(modid = DimensionCollapse.MODID, value = Dist.CLIENT)
public final class FoilEffectsSmokeCheck {
    private static boolean started;
    private static int ticks;
    private static int unfoldedAt;
    private static double launchRoll;
    private static double disabledRoll;
    private static boolean charged;
    private static boolean launched;
    private static final StringBuilder REPORT = new StringBuilder();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!Boolean.getBoolean("dimensioncollapse.foilSmoke")) return;
        if (ticks >= 85 && ticks <= 95) launchRoll = Math.max(launchRoll, Math.abs(event.getRoll()));
        if (ticks >= 145 && ticks <= 165) disabledRoll = Math.max(disabledRoll, Math.abs(event.getRoll()));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("dimensioncollapse.foilSmoke")) return;
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
            mc.options.guiScale().set(2);
            mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
            mc.createWorldOpenFlows().createFreshLevel("foil-fx-" + System.currentTimeMillis(),
                    new LevelSettings("Foil FX smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), mc.screen);
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        if (ticks == 20) {
            EffectsConfig.CAMERA_SHAKE.set(1.0);
            EffectsConfig.WARNING_FLASH.set(1.0);
            EffectsConfig.EXTRA_PARTICLES.set(true);
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var level = server.overworld();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.setDayTime(12500);
                player.teleportTo(level, 0.5, -53, 0.5, 0, 15);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.getInventory().setItem(0, new ItemStack(ModItems.FOIL_LAUNCHER.get()));
                player.getInventory().setItem(1, new ItemStack(ModItems.DUAL_VECTOR_FOIL.get(), 8));
            });
        }
        if (ticks >= 35 && ticks < 90) { mc.player.setYRot(0); mc.player.setXRot(15); }
        if (ticks == 40) {
            // An incomplete charge must not start a launch timeline or create a collapse.
            mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
        if (ticks == 48) {
            mc.options.keyUse.setDown(false);
            mc.gameMode.releaseUsingItem(mc.player);
        }
        if (ticks == 52) {
            capture(mc, "01-cancelled-charge.png");
            mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
        if (ticks == 83) {
            charged = mc.player.isUsingItem() && mc.player.getTicksUsingItem() >= 30;
            capture(mc, "02-charge-ready.png");
        }
        if (ticks == 85) {
            mc.options.keyUse.setDown(false);
            mc.gameMode.releaseUsingItem(mc.player);
        }
        if (ticks == 88) {
            launched = mc.player.getCooldowns().isOnCooldown(ModItems.FOIL_LAUNCHER.get());
            capture(mc, "03-launch.png");
        }
        if (ticks == 97) capture(mc, "04-flight.png");
        if (unfoldedAt == 0 && !CollapseClientState.sourcesIn(mc.level.dimension()).isEmpty()) unfoldedAt = ticks;
        if (unfoldedAt > 0 && ticks == unfoldedAt + 4) capture(mc, "05-unfold.png");
        if (unfoldedAt > 0 && ticks == unfoldedAt + 12) capture(mc, "06-shockwave.png");
        if (ticks == 140) {
            EffectsConfig.CAMERA_SHAKE.set(0.0);
            EffectsConfig.WARNING_FLASH.set(0.0);
        }
        if (ticks == 155) capture(mc, "07-reduced-effects.png");
        if (ticks == 180) {
            boolean pass = charged && launched && unfoldedAt > 85 && launchRoll > 0.1 && disabledRoll < 0.0001;
            record(mc, "CHARGE_READY=" + charged);
            record(mc, "SERVER_CONFIRMED_LAUNCH=" + launched);
            record(mc, "UNFOLD_TICK=" + unfoldedAt);
            record(mc, "LAUNCH_CAMERA_ROLL=" + launchRoll);
            record(mc, "DISABLED_CAMERA_ROLL=" + disabledRoll);
            record(mc, "FOIL_FX_SMOKE=" + (pass ? "PASS" : "FAIL"));
            EffectsConfig.CAMERA_SHAKE.set(1.0);
            EffectsConfig.WARNING_FLASH.set(1.0);
            mc.stop();
        }
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
    }

    private static void record(Minecraft mc, String line) {
        DimensionCollapse.LOGGER.info("FOIL_SMOKE {}", line);
        REPORT.append(line).append('\n');
        try { Files.writeString(mc.gameDirectory.toPath().resolve("foil-smoke-report.txt"), REPORT); }
        catch (java.io.IOException ex) { throw new RuntimeException(ex); }
    }
}
