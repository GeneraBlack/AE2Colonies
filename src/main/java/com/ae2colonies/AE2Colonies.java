package com.ae2colonies;

import com.ae2colonies.init.ModBlockEntities;
import com.ae2colonies.init.ModBlocks;
import com.ae2colonies.init.ModCreativeTabs;
import com.ae2colonies.init.ModItems;
import com.ae2colonies.init.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AE2Colonies.MOD_ID)
public class AE2Colonies {
    public static final String MOD_ID = "ae2colonies";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public AE2Colonies(IEventBus modEventBus) {
        LOGGER.info("AE2Colonies initializing...");

        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent.class, event -> {
            event.registerBlockEntity(
                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    ModBlockEntities.ME_ARCHITECTS_CUTTER.get(),
                    (be, side) -> be.getItemHandler(side)
            );
        });

        LOGGER.info("AE2Colonies registries registered.");
    }
}
