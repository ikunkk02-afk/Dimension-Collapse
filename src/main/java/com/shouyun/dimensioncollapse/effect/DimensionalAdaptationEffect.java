package com.shouyun.dimensioncollapse.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Lets a player survive inside a collapse as a two-dimensional being, for as long as it lasts. */
public class DimensionalAdaptationEffect extends MobEffect {
    public DimensionalAdaptationEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7FD8FF);
    }
}
