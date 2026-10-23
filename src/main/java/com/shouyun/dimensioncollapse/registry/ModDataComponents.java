package com.shouyun.dimensioncollapse.registry;

import java.util.function.Supplier;

import com.shouyun.dimensioncollapse.DimensionCollapse;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DimensionCollapse.MODID);

    /** The receiver a transfer device sends to. */
    public static final Supplier<DataComponentType<GlobalPos>> RECEIVER_LINK = COMPONENTS.registerComponentType("receiver_link",
            builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private ModDataComponents() {}
}
