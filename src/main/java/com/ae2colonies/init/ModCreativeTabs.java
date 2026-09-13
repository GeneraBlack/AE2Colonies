package com.ae2colonies.init;

import com.ae2colonies.AE2Colonies;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AE2Colonies.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2colonies"))
                    .icon(() -> new ItemStack(ModItems.COLONY_TERMINAL.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.COLONY_TERMINAL.get());
                        output.accept(ModItems.ME_ARCHITECTS_CUTTER.get());
                    })
                    .build()
            );

    public static void register(IEventBus bus) {
        CREATIVE_TABS.register(bus);
    }
}
