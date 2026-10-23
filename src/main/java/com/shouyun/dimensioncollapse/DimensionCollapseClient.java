package com.shouyun.dimensioncollapse;

import com.shouyun.dimensioncollapse.block.FlattenedBlock;
import com.shouyun.dimensioncollapse.client.CollapseEffects;
import com.shouyun.dimensioncollapse.client.CollapseRenderer;
import com.shouyun.dimensioncollapse.collapse.TwoDimensional;
import com.shouyun.dimensioncollapse.network.CollapseClientState;
import com.shouyun.dimensioncollapse.registry.ModBlocks;
import com.shouyun.dimensioncollapse.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = DimensionCollapse.MODID, dist = Dist.CLIENT)
public class DimensionCollapseClient {
    // Item tints are ARGB; the alpha byte must be opaque.
    private static final int SUPPRESSANT_COLOR = 0xFF7FD8FF;
    private static final int FLAT_MATTER_COLOR = 0xFFB89CFF;

    public DimensionCollapseClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                com.shouyun.dimensioncollapse.client.EffectsConfig.SPEC);

        modEventBus.addListener(CollapseRenderer::registerShaders);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(com.shouyun.dimensioncollapse.registry.ModEntities.DUAL_VECTOR_FOIL.get(),
                        net.minecraft.client.renderer.entity.ThrownItemRenderer::new));
        modEventBus.addListener(DimensionCollapseClient::registerBlockColors);
        modEventBus.addListener(DimensionCollapseClient::registerItemColors);
        // A two-dimensional being has no hand sticking out into the third dimension.
        NeoForge.EVENT_BUS.addListener((RenderHandEvent event) -> {
            Player player = Minecraft.getInstance().player;
            if (player != null && TwoDimensional.is(player)) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(CollapseRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(CollapseEffects::onClientTick);
        NeoForge.EVENT_BUS.addListener(CollapseEffects::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(com.shouyun.dimensioncollapse.client.FoilEffects::tick);
        NeoForge.EVENT_BUS.addListener(com.shouyun.dimensioncollapse.client.FoilEffects::camera);
        NeoForge.EVENT_BUS.addListener(com.shouyun.dimensioncollapse.client.FoilEffects::fov);
        NeoForge.EVENT_BUS.addListener(com.shouyun.dimensioncollapse.client.FoilEffects::hud);
        NeoForge.EVENT_BUS.addListener(com.shouyun.dimensioncollapse.client.FoilEffects::renderRings);
        // Lowest priority without receiving cancelled events, so Pre and Post always pair up.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, CollapseEffects::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Post.class, CollapseEffects::onRenderLivingPost);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            CollapseClientState.clear();
            com.shouyun.dimensioncollapse.client.FoilEffects.clear();
        });
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> FlattenedBlock.rgb(state), ModBlocks.FLATTENED.get());
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? SUPPRESSANT_COLOR : -1, ModItems.DIMENSION_SUPPRESSANT.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? FLAT_MATTER_COLOR : -1, ModItems.FLAT_MATTER.get());
    }
}
