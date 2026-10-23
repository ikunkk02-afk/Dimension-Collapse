package com.shouyun.dimensioncollapse.item;

import com.shouyun.dimensioncollapse.entity.DualVectorFoilEntity;
import com.shouyun.dimensioncollapse.registry.ModItems;
import com.shouyun.dimensioncollapse.registry.ModSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Hold to charge, release to send a dual-vector foil drifting towards where you aim. */
public class FoilLauncherItem extends DescribedItem {
    public static final int CHARGE_TICKS = 30;
    private static final int COOLDOWN_TICKS = 100;
    private static final float LAUNCH_SPEED = 1.6f;

    public FoilLauncherItem(Properties properties) {
        super(properties, 3);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        if (!player.hasInfiniteMaterials() && findAmmo(player).isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.dimensioncollapse.launcher.no_foil"), true);
            }
            return InteractionResultHolder.fail(launcher);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.LAUNCHER_CHARGE.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
        return InteractionResultHolder.consume(launcher);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide && getUseDuration(stack, entity) - remainingUseDuration == CHARGE_TICKS) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.LAUNCHER_READY.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || getUseDuration(stack, entity) - timeLeft < CHARGE_TICKS) return;
        ItemStack ammo = findAmmo(player);
        boolean infinite = player.hasInfiniteMaterials();
        if (ammo.isEmpty() && !infinite) return;

        if (!level.isClientSide) {
            DualVectorFoilEntity foil = new DualVectorFoilEntity(level, player);
            foil.setItem(ammo.isEmpty() ? ModItems.DUAL_VECTOR_FOIL.get().getDefaultInstance() : ammo.copyWithCount(1));
            foil.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, LAUNCH_SPEED, 0.0f);
            if (!level.addFreshEntity(foil)) return;
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.LAUNCHER_FIRE.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            if (!infinite) ammo.shrink(1);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    private static ItemStack findAmmo(Player player) {
        if (player.getOffhandItem().is(ModItems.DUAL_VECTOR_FOIL)) return player.getOffhandItem();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.DUAL_VECTOR_FOIL)) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }
}
