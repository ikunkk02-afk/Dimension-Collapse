package com.shouyun.dimensioncollapse.collapse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.shouyun.dimensioncollapse.Config;
import com.shouyun.dimensioncollapse.network.CollapseSyncPayload;
import com.shouyun.dimensioncollapse.network.FlattenedEntityPayload;
import com.shouyun.dimensioncollapse.network.TwoDimensionalPayload;
import com.shouyun.dimensioncollapse.registry.ModAttachments;
import com.shouyun.dimensioncollapse.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Drives every collapse on the server. Work is done per loaded chunk: each chunk remembers how far
 * each source has already eaten into it, so chunks loaded long after the front passed (or never
 * loaded at all until now) catch up on their own and the spread is effectively unbounded.
 */
public final class CollapseManager {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int ENTITY_SCAN_INTERVAL = 5;
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new ConcurrentHashMap<>();

    private CollapseManager() {}

    public static void register(IEventBus bus) {
        bus.addListener(CollapseManager::onChunkLoad);
        bus.addListener(CollapseManager::onChunkUnload);
        bus.addListener(CollapseManager::onLevelTick);
        bus.addListener(CollapseManager::onServerStopped);
        bus.addListener(CollapseManager::onPlayerLoggedIn);
        bus.addListener(CollapseManager::onPlayerChangedDimension);
        bus.addListener(CollapseManager::onPlayerRespawn);
        bus.addListener(CollapseManager::onStartTracking);
        bus.addListener(CollapseManager::onEntityTick);
    }

    public static boolean isFlattened(Entity entity) {
        return entity.hasData(ModAttachments.FLATTENED) && entity.getData(ModAttachments.FLATTENED);
    }

