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
import java.util.concurrent.CopyOnWriteArrayList;

public class WarehouseMEBridge {

    private static volatile boolean shuttingDown = false;
    private static final Map<net.minecraft.core.GlobalPos, CopyOnWriteArrayList<ColonyTerminalBlockEntity>> WAREHOUSE_TERMINALS = new ConcurrentHashMap<>();

    public static boolean isShuttingDown() {
        return shuttingDown;
    }

    public static void registerTerminal(@NotNull ColonyTerminalBlockEntity terminal) {
        if (shuttingDown) {
            return;
        }

        Level level = terminal.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos pos = terminal.getBlockPos();

        IColony colony;
        try {
            colony = IColonyManager.getInstance().getIColony(level, pos);
            if (colony == null) {
                colony = IColonyManager.getInstance().getClosestIColony(level, pos);
            }
        } catch (Exception e) {
            AE2Colonies.LOGGER.info("Failed to look up colony for terminal at {}: {}", pos, e.getMessage());
            return;
        }

        if (colony == null) {
            return;
        }

        IRegisteredStructureManager sm = colony.getServerBuildingManager();
        if (sm == null) {
            return;
        }

        List<IWareHouse> warehouses;
        try {
            warehouses = sm.getWareHouses();
        } catch (Exception e) {
            AE2Colonies.LOGGER.info("Failed to get warehouses for colony {}: {}", colony.getID(), e.getMessage());
            return;
        }

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
            try {
                warehouse = sm.getClosestWarehouseInColony(pos);
            } catch (Exception e) {
                AE2Colonies.LOGGER.info("Failed to find closest warehouse: {}", e.getMessage());
                return;
            }
        }

        if (warehouse != null && !shuttingDown) {
            BlockPos whPos = warehouse.getPosition();
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension = level.dimension();
            net.minecraft.core.GlobalPos globalWhPos = net.minecraft.core.GlobalPos.of(dimension, whPos);
            boolean wasLinked = whPos.equals(terminal.getLinkedWarehousePos()) && terminal.getLinkedColonyId() == colony.getID();
            if (!wasLinked) {
                terminal.setLinkedWarehouse(colony.getID(), whPos);
                AE2Colonies.LOGGER.info("Linked Colony Terminal at {} to Warehouse at {} (Colony #{})", pos, whPos, colony.getID());
            }

            CopyOnWriteArrayList<ColonyTerminalBlockEntity> list = WAREHOUSE_TERMINALS.computeIfAbsent(globalWhPos, k -> new CopyOnWriteArrayList<>());
            if (!list.contains(terminal)) {
                list.addIfAbsent(terminal);
            }
        }
    }

    public static void unregisterTerminal(@NotNull ColonyTerminalBlockEntity terminal) {
        BlockPos whPos = terminal.getLinkedWarehousePos();
        if (whPos != null && terminal.getLevel() != null) {
            net.minecraft.core.GlobalPos globalPos = net.minecraft.core.GlobalPos.of(terminal.getLevel().dimension(), whPos);
            CopyOnWriteArrayList<ColonyTerminalBlockEntity> list = WAREHOUSE_TERMINALS.get(globalPos);
            if (list != null) {
                list.remove(terminal);
                if (list.isEmpty()) {
                    WAREHOUSE_TERMINALS.remove(globalPos);
                }
            }
        }
        // Fallback: also try to remove from any list that contains this terminal
        WAREHOUSE_TERMINALS.values().forEach(l -> l.remove(terminal));
        WAREHOUSE_TERMINALS.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public static void beginShutdown() {
        shuttingDown = true;
        AE2Colonies.LOGGER.info("WarehouseMEBridge: shutdown flag set");
    }

    public static void clearCaches() {
        shuttingDown = true;
        WAREHOUSE_TERMINALS.clear();
        AE2Colonies.LOGGER.info("Cleared WarehouseMEBridge terminal caches");
    }

    public static void resetForNewServer() {
        WAREHOUSE_TERMINALS.clear();
        shuttingDown = false;
        AE2Colonies.LOGGER.info("WarehouseMEBridge reset for new server");
    }

    public static void onLevelUnload(@NotNull Level level) {
        // Use a snapshot approach to avoid concurrent modification
        for (Map.Entry<net.minecraft.core.GlobalPos, CopyOnWriteArrayList<ColonyTerminalBlockEntity>> entry : WAREHOUSE_TERMINALS.entrySet()) {
            entry.getValue().removeIf(t -> t.getLevel() == level || t.isRemoved());
        }
        WAREHOUSE_TERMINALS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @NotNull
    public static List<ColonyTerminalBlockEntity> getTerminalsForWarehouse(@NotNull IWareHouse warehouse) {
        if (shuttingDown) {
            return Collections.emptyList();
        }

        Level whLevel = warehouse.getColony() != null ? warehouse.getColony().getWorld() : null;
        net.minecraft.core.GlobalPos globalPos = whLevel != null 
            ? net.minecraft.core.GlobalPos.of(whLevel.dimension(), warehouse.getPosition())
            : null;
        CopyOnWriteArrayList<ColonyTerminalBlockEntity> terminals = globalPos != null ? WAREHOUSE_TERMINALS.get(globalPos) : null;
        if (terminals == null || terminals.isEmpty()) {
            return Collections.emptyList();
        }

        // Build a filtered snapshot — CopyOnWriteArrayList is safe to iterate
        List<ColonyTerminalBlockEntity> result = new ArrayList<>();
        for (ColonyTerminalBlockEntity t : terminals) {
            if (!t.isRemoved() && t.getLevel() != null && (whLevel == null || t.getLevel() == whLevel)) {
                result.add(t);
            }
        }
        // Lazy cleanup of stale entries
        terminals.removeIf(t -> t.isRemoved() || t.getLevel() == null);
        result.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return Collections.unmodifiableList(result);
    }

    @NotNull
    public static List<ColonyTerminalBlockEntity> getAllTerminals() {
        List<ColonyTerminalBlockEntity> all = new ArrayList<>();
        for (CopyOnWriteArrayList<ColonyTerminalBlockEntity> list : WAREHOUSE_TERMINALS.values()) {
            for (ColonyTerminalBlockEntity t : list) {
                if (!t.isRemoved() && t.getLevel() != null) {
                    all.add(t);
                }
            }
        }
        return all;
    }

    @Nullable
    public static ColonyTerminalBlockEntity getTerminalAt(@NotNull Level level, @NotNull BlockPos pos) {
        if (shuttingDown) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof ColonyTerminalBlockEntity terminal) {
            return terminal;
        }
        return null;
    }

    @Nullable
    public static IWareHouse getWarehouseForTerminal(@NotNull ColonyTerminalBlockEntity terminal) {
        if (shuttingDown) {
            return null;
        }
        Level level = terminal.getLevel();
        BlockPos whPos = terminal.getLinkedWarehousePos();
        if (level == null || whPos == null) {
            return null;
        }
        try {
            IColony colony = IColonyManager.getInstance().getIColony(level, terminal.getBlockPos());
            if (colony == null) {
                colony = IColonyManager.getInstance().getClosestIColony(level, terminal.getBlockPos());
            }
            if (colony == null) {
                return null;
            }
            IRegisteredStructureManager sm = colony.getServerBuildingManager();
            if (sm == null) {
                return null;
            }
            IBuilding building = sm.getBuilding(whPos);
            if (building instanceof IWareHouse wh) {
                return wh;
            }
        } catch (Exception e) {
            AE2Colonies.LOGGER.debug("Error looking up warehouse for terminal at {}: {}", terminal.getBlockPos(), e.getMessage());
        }
        return null;
    }
}