package com.ae2colonies.mixin;

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

@Mixin(value = EntityAIWorkDeliveryman.class, remap = false)
public abstract class EntityAIWorkDeliverymanMixin extends AbstractAISkeleton<JobDeliveryman> {

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
                        this.worker.swing(InteractionHand.MAIN_HAND);
                        cir.setReturnValue(true);
                        return;
                    }
                }

                // If item is currently being crafted in AE2, wait at the terminal
                if (terminal.isAllowAutocraft() && terminal.isCrafting(is)) {
                    this.worker.swing(InteractionHand.MAIN_HAND);
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
