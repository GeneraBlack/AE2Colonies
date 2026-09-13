package com.ae2colonies.init;

import com.ae2colonies.AE2Colonies;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AE2Colonies.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> COLONY_TERMINAL = ITEMS.register(
            "colony_terminal",
            () -> new BlockItem(ModBlocks.COLONY_TERMINAL.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> ME_ARCHITECTS_CUTTER = ITEMS.register(
            "me_architects_cutter",
            () -> new BlockItem(ModBlocks.ME_ARCHITECTS_CUTTER.get(), new Item.Properties())
    );

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
