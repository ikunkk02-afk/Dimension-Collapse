package com.shouyun.dimensioncollapse.collapse;

import java.util.List;

import com.shouyun.dimensioncollapse.network.CollapseClientState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Side-agnostic lookups: the server reads saved data, the client the last synced copy. */
public final class CollapseQueries {
    private CollapseQueries() {}

    public static List<CollapseSource> sources(Level level) {
        return level instanceof ServerLevel serverLevel
                ? CollapseData.get(serverLevel).sources()
                : CollapseClientState.sourcesIn(level.dimension());
    }

    /** The collapse whose sphere contains {@code pos}, or null. */
    public static CollapseSource containing(Level level, Vec3 pos) {
        long now = level.getGameTime();
        for (CollapseSource source : sources(level)) {
            double r = source.radius(now);
            if (pos.distanceToSqr(Vec3.atCenterOf(source.center())) <= r * r) {
                return source;
            }
        }
        return null;
    }

    /** Height of the top surface of the flattened sheet. */
    public static double planeTop(CollapseSource source) {
        return source.center().getY() + 1.0 / 16.0;
    }
}
