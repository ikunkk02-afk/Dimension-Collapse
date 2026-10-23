package com.shouyun.dimensioncollapse.item;

import com.shouyun.dimensioncollapse.entity.DualVectorFoilEntity;
import com.shouyun.dimensioncollapse.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DualVectorFoilItem extends DescribedItem {
    public DualVectorFoilItem(Properties properties) { super(properties, 2); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            DualVectorFoilEntity foil = new DualVectorFoilEntity(level, player);
            foil.setItem(stack);
            foil.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 0.65f, 0);
            if (!level.addFreshEntity(foil)) return InteractionResultHolder.fail(stack);
            stack.consume(1, player);
            player.getCooldowns().addCooldown(this, 20);
            level.playSound(null, player.blockPosition(), ModSounds.LAUNCHER_FIRE.get(), SoundSource.PLAYERS, 0.5f, 1.25f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
