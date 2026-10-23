package com.shouyun.dimensioncollapse.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An item whose tooltip explains it, read from {@code <description id>.desc.1 .. .desc.N}. */
public class DescribedItem extends Item {
    private final int descriptionLines;

    public DescribedItem(Properties properties, int descriptionLines) {
        super(properties);
        this.descriptionLines = descriptionLines;
    }

    public static void describe(String descriptionId, int lines, List<Component> tooltip) {
        for (int i = 1; i <= lines; i++) {
            tooltip.add(Component.translatable(descriptionId + ".desc." + i).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        describe(getDescriptionId(), descriptionLines, tooltip);
    }
}