    /** Flattened entities are pictures: they do not tick at all, on either side. */
    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (isFlattened(event.getEntity())) event.setCanceled(true);
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        if (isFlattened(target)) {
            PacketDistributor.sendToPlayer(player, new FlattenedEntityPayload(target.getId(), target.getX(), target.getY(), target.getZ()));
        }
        if (TwoDimensional.is(target)) {
            PacketDistributor.sendToPlayer(player, new TwoDimensionalPayload(target.getId(), true));
        }
    }

    public static void broadcast(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, payloadFor(level));
    }

    public static CollapseSource start(ServerLevel level, BlockPos pos, float radius, float speed, float acceleration) {
        CollapseSource source = CollapseData.get(level).add(pos, level.getGameTime(), radius, speed, acceleration);
        broadcast(level);
        level.playSound(null, pos, com.shouyun.dimensioncollapse.registry.ModSounds.COLLAPSE_START.get(),
                net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 0.7f);
        return source;
    }

    private static CollapseSyncPayload payloadFor(ServerLevel level) {
        return new CollapseSyncPayload(level.dimension(), List.copyOf(CollapseData.get(level).sources()));
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payloadFor(player.serverLevel()));
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            LOADED_CHUNKS.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet()).add(chunk.getPos().toLong());
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Set<Long> loaded = LOADED_CHUNKS.get(level.dimension());
            if (loaded != null) loaded.remove(event.getChunk().getPos().toLong());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        LOADED_CHUNKS.clear();
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<Long> loaded = LOADED_CHUNKS.get(level.dimension());
        com.shouyun.dimensioncollapse.block.SuppressionController.tick(level, loaded == null ? Set.of() : loaded);
        List<CollapseSource> sources = CollapseData.get(level).sources();
        if (sources.isEmpty() || loaded == null) return;

        long now = level.getGameTime();
        if (now % ENTITY_SCAN_INTERVAL == 0) {
            flattenEntities(level, sources, now);
        }

        int budget = Config.BLOCKS_PER_TICK.getAsInt();
        for (CollapseSource source : sources) {
            budget = advance(level, source, now, loaded, budget);
            if (budget <= 0) break;
        }
    }

    private static void flattenEntities(ServerLevel level, List<CollapseSource> sources, long now) {
        int count = sources.size();
        Vec3[] centers = new Vec3[count];
        double[] radii2 = new double[count];
        for (int i = 0; i < count; i++) {
            centers[i] = Vec3.atCenterOf(sources.get(i).center());
            double r = sources.get(i).radius(now);
            radii2[i] = r * r;
        }

        // Collect first: flattening moves entities and killing removes them, neither may happen mid-iteration.
        List<LivingEntity> victims = new ArrayList<>();
        List<CollapseSource> owners = new ArrayList<>();
        List<Player> doomed = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || isFlattened(entity)) continue;
            if (entity instanceof Player player
                    && (player.isCreative() || player.isSpectator() || TwoDimensional.isAdapted(player))) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                if (entity.position().distanceToSqr(centers[i]) <= radii2[i]) {
                    if (entity instanceof Player player) {
                        doomed.add(player);
                    } else {
                        victims.add(living);
                        owners.add(sources.get(i));
                    }
                    break;
                }
            }
        }
        for (int i = 0; i < victims.size(); i++) {
            flattenEntity(victims.get(i), CollapseQueries.planeTop(owners.get(i)));
        }
        for (Player player : doomed) {
            player.hurt(TwoDimensional.flattenedDamage(level), Float.MAX_VALUE);
        }
    }

    private static void flattenEntity(LivingEntity entity, double planeTop) {
        entity.stopRiding();
        entity.ejectPassengers();
        if (entity instanceof Mob mob) mob.setNoAi(true);
        entity.setNoGravity(true);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.teleportTo(entity.getX(), planeTop, entity.getZ());
        entity.setOldPosAndRot();
        entity.setData(ModAttachments.FLATTENED, true);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new FlattenedEntityPayload(entity.getId(), entity.getX(), planeTop, entity.getZ()));
        entity.level().playSound(null, entity.blockPosition(), com.shouyun.dimensioncollapse.registry.ModSounds.ENTITY_FLATTEN.get(),
                net.minecraft.sounds.SoundSource.HOSTILE, 0.65f, 0.85f + entity.getRandom().nextFloat() * 0.3f);
    }

    private record Job(LevelChunk chunk, double done) {}

    private static int advance(ServerLevel level, CollapseSource source, long now, Set<Long> loaded, int budget) {
        double radius = source.radius(now);
        BlockPos center = source.center();
        String key = Integer.toString(source.id());

        List<Job> jobs = new ArrayList<>();
        for (long packed : loaded) {
            int chunkX = ChunkPos.getX(packed);
            int chunkZ = ChunkPos.getZ(packed);
            double nearest = horizontalDistanceToChunk(center, chunkX, chunkZ);
            if (nearest > radius) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) continue;
            double done = chunk.getData(ModAttachments.COLLAPSE_PROGRESS).getOrDefault(key, -1.0);
            if (done >= radius) continue;
            // Nothing in this chunk is closer than `nearest`, so skip straight to it.
            jobs.add(new Job(chunk, Math.max(done, nearest - 1.0)));
        }
        jobs.sort(Comparator.comparingDouble(Job::done));

        double maxStep = Config.MAX_STEP_PER_CHUNK.getAsDouble();
        for (Job job : jobs) {
            if (budget <= 0) break;
            double target = Math.min(radius, job.done() + maxStep);
            budget -= flattenShell(level, job.chunk(), center, job.done(), target);
            Map<String, Double> progress = new HashMap<>(job.chunk().getData(ModAttachments.COLLAPSE_PROGRESS));
            progress.put(key, target);
            job.chunk().setData(ModAttachments.COLLAPSE_PROGRESS, progress);
        }
        return budget;
    }

    private static double horizontalDistanceToChunk(BlockPos center, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int dx = Math.clamp(center.getX(), minX, minX + 15) - center.getX();
        int dz = Math.clamp(center.getZ(), minZ, minZ + 15) - center.getZ();
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    /**
     * Flattens every block of the chunk whose distance d to the centre satisfies inner < d <= outer.
     * Works column by column so the cost is proportional to the shell volume, not the chunk volume.
     *
     * @return number of blocks changed
     */
    private static int flattenShell(ServerLevel level, LevelChunk chunk, BlockPos center, double inner, double outer) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        boolean planeInWorld = center.getY() >= minY && center.getY() <= maxY;
        double inner2 = inner < 0.0 ? -1.0 : inner * inner;
        double outer2 = outer * outer;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos planePos = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = chunk.getPos();
        int changed = 0;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkPos.getMinBlockX() + lx;
                int z = chunkPos.getMinBlockZ() + lz;
                long dx = x - center.getX();
                long dz = z - center.getZ();
                long h2 = dx * dx + dz * dz;
                if (h2 > outer2) continue;
                planePos.set(x, center.getY(), z);

                // The plane cell is always the first of its column to be reached.
                if (h2 > inner2 && planeInWorld) {
                    changed += flatten(level, chunk, planePos, 0, planePos, planeInWorld);
                }
                int maxDy = (int) Math.floor(Math.sqrt(outer2 - h2));
                int minDy = inner2 > h2 ? Math.max(1, (int) Math.floor(Math.sqrt(inner2 - h2))) : 1;
                for (int ady = minDy; ady <= maxDy; ady++) {
                    double d2 = h2 + (double) ady * ady;
                    if (d2 <= inner2 || d2 > outer2) continue;
                    int below = center.getY() - ady;
                    int above = center.getY() + ady;
                    if (below >= minY && below <= maxY) {
                        changed += flatten(level, chunk, pos.set(x, below, z), -ady, planePos, planeInWorld);
                    }
                    if (above >= minY && above <= maxY) {
                        changed += flatten(level, chunk, pos.set(x, above, z), ady, planePos, planeInWorld);
                    }
                }
            }
        }
        return changed;
    }

    private static int flatten(ServerLevel level, LevelChunk chunk, BlockPos pos, int dy, BlockPos planePos, boolean planeInWorld) {
        BlockState state = chunk.getBlockState(pos);
        if (state.isAir() || state.is(ModBlocks.FLATTENED)) return 0;

        MapColor color = state.getMapColor(level, pos);
        BlockState painted = color == MapColor.NONE ? null : ModBlocks.FLATTENED.get().paint(color, shadeFor(dy));
        // Removing the block entity first keeps containers from spilling their contents.
        if (state.hasBlockEntity()) level.removeBlockEntity(pos);

        if (dy == 0) {
            level.setBlock(pos, painted != null ? painted : Blocks.AIR.defaultBlockState(), FLAGS);
            return 1;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        if (painted != null && planeInWorld && chunk.getBlockState(planePos).isAir()) {
            level.setBlock(planePos, painted, FLAGS);
            return 2;
        }
        return 1;
    }

    /** Matter from above the plane is drawn lighter, matter from below darker, like relief shading on a map. */
    private static MapColor.Brightness shadeFor(int dy) {
        if (dy > 0) return MapColor.Brightness.HIGH;
        if (dy == 0) return MapColor.Brightness.NORMAL;
        return dy < -12 ? MapColor.Brightness.LOWEST : MapColor.Brightness.LOW;
    }
}
