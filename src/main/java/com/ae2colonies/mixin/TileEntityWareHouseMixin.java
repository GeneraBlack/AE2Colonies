package com.ae2colonies.mixin;

import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.colony.WarehouseMEBridge;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.core.tileentities.TileEntityWareHouse;
import net.minecraft.core.BlockPos;
import com.minecolonies.api.util.Tuple;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value = TileEntityWareHouse.class, remap = false)
public abstract class TileEntityWareHouseMixin {

    private IWareHouse getWarehouse() {
        try {
            Method method = this.getClass().getMethod("getBuilding");
            Object building = method.invoke(this);
            if (building instanceof IWareHouse warehouse) {
                return warehouse;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Inject(
            method = "hasMatchingItemStackInWarehouse(Ljava/util/function/Predicate;I)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void onHasMatchingItemStackPredicate(
            Predicate<ItemStack> itemStackSelectionPredicate,
            int count,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue()) {
            IWareHouse warehouse = getWarehouse();
            if (warehouse != null) {
                for (ColonyTerminalBlockEntity terminal : WarehouseMEBridge.getTerminalsForWarehouse(warehouse)) {
                    if (terminal.isTerminalOnline() && terminal.isAllowWithdraw()) {
                        int available = AE2IntegrationHelper.getAvailableCount(terminal.getGrid(), itemStackSelectionPredicate);
                        if (available >= count) {
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @Inject(
            method = "hasMatchingItemStackInWarehouse(Lnet/minecraft/world/item/ItemStack;IZZI)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void onHasMatchingItemStackStack(
            ItemStack itemStack,
            int count,
            boolean ignoreNBT,
            boolean ignoreDamage,
            int leftOver,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue()) {
            IWareHouse warehouse = getWarehouse();
            if (warehouse != null) {
                for (ColonyTerminalBlockEntity terminal : WarehouseMEBridge.getTerminalsForWarehouse(warehouse)) {
                    if (terminal.isTerminalOnline() && terminal.isAllowWithdraw()) {
                        int available = AE2IntegrationHelper.getAvailableCount(terminal.getGrid(), itemStack, !ignoreNBT);
                        if (available >= count + leftOver) {
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @Inject(
            method = "getMatchingItemStacksInWarehouse(Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void onGetMatchingItemStacks(
            Predicate<ItemStack> itemStackSelectionPredicate,
            CallbackInfoReturnable<List<Tuple<ItemStack, BlockPos>>> cir
    ) {
        IWareHouse warehouse = getWarehouse();
        if (warehouse != null) {
            List<Tuple<ItemStack, BlockPos>> list = cir.getReturnValue();
            for (ColonyTerminalBlockEntity terminal : WarehouseMEBridge.getTerminalsForWarehouse(warehouse)) {
                if (!terminal.isTerminalOnline()) {
                    continue;
                }

                if (terminal.isAllowWithdraw()) {
                    List<ItemStack> ae2Matches = AE2IntegrationHelper.getMatchingItemStacks(terminal.getGrid(), itemStackSelectionPredicate);
                    for (ItemStack stack : ae2Matches) {
                        list.add(new Tuple<>(stack, terminal.getBlockPos()));
                    }
                }

                if (terminal.isAllowAutocraft()) {
                    com.minecolonies.api.colony.requestsystem.request.IRequest<? extends com.minecolonies.api.colony.requestsystem.requestable.IDeliverable> activeReq =
                            com.ae2colonies.colony.WarehouseRequestContext.getCurrentRequest();

                    if (activeReq != null) {
                        com.minecolonies.api.colony.requestsystem.requestable.IDeliverable deliverable = activeReq.getRequest();
                        int count = deliverable.getCount();

                        if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable concrete) {
                            // Concrete deliverable: try each specific requested item
                            for (ItemStack requestedStack : concrete.getRequestedItems()) {
                                if (requestedStack.isEmpty() || !itemStackSelectionPredicate.test(requestedStack)) {
                                    continue;
                                }

                                if (com.ae2colonies.domum.DomumOrnamentumHelper.isDOBlock(requestedStack)) {
                                    if (terminal.canSynthesizeDOBlock(requestedStack, count)) {
                                        terminal.synthesizeDOBlock(requestedStack, count);
                                        ItemStack synthesized = requestedStack.copyWithCount(count);
                                        list.add(new Tuple<>(synthesized, terminal.getBlockPos()));
                                        break;
                                    }
                                } else if (AE2IntegrationHelper.isCraftable(terminal.getGrid(), requestedStack)) {
                                    String requesterName = "Colony Request";
                                    terminal.requestCrafting(requestedStack, count, requesterName);
                                    ItemStack craftStack = requestedStack.copyWithCount(count);
                                    list.add(new Tuple<>(craftStack, terminal.getBlockPos()));
                                    break;
                                }
                            }
                        } else if (terminal.getGrid() != null) {
                            // Non-concrete deliverable (Tool, Food, etc.): iterate AE2 craftables
                            // and match using the request predicate (which calls deliverable.matches())
                            for (appeng.api.stacks.AEKey key : terminal.getGrid().getCraftingService().getCraftables(k -> k instanceof appeng.api.stacks.AEItemKey)) {
                                if (key instanceof appeng.api.stacks.AEItemKey itemKey) {
                                    ItemStack candidate = itemKey.toStack(count);
                                    if (itemStackSelectionPredicate.test(candidate)) {
                                        terminal.requestCrafting(candidate, count, "Colony Request");
                                        list.add(new Tuple<>(candidate, terminal.getBlockPos()));
                                        break;
                                    }
                                }
                            }
                        }
                    } else if (terminal.getGrid() != null) {
                        // No active request context — fallback: iterate craftables and match
                        for (appeng.api.stacks.AEKey key : terminal.getGrid().getCraftingService().getCraftables(k -> k instanceof appeng.api.stacks.AEItemKey)) {
                            if (key instanceof appeng.api.stacks.AEItemKey itemKey) {
                                ItemStack candidate = itemKey.toStack(64);
                                if (itemStackSelectionPredicate.test(candidate)) {
                                    terminal.requestCrafting(candidate, 64, "Colony Request");
                                    list.add(new Tuple<>(candidate, terminal.getBlockPos()));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(
            method = "dumpInventoryIntoWareHouse(Lcom/minecolonies/api/inventory/InventoryCitizen;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onDumpInventoryIntoWarehouse(
            @NotNull InventoryCitizen inventoryCitizen,
            CallbackInfo ci
    ) {
        IWareHouse warehouse = getWarehouse();
        if (warehouse != null) {
            for (ColonyTerminalBlockEntity terminal : WarehouseMEBridge.getTerminalsForWarehouse(warehouse)) {
                if (terminal.isTerminalOnline() && terminal.isAllowDeposit()) {
                    for (int i = 0; i < inventoryCitizen.getSlots(); i++) {
                        ItemStack stack = inventoryCitizen.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            ItemStack remaining = AE2IntegrationHelper.insertItem(
                                    terminal.getGrid(),
                                    stack,
                                    terminal.getActionSource()
                            );
                            inventoryCitizen.setStackInSlot(i, remaining);
                        }
                    }
                    if (inventoryCitizen.isEmpty()) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }
    }
}
