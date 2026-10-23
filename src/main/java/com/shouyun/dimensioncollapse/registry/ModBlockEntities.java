package com.shouyun.dimensioncollapse.registry;

import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.block.SuppressionBlockEntity;
import com.shouyun.dimensioncollapse.block.TransferReceiverBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DimensionCollapse.MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuppressionBlockEntity>> SUPPRESSOR = TYPES.register("expansion_suppressor",
            () -> BlockEntityType.Builder.of(SuppressionBlockEntity::new, ModBlocks.EXPANSION_SUPPRESSOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransferReceiverBlockEntity>> RECEIVER = TYPES.register("transfer_receiver",
            () -> BlockEntityType.Builder.of(TransferReceiverBlockEntity::new, ModBlocks.TRANSFER_RECEIVER.get()).build(null));
    private ModBlockEntities() {}
}
