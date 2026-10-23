package com.shouyun.dimensioncollapse.collapse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One expanding collapse. The radius is a pure function of game time so client and server agree
 * without streaming it: r(t) = r0 + v0 * t + a * t^2 / 2, t in seconds of unfrozen time.
 *
 * @param frozenElapsed elapsed ticks at which the source was frozen, or -1 while it is expanding
 */
public record CollapseSource(int id, BlockPos center, long startTick, long frozenElapsed,
                             float initialRadius, float initialSpeed, float acceleration,
                             boolean manualFreeze, boolean suppressed) {
    public CollapseSource(int id, BlockPos center, long startTick, long frozenElapsed,
                          float initialRadius, float initialSpeed, float acceleration) {
        this(id, center, startTick, frozenElapsed, initialRadius, initialSpeed, acceleration, frozenElapsed >= 0, false);
    }
    public static final Codec<CollapseSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(CollapseSource::id),
            BlockPos.CODEC.fieldOf("center").forGetter(CollapseSource::center),
            Codec.LONG.fieldOf("start_tick").forGetter(CollapseSource::startTick),
            Codec.LONG.fieldOf("frozen_elapsed").forGetter(CollapseSource::frozenElapsed),
            Codec.FLOAT.fieldOf("initial_radius").forGetter(CollapseSource::initialRadius),
            Codec.FLOAT.fieldOf("initial_speed").forGetter(CollapseSource::initialSpeed),
            Codec.FLOAT.fieldOf("acceleration").forGetter(CollapseSource::acceleration),
            Codec.BOOL.optionalFieldOf("manual_freeze", false).forGetter(CollapseSource::manualFreeze),
            Codec.BOOL.optionalFieldOf("suppressed", false).forGetter(CollapseSource::suppressed)
    ).apply(instance, CollapseSource::new));

    public static final StreamCodec<FriendlyByteBuf, CollapseSource> STREAM_CODEC =
            StreamCodec.ofMember(CollapseSource::write, CollapseSource::read);

    public boolean frozen() {
        return frozenElapsed >= 0;
    }

    public double elapsedTicks(double gameTime) {
        return frozen() ? frozenElapsed : Math.max(0.0, gameTime - startTick);
    }

    public double radius(double gameTime) {
        double t = elapsedTicks(gameTime) / 20.0;
        return initialRadius + initialSpeed * t + 0.5 * acceleration * t * t;
    }

    /** Expansion speed in blocks per second. */
    public double speed(double gameTime) {
        return frozen() ? 0.0 : initialSpeed + acceleration * elapsedTicks(gameTime) / 20.0;
    }

    public CollapseSource freeze(long gameTime) {
        return transition(gameTime, true, suppressed);
    }

    public CollapseSource resume(long gameTime) {
        return transition(gameTime, false, suppressed);
    }

    public CollapseSource withSuppressed(long gameTime, boolean active) {
        if (suppressed == active) return this;
        // Old saves had only frozenElapsed; preserve those operator freezes too.
        boolean manual = manualFreeze || (frozen() && !suppressed);
        return transition(gameTime, manual, active);
    }

    private CollapseSource transition(long now, boolean manual, boolean suppression) {
        boolean pause = manual || suppression;
        long elapsed = (long) elapsedTicks(now);
        return new CollapseSource(id, center, !pause && frozen() ? now - elapsed : startTick,
                pause ? elapsed : -1L, initialRadius, initialSpeed, acceleration, manual, suppression);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeBlockPos(center);
        buf.writeLong(startTick);
        buf.writeLong(frozenElapsed);
        buf.writeFloat(initialRadius);
        buf.writeFloat(initialSpeed);
        buf.writeFloat(acceleration);
        buf.writeBoolean(manualFreeze);
        buf.writeBoolean(suppressed);
    }

    private static CollapseSource read(FriendlyByteBuf buf) {
        return new CollapseSource(buf.readVarInt(), buf.readBlockPos(), buf.readLong(), buf.readLong(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }
}
