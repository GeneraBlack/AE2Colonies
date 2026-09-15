package com.ae2colonies.mixin;

import com.ae2colonies.colony.CourierTerminalGatherHelper;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.entity.ai.workers.AbstractAISkeleton;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        Boolean result = CourierTerminalGatherHelper.tryGatherFromTerminal(entity, is, this.worker);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
