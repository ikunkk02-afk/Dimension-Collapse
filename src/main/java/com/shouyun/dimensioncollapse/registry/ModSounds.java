package com.shouyun.dimensioncollapse.registry;

import com.shouyun.dimensioncollapse.DimensionCollapse;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, DimensionCollapse.MODID);

    /** Heard from far away, so the server must broadcast it well beyond the usual 16 blocks. */
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_START = fixedRange("collapse.start", 256.0f);
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_AMBIENT = variableRange("collapse.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_CRACKLE = variableRange("collapse.crackle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENTITY_FLATTEN = variableRange("entity.flatten");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANE_ENTER = variableRange("plane.enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANE_LEAVE = variableRange("plane.leave");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCHER_CHARGE = variableRange("launcher.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCHER_READY = variableRange("launcher.ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCHER_FIRE = variableRange("launcher.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPRESSOR_ACTIVATE = variableRange("suppressor.activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPRESSOR_DEACTIVATE = variableRange("suppressor.deactivate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPRESSOR_HUM = variableRange("suppressor.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPRESSOR_FUEL = variableRange("suppressor.fuel");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFER_SEND = variableRange("transfer.send");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFER_RECEIVE = variableRange("transfer.receive");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFER_LINK = variableRange("transfer.link");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFER_FAIL = variableRange("transfer.fail");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> variableRange(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> fixedRange(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(id(name), range));
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(DimensionCollapse.MODID, name);
    }
}
