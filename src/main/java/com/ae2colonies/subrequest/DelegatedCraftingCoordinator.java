package com.ae2colonies.subrequest;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.colony.WarehouseMEBridge;
import com.ae2colonies.config.AE2ColoniesConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates delegated crafting requests where missing ingredients are
 * sub-contracted to MineColonies workers via the linked Warehouse.
 */
public class DelegatedCraftingCoordinator {

    private final ColonyTerminalBlockEntity terminal;
    private final Map<UUID, DelegatedJob> activeJobs = new ConcurrentHashMap<>();

    public DelegatedCraftingCoordinator(@NotNull ColonyTerminalBlockEntity terminal) {
        this.terminal = terminal;
    }

    /**
     * Checks if there is already an active delegated job waiting for colony ingredients
     * for this item stack.
     */
    public boolean isDelegating(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (DelegatedJob job : activeJobs.values()) {
            // Only report as delegating while actively waiting on the colony.
            // Once in AE2_CRAFTING, AE2's own trackers take over to avoid blocking requestCrafting!
            if ((job.getState() == DelegatedJobState.WAITING_FOR_COLONY || job.getState() == DelegatedJobState.ANALYZING)
                    && ItemStack.isSameItemSameComponents(job.getTargetStack(), stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether AE2 can delegate missing ingredients for the target stack
     * to the colony.
     */
    public boolean canDelegate(@NotNull ItemStack targetStack, int amount) {
        if (!AE2ColoniesConfig.ENABLE_SUB_REQUEST_DELEGATION.get()) {
            return false;
        }

        if (!terminal.isTerminalOnline() || !terminal.isAllowAutocraft() || !terminal.isAllowDeposit()
                || targetStack.isEmpty() || amount <= 0) {
            return false;
        }

        long nonTerminalCount = activeJobs.values().stream().filter(j -> !j.getState().isTerminal()).count();
        if (nonTerminalCount >= AE2ColoniesConfig.MAX_CONCURRENT_DELEGATED_JOBS.get()) {
            return false;
        }

        if (isDelegating(targetStack)) {
            return false;
        }

        IWareHouse warehouse = WarehouseMEBridge.getWarehouseForTerminal(terminal);
        if (warehouse == null) {
            return false;
        }

        List<ItemStack> missing = AE2IntegrationHelper.findMissingIngredients(terminal.getGrid(), targetStack, amount);
        return !missing.isEmpty();
    }

    /**
     * Starts a delegated job by creating MineColonies requests on behalf of the Warehouse
     * for all missing ingredients.
     */
    @Nullable
    public DelegatedJob startDelegation(
            @NotNull ItemStack targetStack,
            int amount,
            @NotNull String requesterName,
            long currentTick
    ) {
        if (!canDelegate(targetStack, amount)) {
            return null;
        }

        IWareHouse warehouse = WarehouseMEBridge.getWarehouseForTerminal(terminal);
        if (warehouse == null) {
            return null;
        }

        List<ItemStack> missing = AE2IntegrationHelper.findMissingIngredients(terminal.getGrid(), targetStack, amount);
        if (missing.isEmpty()) {
            return null;
        }

        UUID jobId = UUID.randomUUID();
        DelegatedJob job = new DelegatedJob(jobId, targetStack, amount, requesterName, currentTick, missing);

        // Submit sub-requests to MineColonies via the Warehouse
        for (ItemStack ingredient : missing) {
            try {
                com.minecolonies.api.colony.requestsystem.requestable.Stack stackRequestable =
                        new com.minecolonies.api.colony.requestsystem.requestable.Stack(ingredient);
                IToken<?> token = warehouse.createRequest(stackRequestable, false);
                if (token != null) {
                    job.trackIngredientToken(ingredient, token);
                    AE2Colonies.LOGGER.info("Delegated sub-request created for {} x{} via Warehouse at {} (Job: {})",
                            ingredient, ingredient.getCount(), warehouse.getPosition(), jobId);
                } else {
                    AE2Colonies.LOGGER.warn("Failed to create sub-request token for {} (Warehouse returned null token)", ingredient);
                }
            } catch (Exception e) {
                AE2Colonies.LOGGER.error("Exception creating sub-request for {} in MineColonies: {}", ingredient, e.getMessage());
            }
        }

        job.setState(DelegatedJobState.WAITING_FOR_COLONY, currentTick);
        activeJobs.put(jobId, job);
        terminal.setChanged();

        AE2Colonies.LOGGER.info("Started DelegatedCraftingJob {} for {} x{} (waiting on {} missing ingredients)",
                jobId, targetStack, amount, missing.size());
        return job;
    }

    /**
     * Ticks the coordinator to check ingredient arrivals, trigger AE2 jobs,
     * and handle timeouts.
     */
    public void tick(long currentTick) {
        if (activeJobs.isEmpty() || !terminal.isTerminalOnline()) {
            return;
        }

        long timeoutTicks = AE2ColoniesConfig.SUB_REQUEST_TIMEOUT_TICKS.get();
        List<UUID> toRemove = new ArrayList<>();

        for (DelegatedJob job : activeJobs.values()) {
            // Check timeout
            if (!job.getState().isTerminal() && job.isTimedOut(currentTick, timeoutTicks)) {
                AE2Colonies.LOGGER.warn("DelegatedCraftingJob {} timed out after {} ticks waiting for ingredients for {}",
                        job.getJobId(), timeoutTicks, job.getTargetStack());
                cancelJobSubRequests(job);
                job.setState(DelegatedJobState.CANCELLED, currentTick);
                terminal.setChanged();
                continue;
            }

            switch (job.getState()) {
                case WAITING_FOR_COLONY -> {
                    // Check if all required ingredients have arrived in ME storage
                    if (terminal.getGrid() != null) {
                        // Aggregate required totals per item to handle multiple split stacks correctly
                        Map<AEItemKey, Long> requiredTotals = new HashMap<>();
                        for (ItemStack ingredient : job.getRequiredIngredients()) {
                            AEItemKey key = AEItemKey.of(ingredient);
                            if (key != null) {
                                requiredTotals.merge(key, (long) ingredient.getCount(), Long::sum);
                            }
                        }

                        boolean allPresent = true;
                        for (Map.Entry<AEItemKey, Long> entry : requiredTotals.entrySet()) {
                            long inStorage = AE2IntegrationHelper.getAvailableCount(terminal.getGrid(), entry.getKey().toStack(), false);
                            if (inStorage < entry.getValue()) {
                                allPresent = false;
                                break;
                            }
                        }

                        if (allPresent) {
                            AE2Colonies.LOGGER.info("All ingredients arrived in ME storage for DelegatedCraftingJob {}! Triggering AE2 craft for {} x{}",
                                    job.getJobId(), job.getTargetStack(), job.getTargetAmount());
                            // Set to AE2_CRAFTING before queuing request so isDelegating() returns false
                            // and does not block requestCrafting()!
                            job.setState(DelegatedJobState.AE2_CRAFTING, currentTick);

                            // Trigger the actual AE2 autocrafting job now that all ingredients are present
                            terminal.queueCraftingRequest(
                                    job.getTargetStack(),
                                    job.getTargetAmount(),
                                    "Delegated: " + job.getRequesterName()
                            );
                            terminal.setChanged();
                        }
                    }
                }
                case AE2_CRAFTING -> {
                    // Grace period of at least 40 ticks (2 seconds) after submitting before checking completion
                    // to give the async calculation queue time to start tracking
                    if (currentTick - job.getLastStateChangeTick() > 40) {
                        if (!terminal.isCrafting(job.getTargetStack())) {
                            long available = AE2IntegrationHelper.getAvailableCount(terminal.getGrid(), job.getTargetStack(), false);
                            if (available >= job.getTargetAmount()) {
                                AE2Colonies.LOGGER.info("DelegatedCraftingJob {} completed successfully! Target item {} is ready in ME storage.",
                                        job.getJobId(), job.getTargetStack());
                                job.setState(DelegatedJobState.COMPLETED, currentTick);
                                terminal.setChanged();
                            }
                        }
                    }
                }
                case COMPLETED, CANCELLED, FAILED -> {
                    // Clean up terminal jobs after 200 ticks (10 seconds)
                    if (currentTick - job.getLastStateChangeTick() > 200) {
                        toRemove.add(job.getJobId());
                    }
                }
                default -> {}
            }
        }

        for (UUID id : toRemove) {
            activeJobs.remove(id);
        }
    }

    private void cancelJobSubRequests(DelegatedJob job) {
        IWareHouse warehouse = WarehouseMEBridge.getWarehouseForTerminal(terminal);
        IColony colony = warehouse != null ? warehouse.getColony() : null;
        if (colony != null) {
            for (IToken<?> token : job.getPendingIngredients().values()) {
                try {
                    colony.getRequestManager().updateRequestState(token, RequestState.CANCELLED);
                } catch (Exception e) {
                    AE2Colonies.LOGGER.debug("Could not cancel token {}: {}", token, e.getMessage());
                }
            }
        }
    }

    /**
     * Cancels a specific delegated job and all its active MineColonies requests.
     */
    public void cancelJob(UUID jobId, long currentTick) {
        DelegatedJob job = activeJobs.get(jobId);
        if (job != null && !job.getState().isTerminal()) {
            cancelJobSubRequests(job);
            job.setState(DelegatedJobState.CANCELLED, currentTick);
            terminal.setChanged();
        }
    }

    /**
     * Cancels all delegated jobs when the terminal is broken or unloaded.
     */
    public void cancelAll() {
        for (DelegatedJob job : activeJobs.values()) {
            if (!job.getState().isTerminal()) {
                cancelJobSubRequests(job);
                job.setState(DelegatedJobState.CANCELLED, 0);
            }
        }
        activeJobs.clear();
    }

    public Collection<DelegatedJob> getActiveJobs() {
        return Collections.unmodifiableCollection(activeJobs.values());
    }

    public void save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (DelegatedJob job : activeJobs.values()) {
            CompoundTag jobTag = new CompoundTag();
            jobTag.putUUID("JobId", job.getJobId());
            jobTag.putString("Requester", job.getRequesterName());
            jobTag.putInt("TargetAmount", job.getTargetAmount());
            jobTag.putString("State", job.getState().name());
            jobTag.putLong("CreatedTick", job.getCreatedTick());

            Tag stackTag = ItemStack.OPTIONAL_CODEC.encodeStart(
                    provider.createSerializationContext(NbtOps.INSTANCE), job.getTargetStack()
            ).result().orElse(new CompoundTag());
            jobTag.put("TargetStack", stackTag);

            list.add(jobTag);
        }
        tag.put("DelegatedJobs", list);
    }

    public void load(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        if (!tag.contains("DelegatedJobs", Tag.TAG_LIST)) {
            return;
        }
        activeJobs.clear();
        ListTag list = tag.getList("DelegatedJobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag jobTag = list.getCompound(i);
            try {
                UUID jobId = jobTag.getUUID("JobId");
                String requester = jobTag.getString("Requester");
                int amount = jobTag.getInt("TargetAmount");
                String stateName = jobTag.getString("State");
                long createdTick = jobTag.getLong("CreatedTick");

                ItemStack targetStack = ItemStack.OPTIONAL_CODEC.parse(
                        provider.createSerializationContext(NbtOps.INSTANCE), jobTag.get("TargetStack")
                ).result().orElse(ItemStack.EMPTY);

                if (!targetStack.isEmpty()) {
                    DelegatedJob job = new DelegatedJob(jobId, targetStack, amount, requester, createdTick, Collections.emptyList());
                    try {
                        job.setState(DelegatedJobState.valueOf(stateName), createdTick);
                    } catch (Exception e) {
                        job.setState(DelegatedJobState.WAITING_FOR_COLONY, createdTick);
                    }
                    activeJobs.put(jobId, job);
                }
            } catch (Exception e) {
                AE2Colonies.LOGGER.debug("Failed to deserialize delegated job: {}", e.getMessage());
            }
        }
    }
}
