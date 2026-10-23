package com.shouyun.dimensioncollapse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shouyun.dimensioncollapse.collapse.CollapseManager;
import com.shouyun.dimensioncollapse.collapse.TwoDimensional;
import com.shouyun.dimensioncollapse.registry.ModBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Debris torn off the front, camera shake near it, and the squashed look of flattened creatures. */
public final class CollapseEffects {
    private static final double PARTICLE_RANGE = 40.0;
    private static final double SHAKE_RANGE = 32.0;
    private static final double INSIDE_FADE = 12.0;
    private static final double INSIDE_LEVEL = 0.2;
    private static final float FLAT_THICKNESS = 0.02f;
    private static final float FLAT_SPREAD = 1.35f;

    private CollapseEffects() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        Vec3 eye = mc.player.getEyePosition();
        ActiveCollapse collapse = ActiveCollapse.find(mc.level, eye, mc.level.getGameTime());
        if (collapse != null) playAmbience(mc, collapse, eye);
        if (collapse == null || Math.abs(collapse.shellDistance(eye)) > PARTICLE_RANGE) return;

        RandomSource random = mc.level.getRandom();
        int attempts = 10 + Mth.floor(Math.min(collapse.speed(), 10.0f) * 2.0f);
        for (int i = 0; i < attempts; i++) {
            // Random point near the player, pushed onto the front.
            Vec3 probe = eye.add((random.nextDouble() - 0.5) * 48.0, (random.nextDouble() - 0.5) * 24.0, (random.nextDouble() - 0.5) * 48.0);
            Vec3 outward = probe.subtract(collapse.center()).normalize();
            Vec3 pos = collapse.center().add(outward.scale(collapse.radius() + random.nextDouble() * 1.2));
            BlockState state = mc.level.getBlockState(BlockPos.containing(pos));
            if (state.isAir() || state.is(ModBlocks.FLATTENED)) continue;

            mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
            // Sucked inwards and down onto the plane.
            Vec3 pull = new Vec3(collapse.center().x - pos.x, (collapse.planeY() - pos.y) * 2.0, collapse.center().z - pos.z)
                    .normalize().scale(0.08 + Math.min(collapse.speed(), 10.0f) * 0.015);
            mc.level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z, pull.x, pull.y, pull.z);
        }
    }

    private static void playAmbience(Minecraft mc, ActiveCollapse collapse, Vec3 eye) {
        double gap = collapse.shellDistance(eye);
        if (gap > 96) return;
        long now = mc.level.getGameTime();
        float proximity = (float)(1.0 - Mth.clamp(Math.max(0, gap) / 96.0, 0.0, 1.0));
        if (now % 80 == 0) {
            mc.level.playLocalSound(eye.x, eye.y, eye.z,
                    com.shouyun.dimensioncollapse.registry.ModSounds.COLLAPSE_AMBIENT.get(),
                    net.minecraft.sounds.SoundSource.AMBIENT, proximity * 0.4f, 0.65f + Math.min(collapse.speed(), 6) * 0.04f, false);
        }
        if (Math.abs(gap) < 32 && collapse.speed() > 0 && now % 20 == 0) {
            Vec3 front = collapse.center().add(eye.subtract(collapse.center()).normalize().scale(collapse.radius()));
            mc.level.playLocalSound(front.x, front.y, front.z,
                    com.shouyun.dimensioncollapse.registry.ModSounds.COLLAPSE_CRACKLE.get(),
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.4f, 0.8f + mc.level.random.nextFloat() * 0.4f, false);
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double time = mc.level.getGameTime() + event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        ActiveCollapse collapse = ActiveCollapse.find(mc.level, cameraPos, time);
        if (collapse == null) return;

        double proximity = frontProximity(collapse.shellDistance(cameraPos));
        if (proximity <= 0.0) return;
        double intensity = proximity * proximity * (0.35 + Math.min(collapse.speed() / 6.0, 1.0));
        double t = time / 20.0;
        float yaw = (float) ((Math.sin(t * 13.1) + Math.sin(t * 29.3 + 1.7) * 0.5) * intensity * 0.6);
        float pitch = (float) ((Math.sin(t * 17.7 + 0.4) + Math.sin(t * 31.9) * 0.5) * intensity * 0.5);
        float roll = (float) (Math.sin(t * 7.3 + 2.1) * intensity * 1.2);
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }

    /** 1 right at the front, fading out toward SHAKE_RANGE outside and settling to a low level deep inside. */
    private static double frontProximity(double shell) {
        if (shell >= 0.0) return 1.0 - Mth.clamp(shell / SHAKE_RANGE, 0.0, 1.0);
        return Mth.lerp(Mth.clamp(-shell / INSIDE_FADE, 0.0, 1.0), 1.0, INSIDE_LEVEL);
    }

    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (isSquashed(event.getEntity())) {
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.scale(FLAT_SPREAD, FLAT_THICKNESS, FLAT_SPREAD);
        }
    }

    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (isSquashed(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    private static boolean isSquashed(Entity entity) {
        return CollapseManager.isFlattened(entity) || TwoDimensional.is(entity);
    }
}
