package com.shouyun.dimensioncollapse.collapse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.shouyun.dimensioncollapse.DimensionCollapse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** All collapse sources of one dimension. */
public class CollapseData extends SavedData {
    private static final String NAME = DimensionCollapse.MODID + "_sources";

    private final List<CollapseSource> sources = new ArrayList<>();
    private final Map<Integer, TerrainSweep> terrainSweeps = new HashMap<>();
    private int nextId = 1;

    public static CollapseData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CollapseData::new, CollapseData::load), NAME);
    }

    private static CollapseData load(CompoundTag tag, HolderLookup.Provider registries) {
        CollapseData data = new CollapseData();
        data.nextId = Math.max(1, tag.getInt("next_id"));
        CollapseSource.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("sources"))
                .resultOrPartial(DimensionCollapse.LOGGER::error)
                .ifPresent(data.sources::addAll);
        CompoundTag sweeps = tag.getCompound("terrain_sweeps");
        for (CollapseSource source : data.sources) {
            CompoundTag sweep = sweeps.getCompound(Integer.toString(source.id()));
            long completed = Math.max(0, sweep.getLong("completed"));
            data.terrainSweeps.put(source.id(), new TerrainSweep(Math.max(completed, sweep.getLong("next")), completed));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next_id", nextId);
        CollapseSource.CODEC.listOf().encodeStart(NbtOps.INSTANCE, sources)
                .resultOrPartial(DimensionCollapse.LOGGER::error)
                .ifPresent(encoded -> tag.put("sources", encoded));
        CompoundTag sweeps = new CompoundTag();
        terrainSweeps.forEach((id, sweep) -> {
            CompoundTag value = new CompoundTag();
            value.putLong("next", sweep.next());
            value.putLong("completed", sweep.completed());
            sweeps.put(Integer.toString(id), value);
        });
        tag.put("terrain_sweeps", sweeps);
        return tag;
    }

    /** A resumable spiral sweep; the completed prefix never needs loading again for this source. */
    public record TerrainSweep(long next, long completed) {
        public TerrainSweep advance(boolean fullyFlattened) {
            return new TerrainSweep(next + 1, fullyFlattened && next == completed ? completed + 1 : completed);
        }

        public TerrainSweep restart() { return new TerrainSweep(completed, completed); }
    }

    public TerrainSweep terrainSweep(int sourceId) {
        return terrainSweeps.getOrDefault(sourceId, new TerrainSweep(0, 0));
    }

    public void setTerrainSweep(int sourceId, TerrainSweep sweep) {
        terrainSweeps.put(sourceId, sweep);
        setDirty();
    }

    public List<CollapseSource> sources() {
        return Collections.unmodifiableList(sources);
    }

    public Optional<CollapseSource> find(int id) {
        return sources.stream().filter(source -> source.id() == id).findFirst();
    }

    public CollapseSource add(BlockPos center, long gameTime, float initialRadius, float initialSpeed, float acceleration) {
        CollapseSource source = new CollapseSource(nextId++, center.immutable(), gameTime, -1L,
                initialRadius, initialSpeed, acceleration);
        sources.add(source);
        setDirty();
        return source;
    }

    public void replace(CollapseSource source) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).id() == source.id()) {
                sources.set(i, source);
                setDirty();
                return;
            }
        }
    }

    public boolean remove(int id) {
        boolean removed = sources.removeIf(source -> source.id() == id);
        if (removed) {
            terrainSweeps.remove(id);
            setDirty();
        }
        return removed;
    }

    public int clear() {
        int count = sources.size();
        sources.clear();
        terrainSweeps.clear();
        setDirty();
        return count;
    }
}
