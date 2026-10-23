package com.shouyun.dimensioncollapse.registry;

import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.effect.DimensionalAdaptationEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, DimensionCollapse.MODID);

    public static final DeferredHolder<MobEffect, DimensionalAdaptationEffect> DIMENSIONAL_ADAPTATION =
            EFFECTS.register("dimensional_adaptation", DimensionalAdaptationEffect::new);

    private ModEffects() {}
}
