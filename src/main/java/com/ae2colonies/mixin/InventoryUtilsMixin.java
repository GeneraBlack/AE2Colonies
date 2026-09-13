package com.ae2colonies.mixin;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.colony.WarehouseMEBridge;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(value = InventoryUtils.class, remap = false)
public abstract class InventoryUtilsMixin {

    @Inject(
            method = "hasBuildingEnoughElseCount(Lcom/minecolonies/api/colony/buildings/ICommonBuilding;Lcom/minecolonies/api/crafting/ItemStorage;I)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void onHasBuildingEnoughElseCountStorage(
            ICommonBuilding provider,
            ItemStorage stack,
            int count,
            CallbackInfoReturnable<Integer> cir
    ) {
        int current = cir.getReturnValue();
        if (current >= count) {
            return;
        }

        if (provider instanceof IWareHouse warehouse) {
            List<ColonyTerminalBlockEntity> terminals = WarehouseMEBridge.getTerminalsForWarehouse(warehouse);
            if (terminals.isEmpty()) {
                return;
            }

            ItemStack is = stack.getItemStack();
            if (is.isEmpty()) {
                return;
            }

            boolean matchNBT = !stack.ignoreNBT();
            int total = current;

            for (ColonyTerminalBlockEntity terminal : terminals) {
                if (!terminal.isTerminalOnline()) {
                    continue;
                }

                if (terminal.isAllowWithdraw()) {
                    int ae2Count = AE2IntegrationHelper.getAvailableCount(terminal.getGrid(), is, matchNBT);
                    if (ae2Count > 0) {
                        total += ae2Count;
                        AE2Colonies.LOGGER.info("InventoryUtilsMixin(Storage): AE2 has {} of {} (total now {})", ae2Count, is, total);
                    }
                    if (total >= count) {
                        cir.setReturnValue(total);
                        return;
                    }
                }

                if (terminal.isAllowAutocraft()) {
                    int needed = count - total;
                    if (needed > 0) {
                        if (AE2IntegrationHelper.isCraftable(terminal.getGrid(), is)
                                || terminal.canSynthesizeDOBlock(is, needed)) {
                            total += needed;
                            AE2Colonies.LOGGER.info("InventoryUtilsMixin(Storage): AE2 can craft {} (total now {})", is, total);
                            cir.setReturnValue(total);
                            return;
                        }
                    }
                }
            }

            if (total > current) {
                cir.setReturnValue(total);
            }
        }
    }

    @Inject(
            method = "hasBuildingEnoughElseCount(Lcom/minecolonies/api/colony/buildings/ICommonBuilding;Ljava/util/function/Predicate;I)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void onHasBuildingEnoughElseCountPredicate(
            ICommonBuilding provider,
            Predicate<ItemStack> stackPredicate,
            int count,
            CallbackInfoReturnable<Integer> cir
    ) {
        int current = cir.getReturnValue();
        if (current >= count) {
            return;
        }

        if (provider instanceof IWareHouse warehouse) {
            List<ColonyTerminalBlockEntity> terminals = WarehouseMEBridge.getTerminalsForWarehouse(warehouse);
            if (terminals.isEmpty()) {
                return;
            }

            int total = current;

            for (ColonyTerminalBlockEntity terminal : terminals) {
                if (!terminal.isTerminalOnline()) {
                    continue;
                }

                if (terminal.isAllowWithdraw()) {
                    int ae2Count = AE2IntegrationHelper.getAvailableCount(terminal.getGrid(), stackPredicate);
                    if (ae2Count > 0) {
                        total += ae2Count;
                        AE2Colonies.LOGGER.info("InventoryUtilsMixin(Predicate): AE2 has {} matching items (total now {})", ae2Count, total);
                    }
                    if (total >= count) {
                        cir.setReturnValue(total);
                        return;
                    }
                }

                if (terminal.isAllowAutocraft() && terminal.getGrid() != null) {
                    int needed = count - total;
                    if (needed > 0) {
                        for (appeng.api.stacks.AEKey key : terminal.getGrid().getCraftingService().getCraftables(k -> k instanceof appeng.api.stacks.AEItemKey)) {
                            if (key instanceof appeng.api.stacks.AEItemKey itemKey) {
                                ItemStack candidate = itemKey.toStack(needed);
                                if (stackPredicate.test(candidate)) {
                                    total += needed;
                                    AE2Colonies.LOGGER.info("InventoryUtilsMixin(Predicate): AE2 can craft {} (total now {})", candidate, total);
                                    cir.setReturnValue(total);
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            if (total > current) {
                cir.setReturnValue(total);
            }
        }
    }
}
