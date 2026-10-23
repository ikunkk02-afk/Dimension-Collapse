package com.shouyun.dimensioncollapse.network;

import java.util.List;

import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.collapse.CollapseSource;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Full list of collapse sources in the receiving player's dimension. */
public record CollapseSyncPayload(ResourceKey<Level> dimension, List<CollapseSource> sources) implements CustomPacketPayload {
    public static final Type<CollapseSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DimensionCollapse.MODID, "collapse_sync"));

    public static final StreamCodec<FriendlyByteBuf, CollapseSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), CollapseSyncPayload::dimension,
            CollapseSource.STREAM_CODEC.apply(ByteBufCodecs.list()), CollapseSyncPayload::sources,
            CollapseSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
