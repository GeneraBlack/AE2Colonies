package com.ae2colonies.init;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2Colonies.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColonyTerminalBlockEntity>> COLONY_TERMINAL =
            BLOCK_ENTITIES.register(
                    "colony_terminal",
                    () -> {
                        BlockEntityType<ColonyTerminalBlockEntity> type = BlockEntityType.Builder.of(
                                ColonyTerminalBlockEntity::new,
                                ModBlocks.COLONY_TERMINAL.get()
                        ).build(null);
                        ModBlocks.COLONY_TERMINAL.get().setBlockEntity(
                                ColonyTerminalBlockEntity.class,
                                type,
                                null,
                                null
                        );
                        return type;
                    }
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity>> ME_ARCHITECTS_CUTTER =
            BLOCK_ENTITIES.register(
                    "me_architects_cutter",
                    () -> {
                        BlockEntityType<com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity> type = BlockEntityType.Builder.of(
                                com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity::new,
                                ModBlocks.ME_ARCHITECTS_CUTTER.get()
                        ).build(null);
                        ModBlocks.ME_ARCHITECTS_CUTTER.get().setBlockEntity(
                                com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity.class,
                                type,
                                null,
                                (level, pos, state, be) -> be.serverTick()
                        );
                        return type;
                    }
            );

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
