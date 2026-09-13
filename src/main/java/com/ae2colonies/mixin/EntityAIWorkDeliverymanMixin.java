package com.ae2colonies.mixin;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.entity.ai.workers.AbstractAISkeleton;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = EntityAIWorkDeliveryman.class, remap = false)
public abstract class EntityAIWorkDeliverymanMixin extends AbstractAISkeleton<JobDeliveryman> {

    // Track how long each courier has been waiting at a terminal for crafting
    // Key: worker entity ID, Value: remaining wait ticks
    private static final Map<Integer, Integer> CRAFTING_WAIT_TICKS = new ConcurrentHashMap<>();
    private static final int MAX_CRAFTING_WAIT = 600; // 30 seconds at 20 tps

    protected EntityAIWorkDeliverymanMixin(JobDeliveryman job) {
        super(job);
    }

    @Inject(
            method = "gatherIfInTileEntity",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onGatherIfInTileEntity(
            BlockEntity entity,
            ItemStack is,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (is.isEmpty()) {
            return;
        }

        if (entity instanceof ColonyTerminalBlockEntity terminal) {
            if (terminal.isTerminalOnline() && terminal.isAllowWithdraw()) {
                // First, always try to extract the item from AE2 storage
                // (it might already be available or crafting might have completed)
                ItemStack extracted = AE2IntegrationHelper.extractItem(
                        terminal.getGrid(),
                        is,
                        terminal.getActionSource()
                );
                if (!extracted.isEmpty()) {
                    ItemStack remainder = ItemHandlerHelper.insertItem(this.worker.getInventoryCitizen(), extracted, false);
                    if (!remainder.isEmpty()) {
                        AE2IntegrationHelper.insertItem(terminal.getGrid(), remainder, terminal.getActionSource());
                    }
                    if (remainder.getCount() < extracted.getCount()) {
                        // Successfully transferred items to courier inventory
                        this.worker.swing(InteractionHand.MAIN_HAND);
                        // Clear the wait counter since we got the item
                        CRAFTING_WAIT_TICKS.remove(this.worker.getId());
                        cir.setReturnValue(true);
                        return;
                    }
                }

                // Item not available in AE2 storage. Check if it's being crafted.
                if (terminal.isAllowAutocraft() && terminal.isCrafting(is)) {
                    int entityId = this.worker.getId();
                    int remaining = CRAFTING_WAIT_TICKS.computeIfAbsent(entityId, k -> MAX_CRAFTING_WAIT);

                    if (remaining > 0) {
                        // Still within wait budget — tell MineColonies "we got it"
                        // to prevent request cancellation, but don't actually transfer items
                        CRAFTING_WAIT_TICKS.put(entityId, remaining - 1);
                        this.worker.swing(InteractionHand.MAIN_HAND);
                        // Return true to keep the courier waiting at the terminal
                        cir.setReturnValue(true);
                    } else {
                        // Timed out — let MineColonies handle the failure
                        AE2Colonies.LOGGER.debug("Courier {} timed out waiting for AE2 crafting of {}",
                                this.worker.getName().getString(), is);
                        CRAFTING_WAIT_TICKS.remove(entityId);
                        cir.setReturnValue(false);
                    }
                    return;
                }

                // Item not available and not being crafted — clear wait state and let MC handle
                CRAFTING_WAIT_TICKS.remove(this.worker.getId());
            }
        }
    }
}
