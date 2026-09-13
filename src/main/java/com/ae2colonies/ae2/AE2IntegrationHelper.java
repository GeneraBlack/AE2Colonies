package com.ae2colonies.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public class AE2IntegrationHelper {

    @Nullable
    public static IGrid getGrid(@Nullable IGridNode node) {
        if (node == null) {
            return null;
        }
        return node.getGrid();
    }

    public static boolean isNodeOnline(@Nullable IGridNode node) {
        return node != null && node.isOnline();
    }

    @Nullable
    public static MEStorage getStorage(@Nullable IGrid grid) {
        if (grid == null) {
            return null;
        }
        return grid.getStorageService().getInventory();
    }

    @Nullable
    public static ICraftingService getCraftingService(@Nullable IGrid grid) {
        if (grid == null) {
            return null;
        }
        return grid.getCraftingService();
    }

    public static boolean isNetworkPowered(@Nullable IGrid grid) {
        if (grid == null) {
            return false;
        }
        return grid.getEnergyService().isNetworkPowered();
    }

    public static int getAvailableCount(@Nullable IGrid grid, @NotNull ItemStack stack, boolean matchNBT) {
        MEStorage storage = getStorage(grid);
        if (storage == null) {
            return 0;
        }

        AEItemKey targetKey = AEItemKey.of(stack);
        if (targetKey == null) {
            return 0;
        }

        KeyCounter counter = new KeyCounter();
        storage.getAvailableStacks(counter);

        if (matchNBT) {
            return (int) Math.min(Integer.MAX_VALUE, counter.get(targetKey));
        } else {
            long total = 0;
            for (Map.Entry<AEKey, Long> entry : counter) {
                if (entry.getKey() instanceof AEItemKey itemKey) {
                    if (itemKey.getItem() == stack.getItem()) {
                        total += entry.getValue();
                    }
                }
            }
            return (int) Math.min(Integer.MAX_VALUE, total);
        }
    }

    public static int getAvailableCount(@Nullable IGrid grid, @NotNull Predicate<ItemStack> predicate) {
        MEStorage storage = getStorage(grid);
        if (storage == null) {
            return 0;
        }

        KeyCounter counter = new KeyCounter();
        storage.getAvailableStacks(counter);

        long total = 0;
        for (Map.Entry<AEKey, Long> entry : counter) {
            if (entry.getKey() instanceof AEItemKey itemKey) {
                ItemStack is = itemKey.toStack((int) Math.min(Integer.MAX_VALUE, entry.getValue()));
                if (predicate.test(is)) {
                    total += entry.getValue();
                }
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    @NotNull
    public static List<ItemStack> getMatchingItemStacks(@Nullable IGrid grid, @NotNull Predicate<ItemStack> predicate) {
        List<ItemStack> result = new ArrayList<>();
        MEStorage storage = getStorage(grid);
        if (storage == null) {
            return result;
        }

        KeyCounter counter = new KeyCounter();
        storage.getAvailableStacks(counter);

        for (Map.Entry<AEKey, Long> entry : counter) {
            if (entry.getKey() instanceof AEItemKey itemKey) {
                int count = (int) Math.min(itemKey.getItem().getDefaultMaxStackSize(), entry.getValue());
                ItemStack is = itemKey.toStack(count);
                if (predicate.test(is)) {
                    result.add(is);
                }
            }
        }
        return result;
    }

    @NotNull
    public static ItemStack extractItem(@Nullable IGrid grid, @NotNull ItemStack request, @NotNull IActionSource source) {
        if (request.isEmpty()) {
            return ItemStack.EMPTY;
        }

        MEStorage storage = getStorage(grid);
        if (storage == null) {
            return ItemStack.EMPTY;
        }

        AEItemKey key = AEItemKey.of(request);
        if (key == null) {
            return ItemStack.EMPTY;
        }

        long extracted = storage.extract(key, request.getCount(), Actionable.MODULATE, source);
        if (extracted <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack result = request.copy();
        result.setCount((int) extracted);
        return result;
    }

    @NotNull
    public static ItemStack insertItem(@Nullable IGrid grid, @NotNull ItemStack stack, @NotNull IActionSource source) {
        if (stack.isEmpty()) {
            return stack;
        }

        MEStorage storage = getStorage(grid);
        if (storage == null) {
            return stack;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return stack;
        }

        long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, source);
        if (inserted <= 0) {
            return stack;
        }

        int remaining = (int) (stack.getCount() - inserted);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        remainder.setCount(remaining);
        return remainder;
    }

    public static boolean isCraftable(@Nullable IGrid grid, @NotNull ItemStack stack) {
        ICraftingService craftingService = getCraftingService(grid);
        if (craftingService == null) {
            return false;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return false;
        }

        return craftingService.isCraftable(key);
    }

    public static Future<ICraftingPlan> requestCraftingCalculation(
            @NotNull Level level,
            @Nullable IGrid grid,
            @NotNull ICraftingRequester requester,
            @NotNull ItemStack stack,
            long amount
    ) {
        ICraftingService craftingService = getCraftingService(grid);
        if (craftingService == null) {
            return null;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return null;
        }

        return craftingService.beginCraftingCalculation(
                level,
                null,
                key,
                amount,
                CalculationStrategy.REPORT_MISSING_ITEMS
        );
    }

    public static ICraftingSubmitResult submitCraftingJob(
            @Nullable IGrid grid,
            @NotNull ICraftingPlan plan,
            @NotNull ICraftingRequester requester,
            @NotNull IActionSource source
    ) {
        ICraftingService craftingService = getCraftingService(grid);
        if (craftingService == null) {
            return null;
        }

        return craftingService.submitJob(plan, requester, null, false, source);
    }
}
