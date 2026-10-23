package com.shouyun.dimensioncollapse;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment("Soft limit on how many blocks all collapses together may flatten per level per tick.",
                    "Higher values keep the world in step with the visual front but cost more server time.")
            .defineInRange("blocksPerTick", 8192, 64, 1_000_000);

    public static final ModConfigSpec.DoubleValue MAX_STEP_PER_CHUNK = BUILDER
            .comment("Maximum radius (in blocks) a single chunk advances in one pass.",
                    "Bounds the work done for chunks that were loaded long after the front passed them.")
            .defineInRange("maxStepPerChunk", 8.0, 1.0, 64.0);

    static final ModConfigSpec SPEC = BUILDER.build();
}
