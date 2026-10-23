package com.shouyun.dimensioncollapse.registry;

import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.block.FlattenedBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DimensionCollapse.MODID);

    public static final DeferredBlock<com.shouyun.dimensioncollapse.block.SuppressionBlock> EXPANSION_SUPPRESSOR = BLOCKS.register("expansion_suppressor",
            () -> new com.shouyun.dimensioncollapse.block.SuppressionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.0f, 8.0f).sound(SoundType.METAL).lightLevel(state -> state.getValue(com.shouyun.dimensioncollapse.block.SuppressionBlock.ACTIVE) ? 10 : 0)));
    public static final DeferredBlock<com.shouyun.dimensioncollapse.block.TransferReceiverBlock> TRANSFER_RECEIVER = BLOCKS.register("transfer_receiver",
            () -> new com.shouyun.dimensioncollapse.block.TransferReceiverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0f, 6.0f).sound(SoundType.METAL)));

    public static final DeferredBlock<FlattenedBlock> FLATTENED = BLOCKS.register("flattened",
            () -> new FlattenedBlock(BlockBehaviour.Properties.of()
                    .mapColor(state -> MapColor.byId(state.getValue(FlattenedBlock.COLOR)))
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)));

    private ModBlocks() {}
}
