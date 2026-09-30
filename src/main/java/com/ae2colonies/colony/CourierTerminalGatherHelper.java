package com.ae2colonies.colony;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.domum.DomumOrnamentumHelper;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CourierTerminalGatherHelper {

    // Track how long each courier has been waiting at a terminal for crafting
    // Key: worker entity ID, Value: remaining wait ticks
    private static final Map<Integer, Integer> CRAFTING_WAIT_TICKS = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_CRAFTED_PROGRESS = new ConcurrentHashMap<>();
    private static final int MAX_CRAFTING_WAIT = 2400; // 2 minutes at 20 tps

    /**
     * Attempts to gather items from a ColonyTerminalBlockEntity into the courier's inventory,
     * including waiting for active AE2 autocrafting jobs or synthesizing Domum Ornamentum blocks.
     *
     * @param entity the block entity being accessed
     * @param is the requested item stack
     * @param worker the citizen courier
     * @return Boolean.TRUE if gathered or actively waiting for craft, Boolean.FALSE if terminal could not supply the item, or null if entity is not a ColonyTerminalBlockEntity
     */
    @Nullable
    public static Boolean tryGatherFromTerminal(
            @Nullable BlockEntity entity,
            ItemStack is,
            @Nullable AbstractEntityCitizen worker
    ) {
        if (is.isEmpty() || worker == null) {
            return null;
        }

        if (entity instanceof ColonyTerminalBlockEntity terminal) {
            if (!terminal.isTerminalOnline() || !terminal.isAllowWithdraw()) {
                clearWaitTicks(worker.getId());
                return Boolean.FALSE;
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
                    AE2Colonies.LOGGER.debug("Courier {} extracted {} from AE2 terminal", worker.getName().getString(), extracted);
                    return Boolean.TRUE;
                }
            }

            // 2. On-demand Domum Ornamentum Synthesis
            if (DomumOrnamentumHelper.isDOBlock(is) && terminal.canSynthesizeDOBlock(is, is.getCount())) {
                terminal.synthesizeDOBlock(is, is.getCount());
                ItemStack doStack = is.copyWithCount(is.getCount());
                ItemStack remainder = ItemHandlerHelper.insertItem(worker.getInventoryCitizen(), doStack, false);
                if (!remainder.isEmpty()) {
                    AE2IntegrationHelper.insertItem(terminal.getGrid(), remainder, terminal.getActionSource());
                }
                if (remainder.getCount() < doStack.getCount()) {
                    worker.swing(InteractionHand.MAIN_HAND);
                    clearWaitTicks(worker.getId());
                    AE2Colonies.LOGGER.debug("Courier {} received synthesized DO block {} from terminal", worker.getName().getString(), doStack);
                    return Boolean.TRUE;
                }
            }

            // 3. AE2 Autocrafting Wait Logic
            if (terminal.isAllowAutocraft() && terminal.isCrafting(is)) {
                int entityId = worker.getId();
                long currentProgress = terminal.getCraftedProgress(is);
                Long lastProgress = LAST_CRAFTED_PROGRESS.get(entityId);

                // If this is a newly tracked wait or progress was made (items finished crafting), reset the wait budget!
                if (lastProgress == null || currentProgress > lastProgress) {
                    LAST_CRAFTED_PROGRESS.put(entityId, currentProgress);
                    CRAFTING_WAIT_TICKS.put(entityId, MAX_CRAFTING_WAIT);
                }

                int remaining = CRAFTING_WAIT_TICKS.computeIfAbsent(entityId, k -> MAX_CRAFTING_WAIT);

                if (remaining > 0) {
                    // Still within wait budget — tell MineColonies "we got it / waiting"
                    // to prevent request cancellation while the Crafting CPU works
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

            // Item not available in AE2 and not currently crafting
            clearWaitTicks(worker.getId());
            return Boolean.FALSE;
        }

        return null;
    }

    public static void clearWaitTicks(int entityId) {
        CRAFTING_WAIT_TICKS.remove(entityId);
        LAST_CRAFTED_PROGRESS.remove(entityId);
    }
}
