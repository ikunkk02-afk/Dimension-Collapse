package com.shouyun.dimensioncollapse.collapse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import com.shouyun.dimensioncollapse.registry.ModAttachments;

/** Loads a bounded stream of terrain behind the front, independent of player view distance. */
public final class CollapseChunkLoader {
    private static final TicketType<Integer> TICKET = TicketType.create("dimensioncollapse_terrain", Integer::compareTo, 100);
    private static final Map<ResourceKey<Level>, Loader> LOADERS = new HashMap<>();
    private static final int PROBES_PER_TICK = 64;

    private CollapseChunkLoader() {}

    public static void tick(ServerLevel level, List<CollapseSource> sources, long now) {
        if (sources.isEmpty()) {
            Loader loader = LOADERS.remove(level.dimension());
            if (loader != null) loader.release(level);
            return;
        }
        LOADERS.computeIfAbsent(level.dimension(), key -> new Loader()).tick(level, sources, now);
    }

    public static void clear() { LOADERS.clear(); }

    /** Square spiral, with each chunk occurring once. Long indices support the entire world border. */
    public static ChunkPos spiral(ChunkPos origin, long index) {
        if (index == 0) return origin;
        long ring = (long) Math.ceil((Math.sqrt(index + 1.0) - 1.0) / 2.0);
        long side = 2 * ring;
        long offset = index - (side - 1) * (side - 1);
        long x, z;
        if (offset < side) { x = ring; z = -ring + 1 + offset; }
        else if (offset < 2 * side) { x = ring - 1 - (offset - side); z = ring; }
        else if (offset < 3 * side) { x = -ring; z = ring - 1 - (offset - 2 * side); }
        else { x = -ring + 1 + (offset - 3 * side); z = -ring; }
        return new ChunkPos(Math.toIntExact(origin.x + x), Math.toIntExact(origin.z + z));
    }

    private static final class Loader {
        private int lastSourceId;
        private int sourceId;
        private ChunkPos current;
        private double target;
        private long requestedAt;
        private final Map<Integer, Long> retryAfter = new HashMap<>();

        void release(ServerLevel level) {
            if (current != null) level.getChunkSource().removeRegionTicket(TICKET, current, 0, sourceId);
            current = null;
        }

        void tick(ServerLevel level, List<CollapseSource> sources, long now) {
            CollapseData data = CollapseData.get(level);
            retryAfter.keySet().removeIf(id -> data.find(id).isEmpty());
            if (current != null) {
                var source = data.find(sourceId).orElse(null);
                if (source == null) {
                    release(level);
                } else {
                    var chunk = level.getChunkSource().getChunkNow(current.x, current.z);
                    double done = chunk == null ? -1 : chunk.getData(ModAttachments.COLLAPSE_PROGRESS)
                            .getOrDefault(Integer.toString(sourceId), -1.0);
                    if (done >= target) {
                        boolean complete = done >= CollapseManager.farthestDistance(level, source.center(), current);
                        data.setTerrainSweep(sourceId, data.terrainSweep(sourceId).advance(complete));
                        release(level);
                    } else if (chunk == null && now - requestedAt >= 200) {
                        // Keep the cursor for retry, but let other sources run if world generation is slow.
                        retryAfter.put(sourceId, now + 20);
                        release(level);
                    } else {
                        // FULL only: no permanent force-load and no forced entity ticking around the chunk.
                        level.getChunkSource().addRegionTicket(TICKET, current, 0, sourceId);
                        return;
                    }
                }
            }

            int first = 0;
            for (int i = 0; i < sources.size(); i++) {
                if (sources.get(i).id() == lastSourceId) { first = (i + 1) % sources.size(); break; }
            }
            int probes = 0;
            for (int i = 0; i < sources.size() && probes < PROBES_PER_TICK; i++) {
                CollapseSource source = sources.get((first + i) % sources.size());
                lastSourceId = source.id();
                if (now < retryAfter.getOrDefault(source.id(), 0L)) continue;
                double radius = source.radius(now);
                ChunkPos origin = new ChunkPos(source.center());
                // A conservative enclosing square, limited only by Minecraft's coordinate bounds.
                long rings = Math.min(3_750_002L, (long) Math.ceil(radius / 16.0) + 1);
                long limit = (2 * rings + 1) * (2 * rings + 1);
                while (probes++ < PROBES_PER_TICK) {
                    var sweep = data.terrainSweep(source.id());
                    if (sweep.next() >= limit) {
                        data.setTerrainSweep(source.id(), sweep.restart());
                        retryAfter.put(source.id(), now + 20);
                        break;
                    }
                    ChunkPos candidate = spiral(origin, sweep.next());
                    if (candidate.getMinBlockX() < -30_000_000 || candidate.getMaxBlockX() >= 30_000_000
                            || candidate.getMinBlockZ() < -30_000_000 || candidate.getMaxBlockZ() >= 30_000_000) {
                        data.setTerrainSweep(source.id(), sweep.advance(true));
                        continue;
                    }
                    if (!level.getWorldBorder().isWithinBounds(candidate)
                            || CollapseManager.horizontalDistanceToChunk(source.center(), candidate.x, candidate.z) > radius) {
                        data.setTerrainSweep(source.id(), sweep.advance(false));
                        continue;
                    }
                    double farthest = CollapseManager.farthestDistance(level, source.center(), candidate);
                    double requestedRadius = Math.min(radius, farthest);
                    var chunk = level.getChunkSource().getChunkNow(candidate.x, candidate.z);
                    double done = chunk == null ? -1 : chunk.getData(ModAttachments.COLLAPSE_PROGRESS)
                            .getOrDefault(Integer.toString(source.id()), -1.0);
                    if (done >= requestedRadius) {
                        data.setTerrainSweep(source.id(), sweep.advance(done >= farthest));
                        continue;
                    }
                    current = candidate;
                    sourceId = source.id();
                    target = requestedRadius;
                    requestedAt = now;
                    level.getChunkSource().addRegionTicket(TICKET, current, 0, sourceId);
                    return;
                }
            }
        }
    }
}
