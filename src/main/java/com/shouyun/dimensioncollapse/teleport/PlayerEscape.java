package com.shouyun.dimensioncollapse.teleport;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import com.shouyun.dimensioncollapse.collapse.CollapseData;
import com.shouyun.dimensioncollapse.collapse.CollapseSource;
import com.shouyun.dimensioncollapse.collapse.TwoDimensional;
import com.shouyun.dimensioncollapse.registry.ModBlocks;
import com.shouyun.dimensioncollapse.registry.ModItems;
import com.shouyun.dimensioncollapse.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-authoritative, bounded evacuation search. Temporary chunk tickets avoid synchronous world generation. */
public final class PlayerEscape {
    public static final double SAFETY_MARGIN = 512;
    public static final int LOOKAHEAD_TICKS = 20 * 30;
    public static final int COOLDOWN_TICKS = 20 * 10;
    private static final int SEARCH_TIMEOUT = 20 * 20;
    private static final TicketType<UUID> SEARCH_TICKET = TicketType.create("dimensioncollapse_escape", UUID::compareTo, 100);
    private static final Map<UUID, Search> PENDING = new HashMap<>();

    private PlayerEscape() {}

    public static void register(IEventBus bus) {
        bus.addListener(PlayerEscape::onTick);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> cancel(event.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent event) -> {
            PENDING.values().forEach(Search::close);
            PENDING.clear();
        });
    }

    public static boolean begin(ServerPlayer player) {
        if (PENDING.containsKey(player.getUUID())) return false;
        var sources = CollapseData.get(player.serverLevel()).sources();
        if (sources.isEmpty() || player.isSpectator() || !player.isAlive()) {
            message(player, "unavailable");
            return false;
        }
        List<ChunkPos> candidates = candidates(player.position(), player.getLookAngle(), sources,
                player.serverLevel().getGameTime(), player.serverLevel().getWorldBorder());
        if (candidates.isEmpty()) {
            failed(player);
            return false;
        }
        Search search = new Search(player, candidates);
        PENDING.put(player.getUUID(), search);
        search.loadNext();
        message(player, "searching");
        return true;
    }

    public static void cancel(UUID playerId) {
        Search search = PENDING.remove(playerId);
        if (search != null) search.close();
    }

    private static void onTick(ServerTickEvent.Post event) {
        for (Search search : List.copyOf(PENDING.values())) {
            if (search.tick()) cancel(search.player.getUUID());
        }
    }

    /** Conservative horizontal clearance also protects players at a different elevation from the source. */
    public static boolean outsideAll(Vec3 pos, List<CollapseSource> sources, long now) {
        for (CollapseSource source : sources) {
            double dx = pos.x - (source.center().getX() + 0.5);
            double dz = pos.z - (source.center().getZ() + 0.5);
            double radius = source.radius(now + LOOKAHEAD_TICKS) + SAFETY_MARGIN;
            if (dx * dx + dz * dz < radius * radius) return false;
        }
        return true;
    }

    public static List<ChunkPos> candidates(Vec3 origin, Vec3 look, List<CollapseSource> sources, long now, WorldBorder border) {
        if (sources.isEmpty()) return List.of();
        CollapseSource nearest = sources.stream().min(java.util.Comparator.comparingDouble(s ->
                origin.distanceTo(Vec3.atCenterOf(s.center())) - s.radius(now))).orElseThrow();
        Vec3 away = origin.subtract(Vec3.atCenterOf(nearest.center())).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-6) away = look.multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-6) away = new Vec3(1, 0, 0);
        double angle = Math.atan2(away.z, away.x);
        var result = new LinkedHashSet<ChunkPos>();
        // Extra rings provide alternatives over oceans, void and steep terrain. At most 24 chunks are examined.
        for (int extra : new int[]{0, 512, 1536}) {
            for (int turn : new int[]{0, 1, -1, 2, -2, 3, -3, 4}) {
                double dx = Math.cos(angle + turn * Math.PI / 4);
                double dz = Math.sin(angle + turn * Math.PI / 4);
                double distance = 256 + extra;
                for (int pass = 0; pass <= sources.size(); pass++) {
                    boolean advanced = false;
                    for (CollapseSource source : sources) {
                        double ox = origin.x - (source.center().getX() + 0.5);
                        double oz = origin.z - (source.center().getZ() + 0.5);
                        // Reserve an extra chunk for rounding and within-chunk landing probes.
                        double radius = source.radius(now + LOOKAHEAD_TICKS) + SAFETY_MARGIN + 32 + extra;
                        double px = ox + dx * distance, pz = oz + dz * distance;
                        if (px * px + pz * pz >= radius * radius) continue;
                        double b = ox * dx + oz * dz;
                        double discriminant = b * b - (ox * ox + oz * oz - radius * radius);
                        distance = Math.max(distance, -b + Math.sqrt(Math.max(0, discriminant)) + 1);
                        advanced = true;
                    }
                    if (!advanced) break;
                }
                double x = origin.x + dx * distance, z = origin.z + dz * distance;
                if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) >= 29_999_968 || Math.abs(z) >= 29_999_968) continue;
                ChunkPos chunk = new ChunkPos(BlockPos.containing(x, origin.y, z));
                if (border.isWithinBounds(chunk.getMinBlockX() + 2, chunk.getMinBlockZ() + 2)
                        && border.isWithinBounds(chunk.getMaxBlockX() - 2, chunk.getMaxBlockZ() - 2)) result.add(chunk);
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    public static Vec3 findLanding(ServerLevel level, ChunkPos chunk, ServerPlayer player) {
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return null;
        int ceiling = level.getMaxBuildHeight() - 2;
        if (level.dimensionType().hasCeiling()) ceiling = Math.min(ceiling, level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 3);
        for (int lx : new int[]{8, 4, 12}) {
            for (int lz : new int[]{8, 4, 12}) {
                int x = chunk.getMinBlockX() + lx, z = chunk.getMinBlockZ() + lz;
                if (!outsideAll(new Vec3(x + 0.5, 0, z + 0.5), CollapseData.get(level).sources(), level.getGameTime())) continue;
                int top = Math.min(ceiling, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
                for (int y = top; y > level.getMinBuildHeight(); y--) {
                    BlockPos feet = new BlockPos(x, y, z);
                    if (safeLanding(level, feet, player)) return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    public static boolean safeLanding(ServerLevel level, BlockPos feet, ServerPlayer player) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 2 > level.getMaxBuildHeight()) return false;
        if (level.dimensionType().hasCeiling() && feet.getY() + 2 >= level.getMinBuildHeight() + level.dimensionType().logicalHeight()) return false;
        if (!level.getWorldBorder().isWithinBounds(feet) || !level.getWorldBorder().isWithinBounds(feet.east().south())) return false;
        BlockPos ground = feet.below();
        BlockState floor = level.getBlockState(ground);
        if (!floor.isFaceSturdy(level, ground, Direction.UP) || !floor.getFluidState().isEmpty()
                || hazardous(floor) || floor.is(BlockTags.LEAVES) || floor.is(ModBlocks.FLATTENED)) return false;
        // Validate a full standing player, even though the current hitbox may be only 0.2 blocks high.
        AABB body = new AABB(feet.getX() + 0.15, feet.getY(), feet.getZ() + 0.15,
                feet.getX() + 0.85, feet.getY() + 2, feet.getZ() + 0.85);
        if (!level.noCollision(player, body) || level.containsAnyLiquid(body)) return false;
        return level.getBlockStates(body).noneMatch(PlayerEscape::hazardous);
    }

    private static boolean hazardous(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }

    /** Revalidate at commit time: other collapses or terrain edits may have appeared during chunk loading. */
    public static boolean teleport(ServerPlayer player, Vec3 destination) {
        ServerLevel level = player.serverLevel();
        if (!outsideAll(destination, CollapseData.get(level).sources(), level.getGameTime())
                || !safeLanding(level, BlockPos.containing(destination), player)) return false;
        Vec3 origin = player.position();
        player.stopRiding();
        player.teleportTo(level, destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
        if (player.position().distanceToSqr(destination) > 1) return false;
        TwoDimensional.leave(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.setOnGround(true);
        player.getCooldowns().addCooldown(ModItems.PLAYER_TRANSMITTER.get(), COOLDOWN_TICKS);
        level.playSound(null, origin.x, origin.y, origin.z, ModSounds.TRANSFER_SEND.get(), SoundSource.PLAYERS, 0.8f, 0.9f);
        level.playSound(null, player.blockPosition(), ModSounds.TRANSFER_RECEIVE.get(), SoundSource.PLAYERS, 0.9f, 0.9f);
        level.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + 0.1, origin.z, 24, 0.4, 0.1, 0.4, 0.04);
        level.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1, destination.z, 32, 0.4, 0.8, 0.4, 0.04);
        player.displayClientMessage(Component.translatable("message.dimensioncollapse.escape.success", player.blockPosition().toShortString()), true);
        return true;
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.dimensioncollapse.escape." + key), true);
    }

    private static void failed(ServerPlayer player) {
        message(player, "failed");
        player.playNotifySound(ModSounds.TRANSFER_FAIL.get(), SoundSource.PLAYERS, 0.5f, 1);
        player.getCooldowns().addCooldown(ModItems.PLAYER_TRANSMITTER.get(), 20);
    }

    private static final class Search {
        final ServerPlayer player;
        final ServerLevel level;
        final List<ChunkPos> candidates;
        int next;
        int age;
        int waiting;
        @Nullable ChunkPos current;

        Search(ServerPlayer player, List<ChunkPos> candidates) {
            this.player = player; this.level = player.serverLevel(); this.candidates = candidates;
        }

        void close() {
            if (current != null) level.getChunkSource().removeRegionTicket(SEARCH_TICKET, current, 0, player.getUUID());
            current = null;
        }

        void loadNext() {
            close(); waiting = 0;
            if (next < candidates.size()) {
                current = candidates.get(next++);
                // Distance zero requests only FULL status; it does not force neighboring entities to tick.
                level.getChunkSource().addRegionTicket(SEARCH_TICKET, current, 0, player.getUUID());
            }
        }

        boolean tick() {
            if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.serverLevel() != level) return true;
            if (++age > SEARCH_TIMEOUT || current == null) { failed(player); return true; }
            if (level.getChunkSource().getChunkNow(current.x, current.z) != null) {
                Vec3 landing = findLanding(level, current, player);
                if (landing != null && teleport(player, landing)) return true;
                loadNext();
            } else if (++waiting >= 80) {
                loadNext();
            } else if (waiting % 20 == 0) {
                level.getChunkSource().addRegionTicket(SEARCH_TICKET, current, 0, player.getUUID());
            }
            return false;
        }
    }
}
