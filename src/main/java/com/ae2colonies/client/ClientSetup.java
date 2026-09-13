package com.ae2colonies.client;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.client.screen.ColonyTerminalScreen;
import com.ae2colonies.init.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = AE2Colonies.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.COLONY_TERMINAL.get(), ColonyTerminalScreen::new);
        event.register(ModMenuTypes.ME_ARCHITECTS_CUTTER_MENU.get(), com.ae2colonies.client.screen.MEArchitectsCutterScreen::new);
    }
}
