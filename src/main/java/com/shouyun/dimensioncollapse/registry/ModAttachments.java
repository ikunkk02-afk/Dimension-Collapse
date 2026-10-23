package com.shouyun.dimensioncollapse.registry;

import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.shouyun.dimensioncollapse.DimensionCollapse;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DimensionCollapse.MODID);

    /** Per chunk: collapse source id -> radius up to which this chunk has already been flattened. Treat as immutable. */
    public static final Supplier<AttachmentType<Map<String, Double>>> COLLAPSE_PROGRESS = ATTACHMENT_TYPES.register(
            "collapse_progress",
            () -> AttachmentType.<Map<String, Double>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.DOUBLE))
                    .build());

    /** Set on living entities that have been pressed into the collapse plane. Mirrored to clients by FlattenedEntityPayload. */
    public static final Supplier<AttachmentType<Boolean>> FLATTENED = ATTACHMENT_TYPES.register(
            "flattened",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    /** Players currently living in a collapse plane. Recomputed by the server every tick, so not saved. */
    public static final Supplier<AttachmentType<Boolean>> TWO_DIMENSIONAL = ATTACHMENT_TYPES.register(
            "two_dimensional",
            () -> AttachmentType.builder(() -> false).build());

    private ModAttachments() {}
}
