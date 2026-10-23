package com.shouyun.dimensioncollapse.block;

import com.shouyun.dimensioncollapse.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TransferReceiverBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    public TransferReceiverBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.RECEIVER.get(), pos, state); }
    @Override public int getContainerSize() { return 27; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override protected Component getDefaultName() { return Component.translatable("container.dimensioncollapse.receiver"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return ChestMenu.threeRows(id, inventory, this); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); items = NonNullList.withSize(27, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, registries);
    }

    /** All-or-nothing insertion, server thread only. Never mutates the supplied stack. */
    public boolean acceptAll(ItemStack stack) {
        if (stack.isEmpty()) return false;
        int capacity = 0;
        for (ItemStack existing : items) {
            int max = Math.min(getMaxStackSize(), stack.getMaxStackSize());
            if (existing.isEmpty()) capacity += max;
            else if (ItemStack.isSameItemSameComponents(existing, stack)) capacity += Math.max(0, max - existing.getCount());
        }
        if (capacity < stack.getCount()) return false;
        int remaining = stack.getCount();
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack existing = items.get(i);
            int max = Math.min(getMaxStackSize(), stack.getMaxStackSize());
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) continue;
            int amount = Math.min(remaining, max - existing.getCount());
            if (amount <= 0) continue;
            if (existing.isEmpty()) items.set(i, stack.copyWithCount(amount)); else existing.grow(amount);
            remaining -= amount;
        }
        setChanged();
        return true;
    }
}
