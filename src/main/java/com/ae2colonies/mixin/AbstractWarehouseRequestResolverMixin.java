package com.ae2colonies.mixin;

import com.ae2colonies.colony.WarehouseRequestContext;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractWarehouseRequestResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = AbstractWarehouseRequestResolver.class, remap = false)
public abstract class AbstractWarehouseRequestResolverMixin {

    @Inject(
            method = "attemptResolveRequest",
            at = @At("HEAD")
    )
    private void onAttemptResolveRequestHead(
            IRequestManager manager,
            IRequest<? extends IDeliverable> request,
            CallbackInfoReturnable<List<IToken<?>>> cir
    ) {
        WarehouseRequestContext.setCurrentRequest(request);
    }

    @Inject(
            method = "attemptResolveRequest",
            at = @At("RETURN")
    )
    private void onAttemptResolveRequestReturn(
            IRequestManager manager,
            IRequest<? extends IDeliverable> request,
            CallbackInfoReturnable<List<IToken<?>>> cir
    ) {
        WarehouseRequestContext.clear();
    }

    @Inject(
            method = "getFollowupRequestForCompletion",
            at = @At("HEAD")
    )
    private void onGetFollowupRequestHead(
            IRequestManager manager,
            IRequest<? extends IDeliverable> completedRequest,
            CallbackInfoReturnable<List<IRequest<?>>> cir
    ) {
        WarehouseRequestContext.setCurrentRequest(completedRequest);
    }

    @Inject(
            method = "getFollowupRequestForCompletion",
            at = @At("RETURN")
    )
    private void onGetFollowupRequestReturn(
            IRequestManager manager,
            IRequest<? extends IDeliverable> completedRequest,
            CallbackInfoReturnable<List<IRequest<?>>> cir
    ) {
        WarehouseRequestContext.clear();
    }
}
