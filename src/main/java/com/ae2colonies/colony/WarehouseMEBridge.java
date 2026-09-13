package com.ae2colonies.colony;

import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WarehouseMEBridge {

    private static final Map<BlockPos, List<ColonyTerminalBlockEntity>> WAREHOUSE_TERMINALS = new ConcurrentHashMap<>();

    public static void registerTerminal(@NotNull ColonyTerminalBlockEntity terminal) {
        Level level = terminal.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos pos = terminal.getBlockPos();
        IColony colony = IColonyManager.getInstance().getIColony(level, pos);
        if (colony == null) {
            return;
        }

        IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        IWareHouse warehouse = null;

        if (building instanceof IWareHouse wh) {
            warehouse = wh;
        } else {
            warehouse = colony.getServerBuildingManager().getClosestWarehouseInColony(pos);
        }

        if (warehouse != null) {
            BlockPos whPos = warehouse.getPosition();
            terminal.setLinkedWarehouse(colony.getID(), whPos);

            WAREHOUSE_TERMINALS.computeIfAbsent(whPos, k -> new ArrayList<>()).add(terminal);
            warehouse.addContainerPosition(pos);
        }
    }

    public static void unregisterTerminal(@NotNull ColonyTerminalBlockEntity terminal) {
        Level level = terminal.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos whPos = terminal.getLinkedWarehousePos();
        if (whPos != null) {
            List<ColonyTerminalBlockEntity> list = WAREHOUSE_TERMINALS.get(whPos);
            if (list != null) {
                list.remove(terminal);
                if (list.isEmpty()) {
                    WAREHOUSE_TERMINALS.remove(whPos);
                }
            }

            IColony colony = IColonyManager.getInstance().getColonyByWorld(terminal.getLinkedColonyId(), level);
            if (colony != null) {
                IBuilding building = colony.getServerBuildingManager().getBuilding(whPos);
                if (building instanceof IWareHouse warehouse) {
                    warehouse.removeContainerPosition(terminal.getBlockPos());
                }
            }
        }
    }

    @NotNull
    public static List<ColonyTerminalBlockEntity> getTerminalsForWarehouse(@NotNull IWareHouse warehouse) {
        List<ColonyTerminalBlockEntity> terminals = WAREHOUSE_TERMINALS.get(warehouse.getPosition());
        if (terminals == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(terminals);
    }

    @Nullable
    public static ColonyTerminalBlockEntity getTerminalAt(@NotNull Level level, @NotNull BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ColonyTerminalBlockEntity terminal) {
            return terminal;
        }
        return null;
    }
}
