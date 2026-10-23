package com.shouyun.dimensioncollapse.gametest;

import java.util.Set;
import com.shouyun.dimensioncollapse.block.SuppressionBlockEntity;
import com.shouyun.dimensioncollapse.block.SuppressionController;
import com.shouyun.dimensioncollapse.block.TransferReceiverBlockEntity;
import com.shouyun.dimensioncollapse.collapse.CollapseData;
import com.shouyun.dimensioncollapse.collapse.CollapseSource;
import com.shouyun.dimensioncollapse.collapse.CollapseManager;
import com.shouyun.dimensioncollapse.collapse.TwoDimensional;
import com.shouyun.dimensioncollapse.entity.DualVectorFoilEntity;
import com.shouyun.dimensioncollapse.network.FlattenedEntityPayload;
import com.shouyun.dimensioncollapse.registry.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dimensioncollapse")
@PrefixGameTestTemplate(false)
public class CollapseGameTests {
    @GameTest(template = "test_empty")
    public static void launcherChargeAndAmmunition(GameTestHelper h) {
        var level = h.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var launcher = ModItems.FOIL_LAUNCHER.get();
        var stack = new ItemStack(launcher);
        player.moveTo(h.absoluteVec(new Vec3(2.5, 3, 2.5)));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            h.assertFalse(launcher.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "No ammunition must reject charging");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.DUAL_VECTOR_FOIL.get(), 2));
            launcher.use(level, player, InteractionHand.MAIN_HAND);
            launcher.releaseUsing(stack, level, player, 72000 - 29);
            h.assertTrue(player.getOffhandItem().getCount() == 2, "Early release must not consume ammunition");
            launcher.releaseUsing(stack, level, player, 72000 - 30);
            h.assertTrue(player.getOffhandItem().getCount() == 1, "Charged launch consumes exactly one foil");
            h.assertTrue(player.getCooldowns().isOnCooldown(launcher), "Successful launch applies cooldown");
            var shots = level.getEntitiesOfClass(DualVectorFoilEntity.class, player.getBoundingBox().inflate(4), entity -> entity.getOwner() == player);
            h.assertTrue(shots.size() == 1, "Only the completed charge may create a projectile");
            shots.forEach(net.minecraft.world.entity.Entity::discard);
        } finally {
            player.stopUsingItem(); player.getInventory().clearContent(); player.getCooldowns().removeCooldown(launcher);
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void pauseAndPayloadRoundTrip(GameTestHelper h) {
        var source = new CollapseSource(1, BlockPos.ZERO, 0, -1, 1, 1, 0);
        var paused = source.withSuppressed(40, true);
        h.assertTrue(paused.radius(200) == 3, "Suppression must pause elapsed time");
        var manual = paused.freeze(80).withSuppressed(100, false);
        h.assertTrue(manual.frozen(), "Removing fuel must not undo an operator freeze");
        var resumed = manual.resume(200);
        h.assertTrue(resumed.radius(220) == 4, "Resume must not catch up the paused time");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CollapseSource.STREAM_CODEC.encode(buf, paused);
            h.assertTrue(CollapseSource.STREAM_CODEC.decode(buf).equals(paused), "Source sync must preserve suppression state");
            var payload = new FlattenedEntityPayload(14, 12.5, -20.9375, 33.25);
            FlattenedEntityPayload.STREAM_CODEC.encode(buf, payload);
            h.assertTrue(FlattenedEntityPayload.STREAM_CODEC.decode(buf).equals(payload), "Flatten sync must carry exact final XYZ");
            var fx = new com.shouyun.dimensioncollapse.network.FoilFxPayload(
                    net.minecraft.world.level.Level.OVERWORLD, 12.5, -20.9375, 33.25, true);
            com.shouyun.dimensioncollapse.network.FoilFxPayload.STREAM_CODEC.encode(buf, fx);
            h.assertTrue(com.shouyun.dimensioncollapse.network.FoilFxPayload.STREAM_CODEC.decode(buf).equals(fx),
                    "Foil effect sync must preserve dimension, exact position and unfold phase");
        } finally { buf.release(); }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void receiverAtomicAndPersistent(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var state = ModBlocks.TRANSFER_RECEIVER.get().defaultBlockState();
        var receiver = new TransferReceiverBlockEntity(pos, state);
        for (int i = 0; i < 27; i++) receiver.setItem(i, new ItemStack(Items.DIAMOND, 64));
        receiver.setItem(0, new ItemStack(Items.DIAMOND, 63));
        var cargo = new ItemStack(Items.DIAMOND, 2);
        h.assertFalse(receiver.acceptAll(cargo), "Insufficient capacity must reject the whole transfer");
        h.assertTrue(receiver.getItem(0).getCount() == 63 && cargo.getCount() == 2, "Rejected transfer must change neither inventory");
        receiver.setItem(1, ItemStack.EMPTY);
        h.assertTrue(receiver.acceptAll(cargo), "Split across existing and empty slots");
        h.assertTrue(receiver.getItem(0).getCount() == 64 && receiver.getItem(1).getCount() == 1, "Accepted transfer must conserve items");
        CompoundTag saved = receiver.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored = new TransferReceiverBlockEntity(pos, state);
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(restored.getItem(1).getCount() == 1, "Received items must survive save/load");
        restored.setItem(1, ItemStack.EMPTY);
        h.assertTrue(restored.acceptAll(new ItemStack(Items.DIAMOND_SWORD)), "Non-stackable resources must transfer");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void suppressionFuelOverlapAndRemoval(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos firstPos = h.absolutePos(new BlockPos(4, 2, 4));
        BlockPos secondPos = firstPos.east();
        level.setBlock(firstPos, ModBlocks.EXPANSION_SUPPRESSOR.get().defaultBlockState(), 3);
        level.setBlock(secondPos, ModBlocks.EXPANSION_SUPPRESSOR.get().defaultBlockState(), 3);
        var first = (SuppressionBlockEntity)level.getBlockEntity(firstPos);
        var second = (SuppressionBlockEntity)level.getBlockEntity(secondPos);
        var data = CollapseData.get(level);
        var source = data.add(firstPos.west(3), level.getGameTime(), 1, 1, 0);
        Set<Long> chunks = new java.util.HashSet<>(Set.of(new ChunkPos(firstPos).toLong()));
        chunks.add(new ChunkPos(secondPos).toLong());
        try {
            first.addFuel(); second.addFuel();
            SuppressionController.tick(level, chunks);
            h.assertTrue(data.find(source.id()).orElseThrow().suppressed(), "Fueled devices must suppress nearby collapse");
            h.assertTrue(first.fuelTicks() == 1199, "One flat matter supplies 1200 working ticks");
            first.toggle();
            SuppressionController.tick(level, chunks);
            h.assertTrue(data.find(source.id()).orElseThrow().suppressed(), "Second suppressor must keep source paused");
            h.assertTrue(first.fuelTicks() == 1199, "Disabled devices must not burn fuel");
            CompoundTag lastTick = new CompoundTag(); lastTick.putInt("Fuel", 1); lastTick.putBoolean("Enabled", true);
            second.loadWithComponents(lastTick, level.registryAccess());
            SuppressionController.tick(level, chunks);
            h.assertTrue(second.fuelTicks() == 0 && data.find(source.id()).orElseThrow().suppressed(), "Final fuel tick still protects");
            SuppressionController.tick(level, chunks);
            h.assertFalse(data.find(source.id()).orElseThrow().frozen(), "Exhausted fuel must resume expansion");
            second.addFuel();
            SuppressionController.tick(level, chunks);
            level.removeBlock(secondPos, false);
            SuppressionController.tick(level, chunks);
            h.assertFalse(data.find(source.id()).orElseThrow().frozen(), "Removed suppressor must release its source");
            first.toggle();
            SuppressionController.tick(level, chunks);
            SuppressionController.tick(level, Set.of());
            h.assertFalse(data.find(source.id()).orElseThrow().frozen(), "Unloaded devices must not suppress for free");
        } finally { data.remove(source.id()); level.removeBlock(firstPos, false); level.removeBlock(secondPos, false); }
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 80)
    public static void creatureSettlesOnPlane(GameTestHelper h) {
        var level = h.getLevel();
        var center = h.absolutePos(new BlockPos(3, 2, 3));
        var source = CollapseData.get(level).add(center, level.getGameTime(), 5, 0, 0);
        var cow = h.spawn(EntityType.COW, new Vec3(3.5, 5, 3.5));
        cow.setNoGravity(true);
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(CollapseManager.isFlattened(cow), "Creature should be flattened");
                h.assertTrue(Math.abs(cow.getY() - (center.getY() + 1.0/16)) < 1e-8, "Creature must be on sheet, not at its original height");
                h.assertTrue(cow.yo == cow.getY(), "Previous render height must also be on sheet");
                h.assertTrue(cow.getDeltaMovement().lengthSqr() == 0, "Flattened creatures must not drift");
                h.succeed();
            } finally { CollapseData.get(level).remove(source.id()); cow.discard(); }
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void transmitterSuccessAndFailure(GameTestHelper h) {
        var level = h.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var safePos = h.absolutePos(new BlockPos(400, 2, 0));
        var oldState = level.getBlockState(safePos);
        var transmitter = ModItems.RESOURCE_TRANSMITTER.get();
        var device = new ItemStack(transmitter);
        try {
            level.setBlock(safePos, ModBlocks.TRANSFER_RECEIVER.get().defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, device);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.FLAT_MATTER.get(), 32));
            transmitter.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(safePos), Direction.UP, safePos, false)));
            h.assertTrue(device.get(ModDataComponents.RECEIVER_LINK.get()).equals(GlobalPos.of(level.dimension(), safePos)), "Use on receiver must bind coordinates");
            transmitter.use(level, player, InteractionHand.MAIN_HAND);
            h.assertTrue(player.getOffhandItem().getCount() == 32, "3D player must not send cargo");
            TwoDimensional.apply(player, true);
            transmitter.use(level, player, InteractionHand.MAIN_HAND);
            var receiver = (TransferReceiverBlockEntity)level.getBlockEntity(safePos);
            h.assertTrue(player.getOffhandItem().isEmpty() && receiver.getItem(0).getCount() == 32, "Transfer must move the whole stack exactly once");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 5));
            for (int i = 0; i < 27; i++) receiver.setItem(i, new ItemStack(Items.DIAMOND, 64));
            transmitter.use(level, player, InteractionHand.MAIN_HAND);
            h.assertTrue(player.getOffhandItem().getCount() == 5, "Full receiver must retain cargo");
            receiver.setItem(0, ItemStack.EMPTY);
            var threat = CollapseData.get(level).add(safePos.east(64), level.getGameTime(), 1, 0, 0);
            try {
                transmitter.use(level, player, InteractionHand.MAIN_HAND);
                h.assertTrue(player.getOffhandItem().getCount() == 5 && receiver.getItem(0).isEmpty(), "Unsafe receiver must retain cargo");
            } finally { CollapseData.get(level).remove(threat.id()); }
            level.removeBlock(safePos, false);
            transmitter.use(level, player, InteractionHand.MAIN_HAND);
            h.assertTrue(player.getOffhandItem().getCount() == 5, "Missing receiver must retain cargo");
        } finally {
            level.removeBlockEntity(safePos); level.setBlock(safePos, oldState, 3);
            TwoDimensional.apply(player, false);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 80)
    public static void foilImpactCreatesCollapse(GameTestHelper h) {
        var level = h.getLevel();
        var target = h.absolutePos(new BlockPos(5, 3, 3));
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
        var foil = new DualVectorFoilEntity(ModEntities.DUAL_VECTOR_FOIL.get(), level);
        foil.moveTo(target.getX() - 2, target.getY() + 0.5, target.getZ() + 0.5);
        foil.setDeltaMovement(1, 0, 0);
        level.addFreshEntity(foil);
        h.runAfterDelay(10, () -> {
            var created = CollapseData.get(level).sources().stream().filter(s -> s.center().distSqr(target) <= 1).toList();
            try {
                h.assertTrue(foil.isRemoved(), "Foil should disappear on impact");
                h.assertTrue(created.size() == 1, "Impact must create exactly one collapse");
                h.succeed();
            } finally { created.forEach(s -> CollapseData.get(level).remove(s.id())); foil.discard(); }
        });
    }
}
