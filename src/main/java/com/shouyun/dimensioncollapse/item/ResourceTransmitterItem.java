package com.shouyun.dimensioncollapse.item;

import java.util.List;
import com.shouyun.dimensioncollapse.block.TransferReceiverBlockEntity;
import com.shouyun.dimensioncollapse.collapse.CollapseData;
import com.shouyun.dimensioncollapse.collapse.TwoDimensional;
import com.shouyun.dimensioncollapse.registry.ModDataComponents;
import com.shouyun.dimensioncollapse.registry.ModSounds;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ResourceTransmitterItem extends DescribedItem {
    public static final double SAFETY_MARGIN = 128;
    public ResourceTransmitterItem(Properties properties) { super(properties, 3); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof TransferReceiverBlockEntity)) return InteractionResult.PASS;
        if (!level.isClientSide && context.getPlayer() != null) {
            context.getItemInHand().set(ModDataComponents.RECEIVER_LINK.get(), GlobalPos.of(level.dimension(), context.getClickedPos()));
            context.getPlayer().displayClientMessage(Component.translatable("message.dimensioncollapse.transfer.bound",
                    context.getClickedPos().toShortString()), true);
            level.playSound(null, context.getClickedPos(), ModSounds.TRANSFER_LINK.get(), SoundSource.PLAYERS, 0.8f, 1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean isSafe(ServerLevel level, Vec3 position) {
        for (var source : CollapseData.get(level).sources()) {
            if (position.distanceTo(Vec3.atCenterOf(source.center())) < source.radius(level.getGameTime()) + SAFETY_MARGIN) return false;
        }
        return true;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack device = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(device);
        if (hand != InteractionHand.MAIN_HAND || !TwoDimensional.is(player)) return fail(player, device, "outside");
        GlobalPos link = device.get(ModDataComponents.RECEIVER_LINK.get());
        if (link == null) return fail(player, device, "unbound");
        ItemStack cargo = player.getOffhandItem();
        if (cargo.isEmpty()) return fail(player, device, "empty");
        ServerLevel destination = ((ServerLevel) level).getServer().getLevel(link.dimension());
        if (destination == null || !destination.isInWorldBounds(link.pos()) || !destination.getWorldBorder().isWithinBounds(link.pos())) return fail(player, device, "missing");
        if (!isSafe(destination, Vec3.atCenterOf(link.pos()))) return fail(player, device, "unsafe");
        // Load just the linked chunk for this transaction. No permanent force-loading or entity drops.
        destination.getChunk(link.pos());
        if (!(destination.getBlockEntity(link.pos()) instanceof TransferReceiverBlockEntity receiver)) return fail(player, device, "missing");
        if (!receiver.canOpen(player)) return fail(player, device, "locked");
        if (!receiver.acceptAll(cargo)) return fail(player, device, "full");
        int count = cargo.getCount();
        Component name = cargo.getHoverName();
        cargo.setCount(0);
        player.getInventory().setChanged();
        player.getCooldowns().addCooldown(this, 10);
        player.displayClientMessage(Component.translatable("message.dimensioncollapse.transfer.sent", count, name), true);
        level.playSound(null, player.blockPosition(), ModSounds.TRANSFER_SEND.get(), SoundSource.PLAYERS, 0.8f, 1);
        destination.playSound(null, link.pos(), ModSounds.TRANSFER_RECEIVE.get(), SoundSource.BLOCKS, 0.8f, 1);
        ((ServerLevel)level).sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.15, player.getZ(), 16, 0.3, 0.1, 0.3, 0.02);
        destination.sendParticles(ParticleTypes.PORTAL, link.pos().getX() + 0.5, link.pos().getY() + 1.1, link.pos().getZ() + 0.5, 24, 0.3, 0.2, 0.3, 0.02);
        return InteractionResultHolder.consume(device);
    }

    private InteractionResultHolder<ItemStack> fail(Player player, ItemStack stack, String reason) {
        player.displayClientMessage(Component.translatable("message.dimensioncollapse.transfer." + reason), true);
        player.playNotifySound(ModSounds.TRANSFER_FAIL.get(), SoundSource.PLAYERS, 0.5f, 1);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.fail(stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        GlobalPos link = stack.get(ModDataComponents.RECEIVER_LINK.get());
        if (link != null) tooltip.add(Component.translatable("message.dimensioncollapse.transfer.link", link.dimension().location().toString(), link.pos().toShortString()));
    }
}
