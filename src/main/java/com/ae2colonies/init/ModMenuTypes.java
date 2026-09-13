package com.ae2colonies.init;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.menu.ColonyTerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AE2Colonies.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ColonyTerminalMenu>> COLONY_TERMINAL =
            MENUS.register(
                    "colony_terminal",
                    () -> IMenuTypeExtension.create(ColonyTerminalMenu::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<com.ae2colonies.menu.MEArchitectsCutterMenu>> ME_ARCHITECTS_CUTTER_MENU =
            MENUS.register(
                    "me_architects_cutter",
                    () -> IMenuTypeExtension.create(com.ae2colonies.menu.MEArchitectsCutterMenu::new)
            );

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
