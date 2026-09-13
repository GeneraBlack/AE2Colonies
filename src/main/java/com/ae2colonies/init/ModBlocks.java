package com.ae2colonies.init;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.block.ColonyTerminalBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, AE2Colonies.MOD_ID);

    public static final DeferredHolder<Block, ColonyTerminalBlock> COLONY_TERMINAL = BLOCKS.register(
            "colony_terminal",
            ColonyTerminalBlock::new
    );

    public static final DeferredHolder<Block, com.ae2colonies.block.MEArchitectsCutterBlock> ME_ARCHITECTS_CUTTER = BLOCKS.register(
            "me_architects_cutter",
            com.ae2colonies.block.MEArchitectsCutterBlock::new
    );

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
