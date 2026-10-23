package com.shouyun.dimensioncollapse.network;

import com.shouyun.dimensioncollapse.DimensionCollapse;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells a client that the entity with this id has been flattened at the given position.
 * The position travels with the flag because a flattened entity stops ticking at once and would
 * never finish interpolating towards a separately sent teleport.
 */
public record FlattenedEntityPayload(int entityId, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<FlattenedEntityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DimensionCollapse.MODID, "flattened_entity"));

    public static final StreamCodec<ByteBuf, FlattenedEntityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FlattenedEntityPayload::entityId,
            ByteBufCodecs.DOUBLE, FlattenedEntityPayload::x,
            ByteBufCodecs.DOUBLE, FlattenedEntityPayload::y,
            ByteBufCodecs.DOUBLE, FlattenedEntityPayload::z,
            FlattenedEntityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
