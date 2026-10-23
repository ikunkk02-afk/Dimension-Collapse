package com.shouyun.dimensioncollapse.collapse;

import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.network.TwoDimensionalPayload;
import com.shouyun.dimensioncollapse.registry.ModAttachments;
import com.shouyun.dimensioncollapse.registry.ModEffects;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Players under dimensional adaptation who are inside a collapse live in its plane: their body is a
 * sliver, their eyes sit just above the sheet, and they cannot leave the plane vertically.
 */
public final class TwoDimensional {
    public static final ResourceKey<DamageType> FLATTENED_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(DimensionCollapse.MODID, "flattened"));

    private static final ResourceLocation FLIGHT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(DimensionCollapse.MODID, "two_dimensional");
    private static final EntityDimensions DIMENSIONS = EntityDimensions.scalable(0.6f, 0.2f).withEyeHeight(0.1f);

    private TwoDimensional() {}

    public static void register(IEventBus bus) {
        bus.addListener(TwoDimensional::onPlayerTickPre);
        bus.addListener(TwoDimensional::onPlayerTickPost);
        bus.addListener(TwoDimensional::onEntitySize);
    }

    public static boolean is(Entity entity) {
        return entity.hasData(ModAttachments.TWO_DIMENSIONAL) && entity.getData(ModAttachments.TWO_DIMENSIONAL);
    }

    public static boolean isAdapted(Player player) {
        return player.hasEffect(ModEffects.DIMENSIONAL_ADAPTATION);
    }

    public static DamageSource flattenedDamage(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FLATTENED_DAMAGE));
    }

    /** Applied on both sides when the server announces a change, so prediction and hitbox agree. */
    public static void apply(Entity entity, boolean active) {
        entity.setData(ModAttachments.TWO_DIMENSIONAL, active);
        entity.refreshDimensions();
    }

    private static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            update(serverPlayer);
        }
        if (is(player)) {
            player.getAbilities().flying = false;
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, 0.0, motion.z);
            player.resetFallDistance();
            snapToPlane(player);
        }
    }

    private static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (is(event.getEntity())) {
            snapToPlane(event.getEntity());
        }
    }

    private static void onEntitySize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player && is(event.getEntity())) {
            event.setNewSize(DIMENSIONS);
        }
    }

    private static void snapToPlane(Player player) {
        CollapseSource source = CollapseQueries.containing(player.level(), player.position());
        if (source == null) return;
        double planeTop = CollapseQueries.planeTop(source);
        if (Math.abs(player.getY() - planeTop) > 1.0e-3) {
            player.setPos(player.getX(), planeTop, player.getZ());
        }
        player.setOnGround(true);
    }

    private static void update(ServerPlayer player) {
        boolean wanted = !player.isSpectator() && isAdapted(player)
                && CollapseQueries.containing(player.level(), player.position()) != null;
        if (wanted == is(player)) return;

        apply(player, wanted);
        player.level().playSound(null, player.blockPosition(), wanted
                ? com.shouyun.dimensioncollapse.registry.ModSounds.PLANE_ENTER.get()
                : com.shouyun.dimensioncollapse.registry.ModSounds.PLANE_LEAVE.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.0f);
        // Floating over empty plane would otherwise trip the server's anti-flight check.
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null) {
            if (wanted) {
                flight.addOrUpdateTransientModifier(new AttributeModifier(FLIGHT_MODIFIER, 1.0, AttributeModifier.Operation.ADD_VALUE));
            } else {
                flight.removeModifier(FLIGHT_MODIFIER);
            }
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TwoDimensionalPayload(player.getId(), wanted));
    }
}
