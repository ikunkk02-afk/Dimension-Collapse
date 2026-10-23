package com.shouyun.dimensioncollapse.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class DescribedBlockItem extends BlockItem {
    private final int lines;
    public DescribedBlockItem(Block block, Properties properties, int lines) { super(block, properties); this.lines = lines; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        DescribedItem.describe(getDescriptionId(), lines, tooltip);
    }
}
