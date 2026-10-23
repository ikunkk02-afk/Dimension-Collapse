package com.shouyun.dimensioncollapse.entity;

import com.shouyun.dimensioncollapse.collapse.CollapseCommand;
import com.shouyun.dimensioncollapse.collapse.CollapseManager;
import com.shouyun.dimensioncollapse.registry.ModEntities;
import com.shouyun.dimensioncollapse.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** A drifting foil unfolds on impact, or after five seconds in open air. */
public class DualVectorFoilEntity extends ThrowableItemProjectile {
    private int flightTicks;

    public DualVectorFoilEntity(EntityType<? extends DualVectorFoilEntity> type, Level level) { super(type, level); }
    public DualVectorFoilEntity(Level level, LivingEntity owner) { super(ModEntities.DUAL_VECTOR_FOIL.get(), owner, level); }
    @Override protected Item getDefaultItem() { return ModItems.DUAL_VECTOR_FOIL.get(); }
    @Override protected double getDefaultGravity() { return 0.0; }

    @Override public void tick() {
        super.tick();
        if (isRemoved()) return;
        if (!level().isClientSide && ++flightTicks >= 100) {
            unfold();
        }
    }

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (hit.getType() != HitResult.Type.MISS) {
            setPos(hit.getLocation());
            unfold();
        }
    }

    private void unfold() {
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        var pos = blockPosition();
        if (level.isInWorldBounds(pos)) {
            CollapseManager.start(level, pos, CollapseCommand.DEFAULT_INITIAL_RADIUS,
                    CollapseCommand.DEFAULT_INITIAL_SPEED, CollapseCommand.DEFAULT_ACCELERATION);
            com.shouyun.dimensioncollapse.network.FoilFxPayload.send(level, position(), true);
        }
        discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("FlightTicks", flightTicks); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); flightTicks = tag.getInt("FlightTicks"); }
}
