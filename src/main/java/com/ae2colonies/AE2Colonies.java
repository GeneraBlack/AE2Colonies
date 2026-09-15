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
                    ModBlockEntities.COLONY_TERMINAL.get(),
                    (be, side) -> be.getItemHandler(side)
            );
            event.registerBlockEntity(
                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    ModBlockEntities.ME_ARCHITECTS_CUTTER.get(),
                    (be, side) -> be.getItemHandler(side)
            );

            event.registerBlockEntity(
                    appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    ModBlockEntities.COLONY_TERMINAL.get(),
                    (be, context) -> be
            );
            event.registerBlockEntity(
                    appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    ModBlockEntities.ME_ARCHITECTS_CUTTER.get(),
                    (be, context) -> be
            );
        });

        modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent.class, event -> {
            event.enqueueWork(() -> {
                appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                        ModBlockEntities.COLONY_TERMINAL.get(),
                        ModItems.COLONY_TERMINAL.get()
                );
                appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                        ModBlockEntities.ME_ARCHITECTS_CUTTER.get(),
                        ModItems.ME_ARCHITECTS_CUTTER.get()
                );
            });
        });

        var forgeBus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

        // Reset state when a new server starts (important for singleplayer world switches)
        forgeBus.addListener(net.neoforged.neoforge.event.server.ServerStartingEvent.class, event -> {
            com.ae2colonies.colony.WarehouseMEBridge.resetForNewServer();
        });

        // Set shutdown flag ASAP when server begins stopping
        forgeBus.addListener(net.neoforged.neoforge.event.server.ServerStoppingEvent.class, event -> {
            com.ae2colonies.colony.WarehouseMEBridge.beginShutdown();
            com.ae2colonies.colony.WarehouseMEBridge.clearCaches();
        });

        // Clean up when levels are unloaded
        forgeBus.addListener(net.neoforged.neoforge.event.level.LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
                com.ae2colonies.colony.WarehouseMEBridge.onLevelUnload(level);
            }
        });

        // Final cleanup after server has fully stopped
        forgeBus.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, event -> {
            com.ae2colonies.colony.WarehouseMEBridge.clearCaches();
        });

        LOGGER.info("AE2Colonies registries registered.");
    }
}

