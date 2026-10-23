package com.shouyun.dimensioncollapse.network;

import com.shouyun.dimensioncollapse.DimensionCollapse;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A player entered or left two-dimensional form. */
public record TwoDimensionalPayload(int entityId, boolean active) implements CustomPacketPayload {
    public static final Type<TwoDimensionalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DimensionCollapse.MODID, "two_dimensional"));

    public static final StreamCodec<ByteBuf, TwoDimensionalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TwoDimensionalPayload::entityId,
            ByteBufCodecs.BOOL, TwoDimensionalPayload::active,
            TwoDimensionalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
