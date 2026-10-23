package com.shouyun.dimensioncollapse.gametest;

import com.shouyun.dimensioncollapse.collapse.CollapseData;
import com.shouyun.dimensioncollapse.collapse.CollapseChunkLoader;
import com.shouyun.dimensioncollapse.registry.ModAttachments;
import com.shouyun.dimensioncollapse.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dimensioncollapse")
@PrefixGameTestTemplate(false)
public final class CollapseTerrainGameTests {
    @GameTest(batch = "collapseTerrainBudget", template = "test_empty", timeoutTicks = 60)
    public static void outerChunkIsNotStarvedByInnerTerrain(GameTestHelper h) {
        var level = h.getLevel();
        var center = new BlockPos(32776, -60, 32776);
        var probe = center.east(128);
        var near = level.getChunkAt(center);
        var far = level.getChunkAt(probe);
        var ticket = net.minecraft.server.level.TicketType.<Integer>create("terrain_budget_test", Integer::compareTo, 80);
        level.getChunkSource().addRegionTicket(ticket, near.getPos(), 0, 1);
        level.getChunkSource().addRegionTicket(ticket, far.getPos(), 0, 1);
        // Dense inner columns exhaust the budget on every pass while the outer chunk is already engulfed.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z), Blocks.STONE.defaultBlockState(), 18);
            }
        }
        int previousBudget = com.shouyun.dimensioncollapse.Config.BLOCKS_PER_TICK.getAsInt();
        com.shouyun.dimensioncollapse.Config.BLOCKS_PER_TICK.set(64);
        var data = CollapseData.get(level);
        var source = data.add(center, level.getGameTime(), 512, 0, 0);
        h.runAfterDelay(10, () -> {
            try {
                h.assertTrue(far.getBlockState(probe).is(ModBlocks.FLATTENED),
                        "Loaded outer terrain must advance while dense inner chunks are still catching up; progress="
                                + far.getData(ModAttachments.COLLAPSE_PROGRESS));
                h.succeed();
            } finally {
                data.remove(source.id());
                com.shouyun.dimensioncollapse.Config.BLOCKS_PER_TICK.set(previousBudget);
                level.getChunkSource().removeRegionTicket(ticket, near.getPos(), 0, 1);
                level.getChunkSource().removeRegionTicket(ticket, far.getPos(), 0, 1);
            }
        });
    }

    @GameTest(batch = "collapseTerrain", template = "test_empty")
    public static void sweepCoversAllDirectionsAndPersists(GameTestHelper h) throws ReflectiveOperationException {
        var origin = new ChunkPos(-100, -200);
        var visited = new java.util.HashSet<ChunkPos>();
        for (long index = 0; index < 81; index++) {
            ChunkPos chunk = CollapseChunkLoader.spiral(origin, index);
            h.assertTrue(visited.add(chunk), "Background traversal must not repeat a chunk within a sweep");
            h.assertTrue(Math.abs(chunk.x - origin.x) <= 4 && Math.abs(chunk.z - origin.z) <= 4,
                    "First four rings must stay within the expected square");
        }
        h.assertTrue(visited.size() == 81, "Every direction must be covered without gaps");
        var data = new CollapseData();
        var source = data.add(BlockPos.ZERO, 0, 1, 1, 0);
        data.setTerrainSweep(source.id(), new CollapseData.TerrainSweep(57, 9));
        CompoundTag saved = data.save(new CompoundTag(), h.getLevel().registryAccess());
        // Exercise the actual SavedData decoder, including migration from existing worlds.
        var load = CollapseData.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var restored = (CollapseData) load.invoke(null, saved, h.getLevel().registryAccess());
        h.assertTrue(restored.terrainSweep(source.id()).equals(data.terrainSweep(source.id())),
                "World reload must preserve background progress");
        saved.remove("terrain_sweeps");
        var migrated = (CollapseData) load.invoke(null, saved, h.getLevel().registryAccess());
        h.assertTrue(migrated.sources().size() == 1 && migrated.terrainSweep(source.id()).next() == 0,
                "Existing worlds must retain their collapses and start background catch-up");
        restored.remove(source.id());
        h.assertTrue(restored.save(new CompoundTag(), h.getLevel().registryAccess()).getCompound("terrain_sweeps").isEmpty(),
                "Removing a collapse must also remove its saved cursor");
        h.succeed();
    }

    @GameTest(batch = "collapseTerrainCatchup", template = "test_empty", timeoutTicks = 180)
    public static void lateLoadedChunkFlattensThroughBuildHeight(GameTestHelper h) {
        var level = h.getLevel();
        var center = new BlockPos(-16376, -60, -16376);
        var probe = center.east(4096);
        var chunk = level.getChunkAt(probe);
        var ticket = net.minecraft.server.level.TicketType.<Integer>create("terrain_test", Integer::compareTo, 200);
        level.getChunkSource().addRegionTicket(ticket, chunk.getPos(), 0, 1);
        var floor = new BlockPos(probe.getX(), level.getMinBuildHeight(), probe.getZ());
        var roof = new BlockPos(chunk.getPos().getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getPos().getMaxBlockZ());
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(roof, Blocks.STONE.defaultBlockState(), 2);
        var data = CollapseData.get(level);
        // The sphere has already travelled thousands of blocks before this chunk is considered.
        var source = data.add(center, level.getGameTime() - 20 * 600, 1, 0.15f, 0.04f);
        h.runAfterDelay(120, () -> {
            try {
                h.assertTrue(chunk.getBlockState(floor).isAir(), "Late-loaded terrain must catch up at the bottom build limit; progress="
                        + chunk.getData(ModAttachments.COLLAPSE_PROGRESS));
                h.assertTrue(chunk.getBlockState(roof).isAir(), "Late-loaded terrain must catch up at the top/furthest corner");
                h.assertTrue(chunk.getBlockState(probe).is(ModBlocks.FLATTENED), "Catch-up must leave a real 2D sheet");
                double completed = chunk.getData(ModAttachments.COLLAPSE_PROGRESS).getOrDefault(Integer.toString(source.id()), -1.0);
                h.assertTrue(completed > 4096 && completed < source.radius(level.getGameTime()),
                        "Completed chunks must stop scanning once all buildable heights are covered");
                h.succeed();
            } finally {
                data.remove(source.id());
                level.getChunkSource().removeRegionTicket(ticket, chunk.getPos(), 0, 1);
            }
        });
    }

    @GameTest(batch = "collapseTerrain", template = "test_empty", timeoutTicks = 60000)
    public static void spreadsWithoutNearbyPlayers(GameTestHelper h) {
        var level = h.getLevel();
        var center = new BlockPos(16392, -60, 16392);
        var first = new ChunkPos(center);
        var outer = new ChunkPos(first.x + 2, first.z);
        h.assertTrue(level.getChunkSource().getChunkNow(first.x, first.z) == null,
                "Regression must start outside loaded chunks");
        h.assertTrue(level.getChunkSource().getChunkNow(outer.x, outer.z) == null,
                "Outer probe must also start unloaded");
        var data = CollapseData.get(level);
        // GameTest advances ticks faster than real time. Keep a fixed target radius and allow
        // cold CI workers enough wall-clock time for asynchronous terrain generation.
        // Growth is covered separately; this regression tests crossing unloaded chunk boundaries.
        var source = data.add(center, level.getGameTime(), 40, 0, 0);
        boolean[] reached = new boolean[2];
        h.onEachTick(() -> {
            // Inspect only already loaded chunks: the test must never load the terrain itself.
            var a = level.getChunkSource().getChunkNow(first.x, first.z);
            var b = level.getChunkSource().getChunkNow(outer.x, outer.z);
            if (a != null && a.getBlockState(center).is(ModBlocks.FLATTENED)) reached[0] = true;
            if (b != null && b.getBlockState(center.east(32)).is(ModBlocks.FLATTENED)) reached[1] = true;
            if (reached[0] && reached[1]) {
                data.remove(source.id());
                h.succeed();
            }
        });
        h.runAfterDelay(59000, () -> {
            data.remove(source.id());
            h.assertTrue(reached[0], "Collapse must load and flatten terrain without a nearby player");
            h.assertTrue(reached[1], "Terrain must keep spreading beyond the first loaded chunk");
        });
    }
}
