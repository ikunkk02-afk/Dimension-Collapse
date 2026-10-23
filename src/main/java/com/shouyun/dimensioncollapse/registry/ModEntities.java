package com.shouyun.dimensioncollapse.registry;

import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.entity.DualVectorFoilEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, DimensionCollapse.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DualVectorFoilEntity>> DUAL_VECTOR_FOIL = ENTITIES.register("dual_vector_foil",
            () -> EntityType.Builder.<DualVectorFoilEntity>of(DualVectorFoilEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("dual_vector_foil"));

    private ModEntities() {}
}
