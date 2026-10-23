package com.shouyun.dimensioncollapse.client;

import com.shouyun.dimensioncollapse.collapse.CollapseSource;
import com.shouyun.dimensioncollapse.network.CollapseClientState;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The collapse the client should currently render: the one whose front is closest to the viewer.
 * The shader handles a single sphere, so overlapping collapses only show the nearest front.
 *
 * @param planeY world height of the top of the flattened sheet
 */
public record ActiveCollapse(Vec3 center, double planeY, float radius, float speed) {
    public static ActiveCollapse find(ClientLevel level, Vec3 viewPos, double gameTime) {
        ActiveCollapse best = null;
        double bestGap = Double.MAX_VALUE;
        for (CollapseSource source : CollapseClientState.sourcesIn(level.dimension())) {
            Vec3 center = Vec3.atCenterOf(source.center());
            double radius = source.radius(gameTime);
            double gap = Math.abs(viewPos.distanceTo(center) - radius);
            if (gap < bestGap) {
                bestGap = gap;
                best = new ActiveCollapse(center, source.center().getY() + 1.0 / 16.0,
                        (float) radius, (float) source.speed(gameTime));
            }
        }
        return best;
    }

    /** Positive outside the sphere, negative inside. */
    public double shellDistance(Vec3 pos) {
        return pos.distanceTo(center) - radius;
    }
}
