package com.shouyun.dimensioncollapse.registry;

import com.shouyun.dimensioncollapse.DimensionCollapse;
import com.shouyun.dimensioncollapse.item.SuppressantItem;
import com.shouyun.dimensioncollapse.item.DescribedItem;
import com.shouyun.dimensioncollapse.item.DescribedBlockItem;
import com.shouyun.dimensioncollapse.item.DualVectorFoilItem;
import com.shouyun.dimensioncollapse.item.FoilLauncherItem;
import com.shouyun.dimensioncollapse.item.ResourceTransmitterItem;
import com.shouyun.dimensioncollapse.item.PlayerTransmitterItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DimensionCollapse.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DimensionCollapse.MODID);

    public static final DeferredItem<DualVectorFoilItem> DUAL_VECTOR_FOIL = ITEMS.register("dual_vector_foil",
            () -> new DualVectorFoilItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final DeferredItem<FoilLauncherItem> FOIL_LAUNCHER = ITEMS.register("foil_launcher",
            () -> new FoilLauncherItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<ResourceTransmitterItem> RESOURCE_TRANSMITTER = ITEMS.register("resource_transmitter",
            () -> new ResourceTransmitterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<PlayerTransmitterItem> PLAYER_TRANSMITTER = ITEMS.register("player_transmitter",
            () -> new PlayerTransmitterItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<DescribedBlockItem> EXPANSION_SUPPRESSOR = ITEMS.register("expansion_suppressor",
            () -> new DescribedBlockItem(ModBlocks.EXPANSION_SUPPRESSOR.get(), new Item.Properties().rarity(Rarity.RARE), 3));
    public static final DeferredItem<DescribedBlockItem> TRANSFER_RECEIVER = ITEMS.register("transfer_receiver",
            () -> new DescribedBlockItem(ModBlocks.TRANSFER_RECEIVER.get(), new Item.Properties().rarity(Rarity.RARE), 2));

    public static final DeferredItem<SuppressantItem> DIMENSION_SUPPRESSANT = ITEMS.register("dimension_suppressant",
            () -> new SuppressantItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    /** Collected from the collapse plane by two-dimensional players. */
    public static final DeferredItem<DescribedItem> FLAT_MATTER = ITEMS.register("flat_matter",
            () -> new DescribedItem(new Item.Properties().rarity(Rarity.RARE), 2));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dimensioncollapse"))
                    .icon(() -> DIMENSION_SUPPRESSANT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(DIMENSION_SUPPRESSANT.get());
                        output.accept(FLAT_MATTER.get());
                        output.accept(DUAL_VECTOR_FOIL.get());
                        output.accept(FOIL_LAUNCHER.get());
                        output.accept(EXPANSION_SUPPRESSOR.get());
                        output.accept(RESOURCE_TRANSMITTER.get());
                        output.accept(PLAYER_TRANSMITTER.get());
                        output.accept(TRANSFER_RECEIVER.get());
                    })
                    .build());

    private ModItems() {}
}
