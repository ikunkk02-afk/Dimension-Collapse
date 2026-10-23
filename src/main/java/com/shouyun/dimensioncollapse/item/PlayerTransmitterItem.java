package com.shouyun.dimensioncollapse.item;

import com.shouyun.dimensioncollapse.teleport.PlayerEscape;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reusable personal escape device; no receiver or bound coordinates required. */
public class PlayerTransmitterItem extends DescribedItem {
    public PlayerTransmitterItem(Properties properties) { super(properties, 3); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && !PlayerEscape.begin(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
