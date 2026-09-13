package com.ae2colonies.colony;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
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
            colony = IColonyManager.getInstance().getClosestIColony(level, pos);
        }
        if (colony == null) {
            return;
        }

        IRegisteredStructureManager sm = colony.getServerBuildingManager();
        if (sm == null) {
            return;
        }

        List<IWareHouse> warehouses = sm.getWareHouses();
        IWareHouse warehouse = null;

        // 1. Check if pos is strictly within any warehouse's footprint/schematic
        if (warehouses != null) {
            for (IWareHouse wh : warehouses) {
                if (wh.isInBuilding(pos)) {
                    warehouse = wh;
                    break;
                }
            }

            // 2. If not strictly within bounding box, find closest warehouse in colony
            if (warehouse == null && !warehouses.isEmpty()) {
                IWareHouse closest = null;
                double closestDistSq = Double.MAX_VALUE;
                for (IWareHouse wh : warehouses) {
                    double distSq = pos.distSqr(wh.getPosition());
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = wh;
                    }
                }
                // Allow linking if within reasonable distance (64 blocks of warehouse hut)
                if (closestDistSq <= 4096.0) {
                    warehouse = closest;
                }
            }
        }

        // 3. Fallback to closest warehouse in colony
        if (warehouse == null) {
            warehouse = sm.getClosestWarehouseInColony(pos);
        }

        if (warehouse != null) {
            BlockPos whPos = warehouse.getPosition();
            boolean wasLinked = whPos.equals(terminal.getLinkedWarehousePos()) && terminal.getLinkedColonyId() == colony.getID();
            if (!wasLinked) {
                terminal.setLinkedWarehouse(colony.getID(), whPos);
                AE2Colonies.LOGGER.info("Linked Colony Terminal at {} to Warehouse at {} (Colony #{})", pos, whPos, colony.getID());
            }

            List<ColonyTerminalBlockEntity> list = WAREHOUSE_TERMINALS.computeIfAbsent(whPos, k -> new ArrayList<>());
            if (!list.contains(terminal)) {
                list.add(terminal);
            }
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
        }
    }

    public static void clearCaches() {
        WAREHOUSE_TERMINALS.clear();
        AE2Colonies.LOGGER.debug("Cleared WarehouseMEBridge terminal caches");
    }

    public static void onLevelUnload(@NotNull Level level) {
        WAREHOUSE_TERMINALS.values().forEach(list -> list.removeIf(t -> t.getLevel() == level || t.isRemoved()));
        WAREHOUSE_TERMINALS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @NotNull
    public static List<ColonyTerminalBlockEntity> getTerminalsForWarehouse(@NotNull IWareHouse warehouse) {
        List<ColonyTerminalBlockEntity> terminals = WAREHOUSE_TERMINALS.get(warehouse.getPosition());
        if (terminals == null || terminals.isEmpty()) {
            return Collections.emptyList();
        }
        Level whLevel = warehouse.getColony() != null ? warehouse.getColony().getWorld() : null;
        terminals.removeIf(t -> t.isRemoved() || t.getLevel() == null || (whLevel != null && t.getLevel() != whLevel));
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