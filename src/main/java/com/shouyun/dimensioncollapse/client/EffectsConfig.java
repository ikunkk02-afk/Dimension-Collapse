package com.shouyun.dimensioncollapse.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation preferences; never change simulation or other players' settings. */
public final class EffectsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE = BUILDER
            .comment("Camera shake strength, including launch recoil and collapse tremors. 0 disables it.")
            .defineInRange("cameraShake", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue WARNING_FLASH = BUILDER
            .comment("Red warning vignette strength. 0 disables the tint but keeps warning text.")
            .defineInRange("warningFlash", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue EXTRA_PARTICLES = BUILDER
            .comment("Enable foil charge, double-helix trails, and unfold burst particles.")
            .define("extraParticles", true);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private EffectsConfig() {}
}
