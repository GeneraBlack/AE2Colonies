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

import java.util.List;
import java.util.function.Predicate;

@Mixin(value = TileEntityWareHouse.class, remap = false)
public abstract class TileEntityWareHouseMixin {

    /**
     * Splits an ItemStack into multiple Tuples, each with count <= maxStackSize.
     * Prevents MineColonies serialization crash (ItemStack count must be [1;99]).
     */
    private static void addSafeStacks(List<Tuple<ItemStack, BlockPos>> list, ItemStack stack, BlockPos pos) {
        int remaining = stack.getCount();
        int maxSize = Math.min(stack.getMaxStackSize(), 64);
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxSize);
            list.add(new Tuple<>(stack.copyWithCount(chunk), pos));
            remaining -= chunk;
        }
    }

    private static java.lang.reflect.Method getBuildingMethod;

    private IWareHouse getWarehouse() {
        try {
            if (getBuildingMethod == null) {
                getBuildingMethod = this.getClass().getMethod("getBuilding");
            }
            Object building = getBuildingMethod.invoke(this);
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
                int totalAvailable = 0;
                for (ColonyTerminalBlockEntity terminal : WarehouseMEBridge.getTerminalsForWarehouse(warehouse)) {
                    if (terminal.isTerminalOnline()) {
                        if (terminal.isAllowWithdraw()) {
                            totalAvailable += AE2IntegrationHelper.getAvailableCount(terminal.getGrid(), itemStackSelectionPredicate);
                        }
                        if (totalAvailable >= count) {
                            cir.setReturnValue(true);
                            return;
                        }
                        if (terminal.isAllowAutocraft() && terminal.getGrid() != null) {
                            int needed = count - totalAvailable;
                            if (needed > 0) {
                                // Check DO blocks via request context (predicate doesn't carry the stack)
                                com.minecolonies.api.colony.requestsystem.request.IRequest<? extends com.minecolonies.api.colony.requestsystem.requestable.IDeliverable> activeReq =
                                        com.ae2colonies.colony.WarehouseRequestContext.getCurrentRequest();
                                if (activeReq != null) {
                                    com.minecolonies.api.colony.requestsystem.requestable.IDeliverable deliverable = activeReq.getRequest();
                                    if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable concrete) {
                                        for (ItemStack reqStack : concrete.getRequestedItems()) {
                                            if (!reqStack.isEmpty() && itemStackSelectionPredicate.test(reqStack)
                                                    && com.ae2colonies.domum.DomumOrnamentumHelper.isDOBlock(reqStack)) {
                                                if (terminal.canSynthesizeDOBlock(reqStack, needed)) {
                                                    cir.setReturnValue(true);
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }

                                // Check AE2 craftables
                                if (!terminal.getGrid().getCraftingService().getCpus().isEmpty()) {
                                    for (appeng.api.stacks.AEKey key : terminal.getGrid().getCraftingService().getCraftables(k -> k instanceof appeng.api.stacks.AEItemKey)) {
                                        if (key instanceof appeng.api.stacks.AEItemKey itemKey) {
                                            ItemStack candidate = itemKey.toStack(needed);
                                            if (itemStackSelectionPredicate.test(candidate) && !terminal.isCraftingFailedRecently(candidate)) {
                                                if (terminal.isCrafting(candidate) || AE2IntegrationHelper.canCraft(terminal.getGrid(), candidate, needed)) {
                                                    cir.setReturnValue(true);
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
                int target = count + leftOver;
                int totalAvailable = 0;
                for (ColonyTerminalBlockEntity terminal : WarehouseMEBridge.getTerminalsForWarehouse(warehouse)) {
                    if (terminal.isTerminalOnline()) {
                        if (terminal.isAllowWithdraw()) {
                            totalAvailable += AE2IntegrationHelper.getAvailableCount(terminal.getGrid(), itemStack, !ignoreNBT);
                        }
                        if (totalAvailable >= target) {
                            cir.setReturnValue(true);
                            return;
                        }
                        if (terminal.isAllowAutocraft() && !terminal.isCraftingFailedRecently(itemStack)) {
                            int needed = target - totalAvailable;
                            if (needed > 0) {
                                if (terminal.canSynthesizeDOBlock(itemStack, needed)
                                        || terminal.isCrafting(itemStack)
                                        || AE2IntegrationHelper.canCraft(terminal.getGrid(), itemStack, needed)) {
                                    cir.setReturnValue(true);
                                    return;
                                }
                            }
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
        if (warehouse == null) {
            return;
        }

        List<Tuple<ItemStack, BlockPos>> origList = cir.getReturnValue();
        List<Tuple<ItemStack, BlockPos>> list = (origList == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(origList);
        int totalFound = 0;
        if (list != null) {
            for (Tuple<ItemStack, BlockPos> tuple : list) {
                if (tuple != null && !tuple.getA().isEmpty()) {
                    totalFound += tuple.getA().getCount();
                }
            }
        }

        com.minecolonies.api.colony.requestsystem.request.IRequest<? extends com.minecolonies.api.colony.requestsystem.requestable.IDeliverable> activeReq =
                com.ae2colonies.colony.WarehouseRequestContext.getCurrentRequest();
        int requestedCount = activeReq != null ? activeReq.getRequest().getCount() : Integer.MAX_VALUE;

        if (totalFound >= requestedCount) {
            cir.setReturnValue(list);
            return; // Physical racks already have enough
        }

        for (ColonyTerminalBlockEntity terminal : WarehouseMEBridge.getTerminalsForWarehouse(warehouse)) {
            if (!terminal.isTerminalOnline()) {
                continue;
            }

            // 1. Gather storage items from this terminal
            java.util.List<ItemStack> ae2Matches = new java.util.ArrayList<>();
            int terminalStorageFound = 0;
            if (terminal.isAllowWithdraw()) {
                ae2Matches = AE2IntegrationHelper.getMatchingItemStacks(terminal.getGrid(), itemStackSelectionPredicate);
                for (ItemStack stack : ae2Matches) {
                    terminalStorageFound += stack.getCount();
                }
            }

            int needed = requestedCount - (totalFound + terminalStorageFound);

            // 2. Autocraft if needed and request is active
            ItemStack craftStack = ItemStack.EMPTY;
            int craftCount = 0;

            if (needed > 0 && activeReq != null && terminal.isAllowAutocraft() && terminal.getGrid() != null) {
                boolean hasCpus = !terminal.getGrid().getCraftingService().getCpus().isEmpty();
                com.minecolonies.api.colony.requestsystem.requestable.IDeliverable deliverable = activeReq.getRequest();

                if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable concrete) {
                    for (ItemStack reqStack : concrete.getRequestedItems()) {
                        if (reqStack.isEmpty() || !itemStackSelectionPredicate.test(reqStack)) {
                            continue;
                        }
                        if (com.ae2colonies.domum.DomumOrnamentumHelper.isDOBlock(reqStack)) {
                            if (terminal.canSynthesizeDOBlock(reqStack, needed)) {
                                terminal.synthesizeDOBlock(reqStack, needed);
                                craftStack = reqStack.copyWithCount(needed);
                                craftCount = needed;
                                break;
                            }
                        } else if (hasCpus && !terminal.isCraftingFailedRecently(reqStack)) {
                            if (terminal.isCrafting(reqStack) || AE2IntegrationHelper.canCraft(terminal.getGrid(), reqStack, needed)) {
                                terminal.queueCraftingRequest(reqStack, needed, "Colony Request");
                                craftStack = reqStack.copyWithCount(needed);
                                craftCount = needed;
                                break;
                            }
                        }
                    }
                } else if (hasCpus) {
                    for (appeng.api.stacks.AEKey key : terminal.getGrid().getCraftingService().getCraftables(k -> k instanceof appeng.api.stacks.AEItemKey)) {
                        if (key instanceof appeng.api.stacks.AEItemKey itemKey) {
                            ItemStack candidate = itemKey.toStack(needed);
                            if (!terminal.isCraftingFailedRecently(candidate) && itemStackSelectionPredicate.test(candidate)) {
                                if (terminal.isCrafting(candidate) || AE2IntegrationHelper.canCraft(terminal.getGrid(), candidate, needed)) {
                                    terminal.queueCraftingRequest(candidate, needed, "Colony Request");
                                    craftStack = candidate.copyWithCount(needed);
                                    craftCount = needed;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // 3. Add to list: Combine storage stack and craft stack for this terminal pos if matching!
            boolean craftCombined = false;
            for (ItemStack storageStack : ae2Matches) {
                if (!craftStack.isEmpty() && !craftCombined
                        && ItemStack.isSameItemSameComponents(storageStack, craftStack)) {
                    int combinedTotal = storageStack.getCount() + craftCount;
                    addSafeStacks(list, storageStack.copyWithCount(combinedTotal), terminal.getBlockPos());
                    craftCombined = true;
                } else {
                    addSafeStacks(list, storageStack, terminal.getBlockPos());
                }
            }

            if (!craftStack.isEmpty() && !craftCombined) {
                addSafeStacks(list, craftStack, terminal.getBlockPos());
            }

            totalFound += terminalStorageFound + craftCount;
            if (totalFound >= requestedCount) {
                cir.setReturnValue(list);
                return;
            }
        }
        cir.setReturnValue(list);
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
