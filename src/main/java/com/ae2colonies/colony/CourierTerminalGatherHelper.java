package com.ae2colonies.colony;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.domum.DomumOrnamentumHelper;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CourierTerminalGatherHelper {

    // Track how long each courier has been waiting at a terminal for crafting
    // Key: worker entity ID, Value: remaining wait ticks
    private static final Map<Integer, Integer> CRAFTING_WAIT_TICKS = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_CRAFTED_PROGRESS = new ConcurrentHashMap<>();
    private static final int MAX_CRAFTING_WAIT = 2400; // 2 minutes at 20 tps

    /**
     * Attempts to gather items from AE2 terminals linked to the warehouse that
     * the courier is visiting. Works both when the courier visits a terminal
     * directly AND when it visits a warehouse rack block.
     *
     * @param entity the block entity being accessed (warehouse rack or terminal)
     * @param is the requested item stack
     * @param worker the citizen courier
     * @return Boolean.TRUE if gathered or actively waiting for craft,
     *         Boolean.FALSE if terminal could not supply the item,
     *         or null if no linked terminals exist for this location
     */
    @Nullable
    public static Boolean tryGatherFromTerminal(
            @Nullable BlockEntity entity,
            ItemStack is,
            @Nullable AbstractEntityCitizen worker
    ) {
        if (is.isEmpty() || worker == null || entity == null) {
            return null;
        }

        // Case 1: Direct terminal access
        if (entity instanceof ColonyTerminalBlockEntity terminal) {
            return gatherFromTerminal(terminal, is, worker);
        }

        // Case 2: Courier is at a warehouse rack — find linked terminals
        Level level = entity.getLevel();
        if (level == null || level.isClientSide()) {
            return null;
        }

        BlockPos rackPos = entity.getBlockPos();
        List<ColonyTerminalBlockEntity> terminals = findTerminalsForRackPosition(level, rackPos);
        if (terminals.isEmpty()) {
            return null; // No terminals linked — let MineColonies handle normally
        }

        // Try each terminal (sorted by priority)
        for (ColonyTerminalBlockEntity terminal : terminals) {
            Boolean result = gatherFromTerminal(terminal, is, worker);
            if (result != null && result) {
                return Boolean.TRUE;
            }
        }

        // No terminal could provide the item
        clearWaitTicks(worker.getId());
        return null; // Return null so MineColonies can still try the rack normally
    }

    /**
     * Find terminals registered to the warehouse that contains the given rack position.
     */
    private static List<ColonyTerminalBlockEntity> findTerminalsForRackPosition(Level level, BlockPos rackPos) {
        try {
            IColony colony = IColonyManager.getInstance().getIColony(level, rackPos);
            if (colony == null) {
                colony = IColonyManager.getInstance().getClosestIColony(level, rackPos);
            }
            if (colony == null) {
                return List.of();
            }

            IRegisteredStructureManager sm = colony.getServerBuildingManager();
            if (sm == null) {
                return List.of();
            }

            // Find which warehouse contains this rack position
            IWareHouse warehouse = null;
            List<IWareHouse> warehouses = sm.getWareHouses();
            if (warehouses != null) {
                for (IWareHouse wh : warehouses) {
                    if (wh.isInBuilding(rackPos)) {
                        warehouse = wh;
                        break;
                    }
                }
                // Fallback: closest warehouse within 32 blocks
                if (warehouse == null) {
                    double closestDistSq = Double.MAX_VALUE;
                    for (IWareHouse wh : warehouses) {
                        double distSq = wh.getPosition().distSqr(rackPos);
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            warehouse = wh;
                        }
                    }
                    if (closestDistSq > 1024.0) { // > 32 blocks away
                        warehouse = null;
                    }
                }
            }

            if (warehouse != null) {
                return WarehouseMEBridge.getTerminalsForWarehouse(warehouse);
            }
        } catch (Exception e) {
            AE2Colonies.LOGGER.debug("Failed to find terminals for rack at {}: {}", rackPos, e.getMessage());
        }
        return List.of();
    }

    /**
     * Gather items from a specific terminal's AE2 network.
     */
    @Nullable
    private static Boolean gatherFromTerminal(
            ColonyTerminalBlockEntity terminal,
            ItemStack is,
            AbstractEntityCitizen worker
    ) {
        if (!terminal.isTerminalOnline() || !terminal.isAllowWithdraw()) {
            return null; // Skip this terminal, try next
        }

        // 1. Direct AE2 Storage Extraction
        ItemStack extracted = AE2IntegrationHelper.extractItem(
                terminal.getGrid(),
                is,
                terminal.getActionSource()
        );

        if (!extracted.isEmpty()) {
            ItemStack remainder = ItemHandlerHelper.insertItem(worker.getInventoryCitizen(), extracted, false);
            if (!remainder.isEmpty()) {
                AE2IntegrationHelper.insertItem(terminal.getGrid(), remainder, terminal.getActionSource());
            }
            if (remainder.getCount() < extracted.getCount()) {
                worker.swing(InteractionHand.MAIN_HAND);
                clearWaitTicks(worker.getId());
                AE2Colonies.LOGGER.debug("Courier {} extracted {} from AE2 terminal at {}",
                        worker.getName().getString(), extracted, terminal.getBlockPos());
                return Boolean.TRUE;
            }
        }

        // 2. On-demand Domum Ornamentum Synthesis
        if (DomumOrnamentumHelper.isDOBlock(is) && terminal.canSynthesizeDOBlock(is, is.getCount())) {
            terminal.synthesizeDOBlock(is, is.getCount());
            // Extract the synthesized items from AE2 (they were inserted by synthesizeDOBlock)
            ItemStack doStack = AE2IntegrationHelper.extractItem(
                    terminal.getGrid(), is.copyWithCount(is.getCount()), terminal.getActionSource());
            if (!doStack.isEmpty()) {
                ItemStack remainder = ItemHandlerHelper.insertItem(worker.getInventoryCitizen(), doStack, false);
                if (!remainder.isEmpty()) {
                    AE2IntegrationHelper.insertItem(terminal.getGrid(), remainder, terminal.getActionSource());
                }
                if (remainder.getCount() < doStack.getCount()) {
                    worker.swing(InteractionHand.MAIN_HAND);
                    clearWaitTicks(worker.getId());
                    AE2Colonies.LOGGER.debug("Courier {} received synthesized DO block {} from terminal at {}",
                            worker.getName().getString(), doStack, terminal.getBlockPos());
                    return Boolean.TRUE;
                }
            }
        }

        // 3. AE2 Autocrafting Wait Logic
        if (terminal.isAllowAutocraft() && terminal.isCrafting(is)) {
            int entityId = worker.getId();
            long currentProgress = terminal.getCraftedProgress(is);
            Long lastProgress = LAST_CRAFTED_PROGRESS.get(entityId);

            // If this is a newly tracked wait or progress was made, reset the wait budget
            if (lastProgress == null || currentProgress > lastProgress) {
                LAST_CRAFTED_PROGRESS.put(entityId, currentProgress);
                CRAFTING_WAIT_TICKS.put(entityId, MAX_CRAFTING_WAIT);
            }

            int remaining = CRAFTING_WAIT_TICKS.computeIfAbsent(entityId, k -> MAX_CRAFTING_WAIT);

            if (remaining > 0) {
                // Still within wait budget — tell MineColonies "we got it / waiting"
                CRAFTING_WAIT_TICKS.put(entityId, remaining - 1);
                worker.swing(InteractionHand.MAIN_HAND);
                return Boolean.TRUE;
            } else {
                // Timed out waiting for crafting CPU (2 minutes without progress)
                AE2Colonies.LOGGER.warn("Courier {} timed out waiting for AE2 crafting of {}",
                        worker.getName().getString(), is);
                clearWaitTicks(entityId);
                return Boolean.FALSE;
            }
        }

        return null; // This terminal can't help — try next
    }

    public static void clearWaitTicks(int entityId) {
        CRAFTING_WAIT_TICKS.remove(entityId);
        LAST_CRAFTED_PROGRESS.remove(entityId);
    }
}
