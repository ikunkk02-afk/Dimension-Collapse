package com.shouyun.dimensioncollapse.network;

import java.util.List;

import com.shouyun.dimensioncollapse.collapse.CollapseSource;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Last synced collapse sources on the client. Kept free of client-only classes so the payload handler is safe to register on both sides. */
public final class CollapseClientState {
    private static final java.util.ArrayDeque<FoilFxPayload> foilEvents = new java.util.ArrayDeque<>();
    private static volatile ResourceKey<Level> dimension;
    private static volatile List<CollapseSource> sources = List.of();

    private CollapseClientState() {}

    public static synchronized void enqueue(FoilFxPayload payload) {
        if (foilEvents.size() >= 64) foilEvents.removeFirst();
        foilEvents.addLast(payload);
    }

    public static synchronized List<FoilFxPayload> drainFoilEvents() {
        var events = List.copyOf(foilEvents);
        foilEvents.clear();
        return events;
    }

    public static void update(ResourceKey<Level> newDimension, List<CollapseSource> newSources) {
        sources = List.copyOf(newSources);
        dimension = newDimension;
    }

    public static List<CollapseSource> sourcesIn(ResourceKey<Level> level) {
        return level.equals(dimension) ? sources : List.of();
    }

    public static synchronized void clear() {
        foilEvents.clear();
        dimension = null;
        sources = List.of();
    }
}
