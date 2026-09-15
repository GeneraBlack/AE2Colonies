package com.ae2colonies.mixin;

import com.ae2colonies.colony.CourierTerminalGatherHelper;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin targeting Pathfinding Edition for MineColonies (colonypathingedition).
 * Pathfinding Edition replaces EntityAIWorkDeliveryman with NewEntityAIWorkDeliveryman.
 * This mixin intercepts gatherIfInTileEntity to enable couriers to withdraw from the
 * ME Colony Terminal and wait for active AE2 autocrafting jobs.
 */
@Mixin(targets = "com.arxyt.colonypathingedition.core.ai.worker.NewEntityAIWorkDeliveryman", remap = false)
public abstract class NewEntityAIWorkDeliverymanMixin {

    private static final java.lang.reflect.Field WORKER_FIELD;
    private static final java.lang.reflect.Field JOB_FIELD;

    static {
        java.lang.reflect.Field fWorker = null;
        java.lang.reflect.Field fJob = null;
        try {
            fWorker = com.minecolonies.core.entity.ai.workers.AbstractAISkeleton.class.getDeclaredField("worker");
            fWorker.setAccessible(true);
        } catch (Throwable ignored) {
        }
        try {
            fJob = com.minecolonies.core.entity.ai.workers.AbstractAISkeleton.class.getDeclaredField("job");
            fJob.setAccessible(true);
        } catch (Throwable ignored) {
        }
        WORKER_FIELD = fWorker;
        JOB_FIELD = fJob;
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
        AbstractEntityCitizen citizen = null;
        if (WORKER_FIELD != null) {
            try {
                citizen = (AbstractEntityCitizen) WORKER_FIELD.get(this);
            } catch (Throwable ignored) {
            }
        }
        if (citizen == null && JOB_FIELD != null) {
            try {
                Object jobObj = JOB_FIELD.get(this);
                if (jobObj instanceof com.minecolonies.api.colony.jobs.IJob<?> job && job.getCitizen() != null) {
                    citizen = job.getCitizen().getEntity().orElse(null);
                }
            } catch (Throwable ignored) {
            }
        }

        Boolean result = CourierTerminalGatherHelper.tryGatherFromTerminal(entity, is, citizen);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
