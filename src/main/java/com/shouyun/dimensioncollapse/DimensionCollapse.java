package com.shouyun.dimensioncollapse;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.shouyun.dimensioncollapse.collapse.CollapseCommand;
import com.shouyun.dimensioncollapse.collapse.CollapseManager;
import com.shouyun.dimensioncollapse.collapse.TwoDimensional;
import com.shouyun.dimensioncollapse.network.CollapseClientState;
import com.shouyun.dimensioncollapse.network.CollapseSyncPayload;
import com.shouyun.dimensioncollapse.network.FlattenedEntityPayload;
import com.shouyun.dimensioncollapse.network.TwoDimensionalPayload;
import com.shouyun.dimensioncollapse.registry.ModAttachments;
import com.shouyun.dimensioncollapse.registry.ModBlocks;
import com.shouyun.dimensioncollapse.registry.ModEffects;
import com.shouyun.dimensioncollapse.registry.ModItems;
import com.shouyun.dimensioncollapse.registry.ModEntities;
import com.shouyun.dimensioncollapse.registry.ModSounds;
import com.shouyun.dimensioncollapse.registry.ModDataComponents;
import com.shouyun.dimensioncollapse.registry.ModBlockEntities;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(DimensionCollapse.MODID)
public class DimensionCollapse {
    public static final String MODID = "dimensioncollapse";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DimensionCollapse(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlockEntities.TYPES.register(modEventBus);
        modEventBus.addListener(DimensionCollapse::registerPayloads);

        CollapseManager.register(NeoForge.EVENT_BUS);
        TwoDimensional.register(NeoForge.EVENT_BUS);
        com.shouyun.dimensioncollapse.teleport.PlayerEscape.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(CollapseCommand::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(CollapseSyncPayload.TYPE, CollapseSyncPayload.STREAM_CODEC,
                (payload, context) -> CollapseClientState.update(payload.dimension(), payload.sources()));
        registrar.playToClient(FlattenedEntityPayload.TYPE, FlattenedEntityPayload.STREAM_CODEC, (payload, context) -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity != null) {
                // Stop interpolation before disabling ticks, including the previous render position.
                entity.moveTo(payload.x(), payload.y(), payload.z(), entity.getYRot(), entity.getXRot());
                entity.setOldPosAndRot();
                entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                entity.setData(ModAttachments.FLATTENED, true);
            }
        });
        registrar.playToClient(TwoDimensionalPayload.TYPE, TwoDimensionalPayload.STREAM_CODEC, (payload, context) -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity != null) {
                TwoDimensional.apply(entity, payload.active());
            }
        });
    }
}
