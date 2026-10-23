package com.shouyun.dimensioncollapse.block;

import com.mojang.serialization.MapCodec;
import com.shouyun.dimensioncollapse.registry.ModItems;
import com.shouyun.dimensioncollapse.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class SuppressionBlock extends BaseEntityBlock {
    public static final MapCodec<SuppressionBlock> CODEC = simpleCodec(SuppressionBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public SuppressionBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(ACTIVE, false)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ACTIVE); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SuppressionBlockEntity(pos, state); }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.FLAT_MATTER)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SuppressionBlockEntity device) {
            if (device.addFuel()) {
                stack.consume(1, player);
                level.playSound(null, pos, ModSounds.SUPPRESSOR_FUEL.get(), SoundSource.BLOCKS, 0.7f, 1);
            }
            status(player, device);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SuppressionBlockEntity device) {
            if (player.isShiftKeyDown()) device.toggle();
            status(player, device);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static void status(Player player, SuppressionBlockEntity device) {
        player.displayClientMessage(Component.translatable("message.dimensioncollapse.suppressor.status",
                Component.translatable(device.enabled() ? "message.dimensioncollapse.enabled" : "message.dimensioncollapse.disabled"),
                (device.fuelTicks() + 19) / 20), true);
    }
}
