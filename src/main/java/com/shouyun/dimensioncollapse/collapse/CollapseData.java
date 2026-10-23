package com.shouyun.dimensioncollapse.collapse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next_id", nextId);
        CollapseSource.CODEC.listOf().encodeStart(NbtOps.INSTANCE, sources)
                .resultOrPartial(DimensionCollapse.LOGGER::error)
                .ifPresent(encoded -> tag.put("sources", encoded));
        return tag;
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
            setDirty();
        }
        return removed;
    }

    public int clear() {
        int count = sources.size();
        sources.clear();
        setDirty();
        return count;
    }
}
