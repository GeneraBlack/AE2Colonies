package com.ae2colonies.colony;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.domum.DomumOrnamentumHelper;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractRequestResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

public class AE2WarehouseResolver extends AbstractRequestResolver<IDeliverable> {

    private final ColonyTerminalBlockEntity terminal;

    public AE2WarehouseResolver(
            @NotNull final ILocation location,
            @NotNull final IToken<?> token,
            @NotNull final ColonyTerminalBlockEntity terminal
    ) {
        super(location, token);
        this.terminal = terminal;
    }

    @Override
    public TypeToken<? extends IDeliverable> getRequestType() {
        return TypeToken.of(IDeliverable.class);
    }

    @Override
    public boolean canResolveRequest(
            @NotNull final IRequestManager manager,
            @NotNull final IRequest<? extends IDeliverable> request
    ) {
        if (!terminal.isTerminalOnline() || !terminal.isAllowAutocraft()) {
            return false;
        }

        IGrid grid = terminal.getGrid();
        if (grid == null) {
            return false;
        }

        IDeliverable deliverable = request.getRequest();
        if (deliverable instanceof IConcreteDeliverable concrete) {
            for (ItemStack stack : concrete.getRequestedItems()) {
                if (AE2IntegrationHelper.isCraftable(grid, stack)) {
                    return true;
                }
                if (DomumOrnamentumHelper.isDOBlock(stack) && canSynthesizeDOBlock(grid, stack, deliverable.getCount())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canSynthesizeDOBlock(@NotNull IGrid grid, @NotNull ItemStack stack, int count) {
        if (grid.getActiveMachines(com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity.class).isEmpty()) {
            return false;
        }

        com.ae2colonies.domum.DomumOrnamentumHelper.DOMaterialCost cost =
                com.ae2colonies.domum.DomumOrnamentumHelper.getMaterialCost(terminal.getLevel(), stack);
        if (cost == null) {
            return false;
        }

        int batches = (int) Math.ceil((double) count / cost.getYield());
        for (ItemStack ingredient : cost.getIngredients()) {
            int needed = batches * ingredient.getCount();
            int available = AE2IntegrationHelper.getAvailableCount(grid, ingredient, false);
            if (available < needed) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    public List<IToken<?>> attemptResolveRequest(
            @NotNull final IRequestManager manager,
            @NotNull final IRequest<? extends IDeliverable> request
    ) {
        if (manager.getColony().getWorld().isClientSide() || !terminal.isTerminalOnline() || !terminal.isAllowAutocraft()) {
            return Lists.newArrayList();
        }

        IGrid grid = terminal.getGrid();
        if (grid == null) {
            return Lists.newArrayList();
        }

        IDeliverable deliverable = request.getRequest();
        if (!(deliverable instanceof IConcreteDeliverable concrete)) {
            return Lists.newArrayList();
        }

        for (ItemStack stack : concrete.getRequestedItems()) {
            if (AE2IntegrationHelper.isCraftable(grid, stack)) {
                int countNeeded = deliverable.getCount();
                try {
                    Future<ICraftingPlan> future = AE2IntegrationHelper.requestCraftingCalculation(
                            terminal.getLevel(),
                            grid,
                            terminal,
                            stack,
                            countNeeded
                    );
                    if (future != null) {
                        ICraftingPlan plan = future.get();
                        if (plan != null && !plan.simulation()) {
                            ICraftingSubmitResult result = AE2IntegrationHelper.submitCraftingJob(
                                    grid,
                                    plan,
                                    terminal,
                                    terminal.getActionSource()
                            );
                            if (result != null && result.successful() && result.link() != null) {
                                String reqName = request.getRequester().getRequesterDisplayName(manager, request).getString();
                                terminal.getCraftingTracker().trackJob(
                                        result.link(),
                                        request.getId().toString(),
                                        stack,
                                        countNeeded,
                                        reqName
                                );
                                return Lists.newArrayList();
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            } else if (com.ae2colonies.domum.DomumOrnamentumHelper.isDOBlock(stack)
                    && canSynthesizeDOBlock(grid, stack, deliverable.getCount())) {
                com.ae2colonies.domum.DomumOrnamentumHelper.DOMaterialCost cost =
                        com.ae2colonies.domum.DomumOrnamentumHelper.getMaterialCost(terminal.getLevel(), stack);
                if (cost != null) {
                    int countNeeded = deliverable.getCount();
                    int batches = (int) Math.ceil((double) countNeeded / cost.getYield());

                    List<ItemStack> extractedIngredients = Lists.newArrayList();
                    boolean success = true;

                    for (ItemStack ingredient : cost.getIngredients()) {
                        int needed = batches * ingredient.getCount();
                        ItemStack toExtract = ingredient.copyWithCount(needed);
                        ItemStack extracted = AE2IntegrationHelper.extractItem(grid, toExtract, terminal.getActionSource());
                        if (extracted.getCount() < needed) {
                            success = false;
                            if (!extracted.isEmpty()) {
                                extractedIngredients.add(extracted);
                            }
                            break;
                        }
                        extractedIngredients.add(extracted);
                    }

                    if (!success) {
                        for (ItemStack rollback : extractedIngredients) {
                            AE2IntegrationHelper.insertItem(grid, rollback, terminal.getActionSource());
                        }
                        return Lists.newArrayList();
                    }

                    grid.getEnergyService().extractAEPower(
                            20.0 * countNeeded,
                            appeng.api.config.Actionable.MODULATE,
                            appeng.api.config.PowerMultiplier.CONFIG
                    );

                    ItemStack synthesized = com.ae2colonies.domum.DomumOrnamentumHelper.synthesizeDOBlock(stack, countNeeded);
                    AE2IntegrationHelper.insertItem(grid, synthesized, terminal.getActionSource());

                    resolveRequest(manager, request);
                    return Lists.newArrayList();
                }
            }
        }

        return Lists.newArrayList();
    }

    @Override
    public void resolveRequest(
            @NotNull final IRequestManager manager,
            @NotNull final IRequest<? extends IDeliverable> request
    ) {
        manager.updateRequestState(request.getId(), RequestState.RESOLVED);
    }

    @Nullable
    @Override
    public List<IRequest<?>> getFollowupRequestForCompletion(
            @NotNull final IRequestManager manager,
            @NotNull final IRequest<? extends IDeliverable> completedRequest
    ) {
        IDeliverable deliverable = completedRequest.getRequest();
        ItemStack stack = null;
        if (deliverable instanceof IConcreteDeliverable concrete) {
            for (ItemStack s : concrete.getRequestedItems()) {
                stack = s.copy();
                stack.setCount(deliverable.getCount());
                break;
            }
        }
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        completedRequest.addDelivery(stack);

        final ILocation terminalLocation = manager.getFactoryController().getNewInstance(
                TypeConstants.ILOCATION,
                terminal.getBlockPos(),
                terminal.getLevel().dimension()
        );

        final Delivery delivery = new Delivery(
                terminalLocation,
                completedRequest.getRequester().getLocation(),
                stack,
                10
        );

        final IToken<?> requestToken = manager.createRequest(this, delivery);
        return Lists.newArrayList(manager.getRequestForToken(requestToken));
    }

    @Override
    public void onAssignedRequestBeingCancelled(@NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    }

    @Override
    public void onAssignedRequestCancelled(@NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    }

    @Override
    public void onRequestedRequestComplete(@NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    }

    @Override
    public void onRequestedRequestCancelled(@NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    }

    @NotNull
    @Override
    public MutableComponent getRequesterDisplayName(@NotNull IRequestManager manager, @NotNull IRequest<?> request) {
        return Component.translatable("block.ae2colonies.colony_terminal");
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public boolean isValid() {
        return terminal.isTerminalOnline();
    }
}
