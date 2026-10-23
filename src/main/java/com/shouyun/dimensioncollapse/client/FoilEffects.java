package com.shouyun.dimensioncollapse.client;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shouyun.dimensioncollapse.entity.DualVectorFoilEntity;
import com.shouyun.dimensioncollapse.item.FoilLauncherItem;
import com.shouyun.dimensioncollapse.network.CollapseClientState;
import com.shouyun.dimensioncollapse.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** A bounded, client-only cinematic timeline driven by confirmed launches and unfolds. */
public final class FoilEffects {
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.25f, 0.95f, 1), 0.6f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7f, 0.3f, 1), 0.65f);
    private static final List<Pulse> PULSES = new ArrayList<>();
    private static ClientLevel currentLevel;
    private record Pulse(Vec3 pos, long start, boolean unfold, float strength) {
        double age(double now) { return Math.max(0, now - start); }
        double kick(double now) {
            double duration = unfold ? 30 : 16;
            double decay = Math.max(0, 1 - age(now) / duration);
            return decay * decay * strength;
        }
    }

    private FoilEffects() {}

    public static void clear() { PULSES.clear(); currentLevel = null; }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != currentLevel) { clear(); currentLevel = mc.level; }
        if (mc.level == null || mc.player == null) {
            CollapseClientState.drainFoilEvents();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        PULSES.removeIf(pulse -> pulse.age(now) > 80);
        for (var fx : CollapseClientState.drainFoilEvents()) {
            if (!fx.dimension().equals(mc.level.dimension())) continue;
            Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
            double range = fx.unfold() ? 96 : 40;
            double distance = mc.player.getEyePosition().distanceTo(pos);
            if (distance >= range) continue;
            float strength = (float) Math.pow(1 - distance / range, 1.5);
            if (PULSES.size() >= 24) PULSES.remove(0);
            PULSES.add(new Pulse(pos, now, fx.unfold(), strength));
            if (EffectsConfig.EXTRA_PARTICLES.get()) burst(mc.level, pos, fx.unfold());
        }
        if (!EffectsConfig.EXTRA_PARTICLES.get()) return;
        // A hard cap keeps mass launches from multiplying the per-frame particle budget.
        int count = 0;
        for (var foil : mc.level.getEntitiesOfClass(DualVectorFoilEntity.class, mc.player.getBoundingBox().inflate(64))) {
            if (count++ >= 12) break;
            trail(mc.level, foil);
        }
        float charge = charge(mc);
        if (charge > 0 && now % 2 == 0) {
            Vec3 forward = mc.player.getLookAngle();
            Vec3 center = mc.player.getEyePosition().add(forward.scale(1.3)).add(0, -0.2, 0);
            Vec3 right = side(forward);
            Vec3 up = forward.cross(right).normalize();
            double angle = now * 0.65;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                Vec3 offset = right.scale(Math.cos(a)).add(up.scale(Math.sin(a))).scale(0.5 - charge * 0.3);
                Vec3 pos = center.add(offset);
                mc.level.addParticle(i % 2 == 0 ? CYAN : VIOLET, pos.x, pos.y, pos.z, 0, 0, 0);
            }
        }
    }

    private static Vec3 side(Vec3 direction) {
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        return side.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : side.normalize();
    }

    private static void trail(ClientLevel level, DualVectorFoilEntity foil) {
        Vec3 motion = foil.getDeltaMovement();
        Vec3 direction = motion.lengthSqr() > 0.0001 ? motion.normalize() : new Vec3(0, 0, 1);
        Vec3 right = side(direction);
        Vec3 up = direction.cross(right).normalize();
        for (int step = 0; step < 3; step++) {
            double fraction = step / 3.0;
            Vec3 center = foil.position().subtract(motion.scale(fraction));
            double angle = (foil.tickCount - fraction) * 0.85;
            Vec3 offset = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(0.19);
            for (int strand = -1; strand <= 1; strand += 2) {
                Vec3 pos = center.add(offset.scale(strand));
                level.addParticle(strand == 1 ? CYAN : VIOLET, pos.x, pos.y, pos.z, 0, 0, 0);
            }
        }
        level.addParticle(ParticleTypes.END_ROD, foil.getX(), foil.getY(), foil.getZ(),
                -motion.x * 0.03, -motion.y * 0.03, -motion.z * 0.03);
    }

    private static void burst(ClientLevel level, Vec3 pos, boolean unfold) {
        int count = unfold ? 64 : 20;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            double speed = unfold ? 0.45 : 0.16;
            level.addParticle(!unfold || i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z, Math.cos(angle) * speed,
                    (level.random.nextDouble() - 0.5) * (unfold ? 0.45 : 0.15), Math.sin(angle) * speed);
        }
    }

    private static float charge(Minecraft mc) {
        if (mc.player == null || !mc.player.isUsingItem() || !mc.player.getUseItem().is(ModItems.FOIL_LAUNCHER)) return 0;
        return Mth.clamp(mc.player.getTicksUsingItem() / (float) FoilLauncherItem.CHARGE_TICKS, 0, 1);
    }

    private static double kick(double time) {
        double result = 0;
        for (Pulse pulse : PULSES) result += pulse.kick(time) * (pulse.unfold ? 1.3 : 1);
        return Math.min(result, 1.5);
    }

    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != currentLevel || mc.player == null || mc.isPaused()) return;
        double now = mc.level.getGameTime() + event.getPartialTick();
        double kick = kick(now);
        double charge = charge(mc);
        double scale = EffectsConfig.CAMERA_SHAKE.get();
        double tremor = charge * charge * 0.18;
        event.setPitch(event.getPitch() + (float) ((-kick * 2.2 + Math.sin(now * 2.7) * (kick * 0.9 + tremor)) * scale));
        event.setYaw(event.getYaw() + (float) (Math.sin(now * 2.1 + 1.2) * (kick * 0.8 + tremor) * scale));
        event.setRoll(event.getRoll() + (float) (Math.sin(now * 1.7) * (kick * 1.4 + tremor) * scale));
    }

    public static void fov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != currentLevel || mc.isPaused()) return;
        double scale = EffectsConfig.CAMERA_SHAKE.get() * mc.options.fovEffectScale().get();
        event.setNewFovModifier(event.getNewFovModifier()
                * (float) (1 + (kick(mc.level.getGameTime()) * 0.055 - charge(mc) * 0.035) * scale));
    }

    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != currentLevel || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        double time = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double warning = 0;
        boolean unfolded = false;
        for (Pulse pulse : PULSES) {
            double strength = pulse.strength * Math.max(0, 1 - pulse.age(time) / (pulse.unfold ? 80 : 50));
            warning = Math.max(warning, strength);
            unfolded |= pulse.unfold && strength > 0.1;
        }
        ActiveCollapse collapse = ActiveCollapse.find(mc.level, mc.player.getEyePosition(), time);
        boolean approaching = collapse != null && collapse.speed() > 0
                && Math.abs(collapse.shellDistance(mc.player.getEyePosition())) < 48;
        if (approaching) warning = Math.max(warning,
                0.7 * (1 - Math.abs(collapse.shellDistance(mc.player.getEyePosition())) / 48));
        var gui = event.getGuiGraphics();
        int w = gui.guiWidth(), h = gui.guiHeight();
        if (warning > 0.03) {
            // A slow heartbeat at the edges; the crosshair and terrain remain readable.
            double beat = Math.pow(0.5 + 0.5 * Math.cos(time * Math.PI / 16), 3);
            double alpha = warning * (0.3 + beat * 0.7) * EffectsConfig.WARNING_FLASH.get();
            gui.fill(0, 0, w, h, color((int) (alpha * 15), 0xFF153C));
            int band = Math.max(8, Math.min(w, h) / 7);
            for (int i = 0; i < 20; i++) {
                int a = i * band / 20, b = (i + 1) * band / 20;
                int tint = color((int) (alpha * 100 * Math.pow(1 - i / 20.0, 2)), 0xFF1238);
                gui.fill(a, a, w - a, b, tint);
                gui.fill(a, h - b, w - a, h - a, tint);
                gui.fill(a, b, b, h - b, tint);
                gui.fill(w - b, b, w - a, h - b, tint);
            }
            String key = approaching || unfolded ? "unfold" : "launch";
            Component title = Component.translatable("hud.dimensioncollapse." + key);
            Component subtitle = Component.translatable("hud.dimensioncollapse.evacuate");
            int panel = Math.min(w - 16, Math.max(mc.font.width(title), mc.font.width(subtitle)) + 32);
            int x = (w - panel) / 2, y = Math.max(10, h / 10);
            gui.fill(x, y, x + panel, y + 35, 0xB0140B19);
            gui.fill(x, y, x + 2, y + 35, 0xFFFF3858);
            gui.fill(x + panel - 2, y, x + panel, y + 35, 0xFFFF3858);
            gui.drawCenteredString(mc.font, title, w / 2, y + 6, 0xFFFF627A);
            gui.drawCenteredString(mc.font, subtitle, w / 2, y + 21, 0xFFE7CAD4);
        }
        float charge = charge(mc);
        if (charge > 0) {
            int y = h / 2 + 27;
            gui.fill(w / 2 - 42, y, w / 2 + 42, y + 3, 0xC0202839);
            gui.fill(w / 2 - 42, y, w / 2 - 42 + (int) (84 * charge), y + 3,
                    charge >= 1 ? 0xFFFF526D : 0xFF77EDFF);
            gui.drawCenteredString(mc.font, Component.translatable(charge >= 1
                    ? "hud.dimensioncollapse.ready" : "hud.dimensioncollapse.charging"), w / 2, y + 8,
                    charge >= 1 ? 0xFFFF899A : 0xFFB5F5FF);
        }
    }

    private static int color(int alpha, int rgb) { return Mth.clamp(alpha, 0, 255) << 24 | rgb; }

    public static void renderRings(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || PULSES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != currentLevel) return;
        double time = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = event.getPoseStack().last().pose();
            for (Pulse pulse : PULSES) {
                double age = pulse.age(time);
                if (!pulse.unfold || age >= 22) continue;
                double progress = age / 22;
                float radius = (float) (0.6 + 18 * (1 - Math.pow(1 - progress, 2)));
                int alpha = (int) (210 * (1 - progress) * (1 - progress));
                // Ground impacts lie exactly on a block face; lift ribbons to prevent depth fighting.
                Vec3 center = pulse.pos.add(0, 0.12, 0).subtract(camera);
                ring(buffer, matrix, center, radius, 0.15f + (float) progress * 0.2f, 110, 235, 255, alpha);
                ring(buffer, matrix, center.add(0, 0.08, 0), radius * 0.77f, 0.09f, 185, 120, 255, alpha);
                ring(buffer, matrix, center, radius, 0.65f, 100, 150, 255, alpha / 5);
            }
            var mesh = buffer.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void ring(BufferBuilder buffer, Matrix4f matrix, Vec3 center, float radius,
            float width, int r, int g, int b, int alpha) {
        for (int i = 0; i < 96; i++) {
            double a = i * Math.PI * 2 / 96, next = (i + 1) * Math.PI * 2 / 96;
            vertex(buffer, matrix, center, a, radius, r, g, b, alpha);
            vertex(buffer, matrix, center, next, radius, r, g, b, alpha);
            vertex(buffer, matrix, center, next, radius + width, r, g, b, alpha);
            vertex(buffer, matrix, center, a, radius + width, r, g, b, alpha);
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double angle,
            float radius, int r, int g, int b, int alpha) {
        buffer.addVertex(matrix, (float) (center.x + Math.cos(angle) * radius), (float) center.y,
                (float) (center.z + Math.sin(angle) * radius)).setColor(r, g, b, alpha);
    }
}
