package com.ae2colonies.mixin;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.colony.requestsystem.management.handlers.RequestHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RequestHandler.class, remap = false)
public abstract class RequestHandlerMixin {

    @Shadow
    @Final
    private IStandardRequestManager manager;

    @Shadow
    public abstract void onRequestCancelledDirectly(final IToken<?> token);

    /**
     * Prevents NullPointerException when cancelling requests whose child tokens
     * are already completed, cleaned up, or missing from the RequestManager.
     */
    @Inject(
            method = "onRequestCancelledDirectly(Lcom/minecolonies/api/colony/requestsystem/token/IToken;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onCancelDirectlyHead(IToken<?> token, CallbackInfo ci) {
        if (token == null) {
            ci.cancel();
            return;
        }
        IRequest<?> req = this.manager.getRequestForToken(token);
        if (req == null) {
            ci.cancel();
        }
    }

    /**
     * Prevents NPEs in onChildRequestCancelled if the request or its parent no longer exists.
     */
    @Inject(
            method = "onChildRequestCancelled(Lcom/minecolonies/api/colony/requestsystem/token/IToken;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onChildRequestCancelledHead(IToken<?> token, CallbackInfo ci) {
        if (token == null) {
            ci.cancel();
            return;
        }
        IRequest<?> req = this.manager.getRequestForToken(token);
        if (req == null) {
            ci.cancel();
            return;
        }
        if (!req.hasParent() || req.getParent() == null) {
            this.onRequestCancelledDirectly(token);
            ci.cancel();
            return;
        }
        IRequest<?> parent = this.manager.getRequestForToken(req.getParent());
        if (parent == null) {
            this.onRequestCancelledDirectly(token);
            ci.cancel();
        }
    }

    /**
     * Prevents IllegalArgumentException when trying to remove a request from a parent that
     * is already cleaned up or missing from the identities store.
     */
    @Inject(
            method = "processDirectCancellationOf(Lcom/minecolonies/api/colony/requestsystem/request/IRequest;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onProcessDirectCancellationOfHead(IRequest<?> request, CallbackInfo ci) {
        if (request == null) {
            ci.cancel();
            return;
        }
        if (request.hasParent() && request.getParent() != null) {
            if (this.manager.getRequestForToken(request.getParent()) == null) {
                request.setParent(null);
            }
        }
    }

    /**
     * Prevents IllegalArgumentException if cleanRequestData is called for an already cleaned token.
     */
    @Inject(
            method = "cleanRequestData(Lcom/minecolonies/api/colony/requestsystem/token/IToken;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onCleanRequestDataHead(IToken<?> token, CallbackInfo ci) {
        if (token == null || !this.manager.getRequestIdentitiesDataStore().getIdentities().containsKey(token)) {
            ci.cancel();
        }
    }

    /**
     * Prevents IllegalArgumentException if onRequestCancelled is called for an unmapped token.
     */
    @Inject(
            method = "onRequestCancelled(Lcom/minecolonies/api/colony/requestsystem/token/IToken;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRequestCancelledHead(IToken<?> token, CallbackInfo ci) {
        if (token == null || !this.manager.getRequestIdentitiesDataStore().getIdentities().containsKey(token)) {
            ci.cancel();
        }
    }

    /**
     * Prevents IllegalArgumentException if onRequestOverruled is called for an unmapped token.
     */
    @Inject(
            method = "onRequestOverruled(Lcom/minecolonies/api/colony/requestsystem/token/IToken;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRequestOverruledHead(IToken<?> token, CallbackInfo ci) {
        if (token == null || !this.manager.getRequestIdentitiesDataStore().getIdentities().containsKey(token)) {
            ci.cancel();
        }
    }
}
