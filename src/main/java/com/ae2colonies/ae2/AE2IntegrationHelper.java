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

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                int maxStack = Math.min(itemKey.getItem().getDefaultMaxStackSize(), 64);
                // Test with a single stack to check predicate
                ItemStack testStack = itemKey.toStack(1);
                if (predicate.test(testStack)) {
                    // Split full available count into safe-sized stacks
                    long remaining = entry.getValue();
                    while (remaining > 0) {
                        int chunk = (int) Math.min(maxStack, remaining);
                        result.add(itemKey.toStack(chunk));
                        remaining -= chunk;
                    }
                }
            }
        }
        return result;
    }

    @NotNull
    public static ItemStack extractItem(@Nullable IGrid grid, @NotNull ItemStack request, @NotNull IActionSource source) {
        return extractItem(grid, request, source, Actionable.MODULATE);
    }

    @NotNull
    public static ItemStack extractItem(@Nullable IGrid grid, @NotNull ItemStack request, @NotNull IActionSource source, Actionable mode) {
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

        long extracted = storage.extract(key, request.getCount(), mode, source);
        if (extracted <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack result = request.copy();
        result.setCount((int) extracted);
        return result;
    }

    @NotNull
    public static ItemStack insertItem(@Nullable IGrid grid, @NotNull ItemStack stack, @NotNull IActionSource source) {
        return insertItem(grid, stack, source, Actionable.MODULATE);
    }

    @NotNull
    public static ItemStack insertItem(@Nullable IGrid grid, @NotNull ItemStack stack, @NotNull IActionSource source, Actionable mode) {
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

        long inserted = storage.insert(key, stack.getCount(), mode, source);
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
        if (craftingService == null || craftingService.getCpus().isEmpty()) {
            return false;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return false;
        }

        return craftingService.isCraftable(key);
    }

    private static int getMaxCraftDepth() {
        return com.ae2colonies.config.AE2ColoniesConfig.MAX_CRAFT_DEPTH.get();
    }

    public static boolean canCraft(@Nullable IGrid grid, @NotNull ItemStack stack, long amountNeeded) {
        if (grid == null || stack.isEmpty() || amountNeeded <= 0) {
            return false;
        }

        if (!isNetworkPowered(grid)) {
            return false;
        }

        ICraftingService craftingService = getCraftingService(grid);
        if (craftingService == null || craftingService.getCpus().isEmpty()) {
            return false;
        }

        // Only claim craftable if at least one CPU is idle — otherwise we'd block
        // MineColonies workers from handling the request themselves
        boolean hasIdleCpu = false;
        for (var cpu : craftingService.getCpus()) {
            if (!cpu.isBusy()) {
                hasIdleCpu = true;
                break;
            }
        }
        if (!hasIdleCpu) {
            return false;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return false;
        }

        var patterns = craftingService.getCraftingFor(key);
        if (patterns.isEmpty()) {
            return false;
        }

        MEStorage storage = getStorage(grid);
        if (storage == null) {
            return false;
        }

        Set<AEKey> visited = new HashSet<>();
        Map<AEKey, Long> simulatedUsage = new HashMap<>();

        for (var pattern : patterns) {
            Map<AEKey, Long> snapshot = new HashMap<>(simulatedUsage);
            visited.clear();
            visited.add(key);

            if (hasCraftingIngredients(grid, storage, craftingService, pattern, amountNeeded, 0, visited, simulatedUsage)) {
                return true;
            }
            simulatedUsage = snapshot;
        }

        return false;
    }

    private static boolean hasCraftingIngredients(
            IGrid grid,
            MEStorage storage,
            ICraftingService craftingService,
            IPatternDetails pattern,
            long amountNeeded,
            int depth,
            Set<AEKey> visited,
            Map<AEKey, Long> simulatedUsage
    ) {
        if (pattern == null || amountNeeded <= 0 || depth > getMaxCraftDepth()) {
            return false;
        }

        GenericStack primaryOutput = pattern.getPrimaryOutput();
        if (primaryOutput == null || primaryOutput.amount() <= 0) {
            return false;
        }

        long outputAmount = primaryOutput.amount();
        long batches = (amountNeeded + outputAmount - 1) / outputAmount;

        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null || inputs.length == 0) {
            return true;
        }

        for (IPatternDetails.IInput input : inputs) {
            GenericStack[] possibles = input.getPossibleInputs();
            if (possibles == null || possibles.length == 0) {
                continue;
            }
            long neededPerBatch = input.getMultiplier();
            long totalNeeded = neededPerBatch * batches;

            boolean inputSatisfied = false;
            for (GenericStack possible : possibles) {
                if (possible == null || possible.what() == null) {
                    continue;
                }
                AEKey inputKey = possible.what();

                long alreadyUsed = simulatedUsage.getOrDefault(inputKey, 0L);
                long totalInStorage = storage.extract(inputKey, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                long available = Math.max(0, totalInStorage - alreadyUsed);

                if (available >= totalNeeded) {
                    simulatedUsage.put(inputKey, alreadyUsed + totalNeeded);
                    inputSatisfied = true;
                    break;
                }

                // If available is less than totalNeeded, check if we can sub-craft the remainder (up to MAX_CRAFT_DEPTH)
                if (depth < getMaxCraftDepth() && !visited.contains(inputKey) && craftingService.isCraftable(inputKey)) {
                    long stillNeeded = totalNeeded - available;
                    var subPatterns = craftingService.getCraftingFor(inputKey);
                    if (!subPatterns.isEmpty()) {
                        visited.add(inputKey);
                        for (var subPattern : subPatterns) {
                            Map<AEKey, Long> branchSnapshot = new HashMap<>(simulatedUsage);
                            if (available > 0) {
                                branchSnapshot.put(inputKey, alreadyUsed + available);
                            }

                            if (hasCraftingIngredients(grid, storage, craftingService, subPattern, stillNeeded, depth + 1, visited, branchSnapshot)) {
                                simulatedUsage.clear();
                                simulatedUsage.putAll(branchSnapshot);
                                inputSatisfied = true;
                                break;
                            }
                        }
                        visited.remove(inputKey);
                        if (inputSatisfied) {
                            break;
                        }
                    }
                }
            }

            if (!inputSatisfied) {
                return false;
            }
        }

        return true;
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

        // ICraftingSimulationRequester provides the action source for the crafting simulation
        appeng.api.networking.crafting.ICraftingSimulationRequester simRequester = () ->
                IActionSource.ofMachine(requester);

        return craftingService.beginCraftingCalculation(
                level,
                simRequester,
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
