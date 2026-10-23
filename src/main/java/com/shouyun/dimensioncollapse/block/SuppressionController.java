package com.shouyun.dimensioncollapse.block;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.shouyun.dimensioncollapse.collapse.CollapseData;
import com.shouyun.dimensioncollapse.collapse.CollapseManager;
import com.shouyun.dimensioncollapse.collapse.CollapseSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Reconciles all loaded devices before expansion; overlapping devices never prematurely resume a source. */
public final class SuppressionController {
    public static final double RANGE = 64;
    private SuppressionController() {}
    public static void tick(ServerLevel level, Set<Long> loaded) {
        CollapseData data = CollapseData.get(level);
        List<CollapseSource> sources = List.copyOf(data.sources());
        Set<Integer> held = new HashSet<>();
        long now = level.getGameTime();
        for (long packed : loaded) {
            var chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (chunk == null) continue;
            for (var blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                if (!(blockEntity instanceof SuppressionBlockEntity device) || device.isRemoved()) continue;
                boolean active = false;
                if (device.canSuppress()) {
                    for (CollapseSource source : sources) {
                        if (source.manualFreeze() || (source.frozen() && !source.suppressed())) continue;
                        double distance = Vec3.atCenterOf(device.getBlockPos()).distanceTo(Vec3.atCenterOf(source.center()));
                        if (Math.abs(distance - source.radius(now)) <= RANGE || distance <= RANGE) {
                            held.add(source.id()); active = true;
                        }
                    }
                }
                device.updateActive(active);
            }
        }
        boolean changed = false;
        for (CollapseSource source : sources) {
            CollapseSource next = source.withSuppressed(now, held.contains(source.id()));
            if (next != source) { data.replace(next); changed = true; }
        }
        if (changed) CollapseManager.broadcast(level);
    }
}
