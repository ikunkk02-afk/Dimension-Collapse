package com.shouyun.dimensioncollapse.gametest;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.shouyun.dimensioncollapse.collapse.CollapseData;
import com.shouyun.dimensioncollapse.collapse.CollapseSource;
import com.shouyun.dimensioncollapse.collapse.TwoDimensional;
import com.shouyun.dimensioncollapse.registry.ModEffects;
import com.shouyun.dimensioncollapse.registry.ModItems;
import com.shouyun.dimensioncollapse.teleport.PlayerEscape;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dimensioncollapse")
@PrefixGameTestTemplate(false)
public final class PlayerEscapeGameTests {
    private static FakePlayer player(GameTestHelper h) {
        // NeoForge's fake connection intentionally ignores teleport packets; simulate their position effect here.
        // The opt-in real client smoke separately verifies the actual packet/acknowledgement path.
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "escape-test")) {
            @Override public void teleportTo(net.minecraft.server.level.ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                if (level != serverLevel()) throw new IllegalArgumentException("Evacuation must stay in the current dimension");
                moveTo(x, y, z, yaw, pitch);
            }
        };
    }

    @GameTest(batch = "playerEscape", template = "test_empty")
    public static void candidateClearanceAndWorldBorder(GameTestHelper h) {
        var sources = List.of(new CollapseSource(1, BlockPos.ZERO, 0, -1, 10, 12, 0.4f),
                new CollapseSource(2, new BlockPos(600, 40, 0), 0, -1, 150, 2, 0));
        var border = new WorldBorder();
        var candidates = PlayerEscape.candidates(new Vec3(0.5, 1, 0.5), new Vec3(0, -1, 0), sources, 100, border);
        h.assertTrue(!candidates.isEmpty() && candidates.size() <= 24, "Search must be bounded even looking straight down at the source");
        for (var chunk : candidates) {
            for (int x : new int[]{4, 12}) for (int z : new int[]{4, 12}) {
                h.assertTrue(PlayerEscape.outsideAll(new Vec3(chunk.getMinBlockX() + x + 0.5, 0, chunk.getMinBlockZ() + z + 0.5), sources, 100),
                        "Every candidate must clear all overlapping, expanding sources");
            }
        }
        border.setSize(128);
        h.assertTrue(PlayerEscape.candidates(Vec3.ZERO, new Vec3(1, 0, 0), sources, 100, border).isEmpty(), "No candidate may cross a restrictive world border");
        h.assertTrue(PlayerEscape.candidates(Vec3.ZERO, Vec3.ZERO, List.of(), 0, border).isEmpty(), "No source means no evacuation");
        h.succeed();
    }

    @GameTest(batch = "playerEscape", template = "test_empty")
    public static void rejectsHazardsAndFullHeightCollision(GameTestHelper h) {
        var level = h.getLevel();
        var feet = h.absolutePos(new BlockPos(3, 3, 3));
        var player = player(h);
        TwoDimensional.apply(player, true);
        level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(PlayerEscape.safeLanding(level, feet, player), "Open solid ground should be accepted");
        level.setBlock(feet.above(), Blocks.STONE.defaultBlockState(), 3);
        h.assertFalse(PlayerEscape.safeLanding(level, feet, player), "A flattened hitbox must not permit teleporting into a low ceiling");
        level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        for (var hazard : List.of(Blocks.LAVA, Blocks.WATER, Blocks.FIRE, Blocks.POWDER_SNOW, Blocks.SWEET_BERRY_BUSH)) {
            level.setBlock(feet, hazard.defaultBlockState(), 3);
            h.assertFalse(PlayerEscape.safeLanding(level, feet, player), "Unsafe feet block must be rejected: " + hazard);
        }
        level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        h.assertFalse(PlayerEscape.safeLanding(level, feet, player), "Magma must not count as safe support");
        level.setBlock(feet.below(), Blocks.AIR.defaultBlockState(), 3);
        h.assertFalse(PlayerEscape.safeLanding(level, feet, player), "No support must be rejected");
        var nether = level.getServer().getLevel(Level.NETHER);
        h.assertFalse(PlayerEscape.safeLanding(nether, new BlockPos(0, 128, 0), player), "Never evacuate onto the Nether roof");
        h.succeed();
    }

    @GameTest(batch = "playerEscape", template = "test_empty", timeoutTicks = 100)
    public static void noReceiverEvacuationPreservesPlayer(GameTestHelper h) {
        var level = h.getLevel();
        var player = player(h);
        Vec3 origin = h.absoluteVec(new Vec3(3.5, 3, 3.5));
        player.moveTo(origin);
        player.setHealth(17);
        player.getInventory().setItem(0, new ItemStack(ModItems.PLAYER_TRANSMITTER.get()));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 42));
        player.addEffect(new MobEffectInstance(ModEffects.DIMENSIONAL_ADAPTATION, 1000));
        TwoDimensional.apply(player, true);
        var source = CollapseData.get(level).add(BlockPos.containing(origin), level.getGameTime(), 2, 0, 0);
        var candidates = PlayerEscape.candidates(origin, player.getLookAngle(), CollapseData.get(level).sources(), level.getGameTime(), level.getWorldBorder());
        var first = candidates.getFirst();
        level.getChunk(first.x, first.z);
        int x = first.getMinBlockX() + 8, z = first.getMinBlockZ() + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        level.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(PlayerEscape.begin(player), "Unbound player transmitter must start without a receiver");
        h.runAfterDelay(25, () -> {
            try {
                h.assertTrue(player.position().distanceTo(origin) >= 512, "Player must travel well beyond the collapse");
                h.assertTrue(PlayerEscape.outsideAll(player.position(), CollapseData.get(level).sources(), level.getGameTime()), "Arrival must clear every source");
                h.assertFalse(TwoDimensional.is(player), "Player must immediately leave 2D state");
                h.assertTrue(player.getBbHeight() > 1.7, "Full player collision height must be restored");
                h.assertTrue(player.getInventory().getItem(1).getCount() == 42 && player.getHealth() == 17, "Inventory and health must survive evacuation");
                h.assertTrue(player.hasEffect(ModEffects.DIMENSIONAL_ADAPTATION), "Remaining adaptation effect must be retained");
                h.assertTrue(player.getCooldowns().isOnCooldown(ModItems.PLAYER_TRANSMITTER.get()), "Successful escape must apply cooldown");
                h.succeed();
            } finally { PlayerEscape.cancel(player.getUUID()); CollapseData.get(level).remove(source.id()); }
        });
    }

    @GameTest(batch = "playerEscape", template = "test_empty")
    public static void lateThreatAndCancellationLeavePlayerInPlace(GameTestHelper h) {
        var level = h.getLevel();
        var player = player(h);
        var feet = h.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        player.moveTo(h.absoluteVec(new Vec3(1, 3, 1)));
        Vec3 before = player.position();
        var source = CollapseData.get(level).add(feet, level.getGameTime(), 1, 0, 0);
        try {
            h.assertFalse(PlayerEscape.teleport(player, Vec3.atBottomCenterOf(feet)), "A newly dangerous destination must be rejected at commit time");
            h.assertTrue(player.position().equals(before), "Rejected teleport must not move the player");
            h.assertTrue(PlayerEscape.begin(player), "Search can start");
            h.assertFalse(PlayerEscape.begin(player), "Repeated use must not create parallel searches");
            PlayerEscape.cancel(player.getUUID());
            h.assertTrue(player.position().equals(before), "Cancellation must not move the player");
        } finally { PlayerEscape.cancel(player.getUUID()); CollapseData.get(level).remove(source.id()); }
        h.succeed();
    }
}
