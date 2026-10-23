package com.shouyun.dimensioncollapse.block;

import com.shouyun.dimensioncollapse.registry.ModBlockEntities;
import com.shouyun.dimensioncollapse.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SuppressionBlockEntity extends BlockEntity {
    public static final int TICKS_PER_MATTER = 1200;
    public static final int MAX_FUEL = TICKS_PER_MATTER * 64;
    private int fuelTicks;
    private boolean enabled = true;

    public SuppressionBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SUPPRESSOR.get(), pos, state); }
    public int fuelTicks() { return fuelTicks; }
    public boolean enabled() { return enabled; }
    public boolean canSuppress() { return enabled && fuelTicks > 0; }
    public boolean addFuel() {
        if (fuelTicks > MAX_FUEL - TICKS_PER_MATTER) return false;
        fuelTicks += TICKS_PER_MATTER;
        setChanged();
        return true;
    }
    public void toggle() { enabled = !enabled; setChanged(); }

    public void updateActive(boolean active) {
        if (level == null || level.isClientSide) return;
        if (getBlockState().getValue(SuppressionBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(SuppressionBlock.ACTIVE, active), 3);
            level.playSound(null, worldPosition, active ? ModSounds.SUPPRESSOR_ACTIVATE.get() : ModSounds.SUPPRESSOR_DEACTIVATE.get(), SoundSource.BLOCKS, 0.8f, 1);
        }
        if (active) {
            fuelTicks--;
            setChanged();
            if (level.getGameTime() % 60 == 0) level.playSound(null, worldPosition, ModSounds.SUPPRESSOR_HUM.get(), SoundSource.BLOCKS, 0.4f, 0.8f);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putInt("Fuel", fuelTicks); tag.putBoolean("Enabled", enabled);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuelTicks = Math.clamp(tag.getInt("Fuel"), 0, MAX_FUEL);
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
    }
}
