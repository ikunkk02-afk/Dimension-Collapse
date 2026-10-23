package com.shouyun.dimensioncollapse.network;

import com.shouyun.dimensioncollapse.DimensionCollapse;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-confirmed one-shot presentation event, scoped to nearby players in this dimension. */
public record FoilFxPayload(ResourceKey<Level> dimension, double x, double y, double z, boolean unfold)
        implements CustomPacketPayload {
    public static final Type<FoilFxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DimensionCollapse.MODID, "foil_fx"));
    public static final StreamCodec<FriendlyByteBuf, FoilFxPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), FoilFxPayload::dimension,
            ByteBufCodecs.DOUBLE, FoilFxPayload::x,
            ByteBufCodecs.DOUBLE, FoilFxPayload::y,
            ByteBufCodecs.DOUBLE, FoilFxPayload::z,
            ByteBufCodecs.BOOL, FoilFxPayload::unfold, FoilFxPayload::new);

    public static void send(ServerLevel level, Vec3 pos, boolean unfold) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, unfold ? 96 : 40,
                new FoilFxPayload(level.dimension(), pos.x, pos.y, pos.z, unfold));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
