package com.ae2colonies.mixin;

import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.colony.WarehouseMEBridge;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.core.tileentities.TileEntityWareHouse;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
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
                if (terminal.isTerminalOnline() && terminal.isAllowWithdraw()) {
                    List<ItemStack> ae2Matches = AE2IntegrationHelper.getMatchingItemStacks(terminal.getGrid(), itemStackSelectionPredicate);
                    for (ItemStack stack : ae2Matches) {
                        list.add(new Tuple<>(stack, terminal.getBlockPos()));
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
