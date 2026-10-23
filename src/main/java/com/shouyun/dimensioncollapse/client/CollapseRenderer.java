package com.shouyun.dimensioncollapse.client;

import java.io.IOException;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.collapse.TwoDimensional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Screen-space collapse effect: copies the finished world frame (color + depth), then redraws it
 * through a fullscreen shader that ray-traces the collapse sphere against the reconstructed scene.
 */
public final class CollapseRenderer {
    private static ShaderInstance shader;
    private static TextureTarget sceneCopy;
    private static float flatland;
    private static long lastFrameNanos;

    private CollapseRenderer() {}

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DimensionCollapse.MODID, "collapse_post"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException e) {
            DimensionCollapse.LOGGER.error("Failed to load collapse shader", e);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || mc.level == null) {
            return;
        }
        double gameTime = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPos = event.getCamera().getPosition();
        ActiveCollapse collapse = ActiveCollapse.find(mc.level, cameraPos, gameTime);
        if (collapse == null) {
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        copyScene(main);

        Vec3 center = collapse.center().subtract(cameraPos);
        Matrix4f viewProj = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Matrix4f invViewProj = new Matrix4f(viewProj).invert();

        shader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
        shader.setSampler("DepthSampler", sceneCopy.getDepthTextureId());
        shader.safeGetUniform("ViewProj").set(viewProj);
        shader.safeGetUniform("InvViewProj").set(invViewProj);
        shader.safeGetUniform("Center").set((float) center.x, (float) center.y, (float) center.z);
        shader.safeGetUniform("PlaneY").set((float) (collapse.planeY() - cameraPos.y));
        shader.safeGetUniform("Radius").set(collapse.radius());
        shader.safeGetUniform("Speed").set(collapse.speed());
        shader.safeGetUniform("Time").set((float) ((gameTime / 20.0) % 3600.0));
        shader.safeGetUniform("Flatland").set(updateFlatland(mc));

        main.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0f, -1.0f, 0.0f);
        buffer.addVertex(1.0f, -1.0f, 0.0f);
        buffer.addVertex(1.0f, 1.0f, 0.0f);
        buffer.addVertex(-1.0f, 1.0f, 0.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** Eases the one-dimensional "Flatland" view in and out over about a second. */
    private static float updateFlatland(Minecraft mc) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0f : Math.min((now - lastFrameNanos) / 1.0e9f, 0.1f);
        lastFrameNanos = now;
        float target = mc.player != null && TwoDimensional.is(mc.player) ? 1.0f : 0.0f;
        flatland += (target - flatland) * Math.min(1.0f, dt * 3.0f);
        return flatland;
    }

    private static void copyScene(RenderTarget main) {
        if (sceneCopy == null) {
            sceneCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            sceneCopy.setFilterMode(GL11.GL_LINEAR);
        } else if (sceneCopy.width != main.width || sceneCopy.height != main.height) {
            sceneCopy.resize(main.width, main.height, Minecraft.ON_OSX);
            sceneCopy.setFilterMode(GL11.GL_LINEAR);
        }
        // Depth blits require identical depth formats, so mirror the main target's stencil attachment.
        if (main.isStencilEnabled() && !sceneCopy.isStencilEnabled()) {
            sceneCopy.enableStencil();
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, sceneCopy.width, sceneCopy.height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
}
