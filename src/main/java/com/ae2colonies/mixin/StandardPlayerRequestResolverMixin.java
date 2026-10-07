package com.ae2colonies.mixin;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.colony.ColonyNotificationHelper;
import com.ae2colonies.colony.WarehouseMEBridge;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.resolvers.StandardPlayerRequestResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(value = StandardPlayerRequestResolver.class, remap = false)
public abstract class StandardPlayerRequestResolverMixin {

    @Shadow
    @Final
    private Set<IToken<?>> assignedRequests;

    @Inject(
            method = "resolveRequest",
            at = @At("HEAD")
    )
    private void onResolveRequestHead(
            IRequestManager manager,
            IRequest<?> request,
            CallbackInfo ci
    ) {
        handleFailedRequest(manager, request);
    }

    @Inject(
            method = "onColonyUpdate",
            at = @At("HEAD")
    )
    private void onColonyUpdateHead(
            IRequestManager manager,
            Predicate<IRequest<?>> shouldTriggerReassign,
            CallbackInfo ci
    ) {
        if (manager == null || manager.getColony() == null || manager.getColony().getWorld().isClientSide()) {
            return;
        }
        for (IToken<?> token : this.assignedRequests) {
            IRequest<?> req = manager.getRequestForToken(token);
            if (req != null) {
                handleFailedRequest(manager, req);
            }
        }
    }

    private static void handleFailedRequest(IRequestManager manager, IRequest<?> request) {
        if (manager == null || request == null) {
            return;
        }

        IColony colony = manager.getColony();
        if (colony == null || colony.getWorld().isClientSide()) {
            return;
        }

        List<ColonyTerminalBlockEntity> terminals = WarehouseMEBridge.getTerminalsForColony(colony);
        if (terminals.isEmpty()) {
            return;
        }

        ColonyTerminalBlockEntity candidateTerminal = null;
        for (ColonyTerminalBlockEntity terminal : terminals) {
            if (terminal.isTerminalOnline() && terminal.isAllowAutocraft()) {
                candidateTerminal = terminal;
                break;
            }
        }

        if (candidateTerminal == null) {
            return;
        }

        int count = 1;
        if (request.getRequest() instanceof IDeliverable deliverable) {
            count = deliverable.getCount();
        }

        List<ItemStack> candidates = new ArrayList<>();
        if (request.getRequest() instanceof IConcreteDeliverable concrete) {
            candidates.addAll(concrete.getRequestedItems());
        } else {
            candidates.addAll(request.getDisplayStacks());
        }

        for (ItemStack candidate : candidates) {
            if (!candidate.isEmpty()) {
                AE2Colonies.LOGGER.info("[PlayerRequest] Request {} for {} x{} assigned to player — checking AE2 failure reasons",
                        request.getId(), candidate, count);
                ColonyNotificationHelper.checkAndNotifyCraftingFailure(candidateTerminal, candidate, count);
                break;
            }
        }
    }
}
