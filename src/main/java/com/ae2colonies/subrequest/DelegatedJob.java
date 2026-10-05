package com.ae2colonies.subrequest;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a single delegated crafting task where missing ingredients
 * have been requested from MineColonies workers.
 */
public class DelegatedJob {

    private final UUID jobId;
    private final ItemStack targetStack;
    private final int targetAmount;
    private final String requesterName;
    private final long createdTick;

    private DelegatedJobState state;
    private final Map<ItemStack, IToken<?>> pendingIngredients = new LinkedHashMap<>();
    private final List<ItemStack> requiredIngredients = new ArrayList<>();
    
    @Nullable
    private String ae2JobId;
    private long lastStateChangeTick;

    public DelegatedJob(
            @NotNull UUID jobId,
            @NotNull ItemStack targetStack,
            int targetAmount,
            @NotNull String requesterName,
            long createdTick,
            @NotNull List<ItemStack> missingIngredients
    ) {
        this.jobId = jobId;
        this.targetStack = targetStack.copy();
        this.targetAmount = targetAmount;
        this.requesterName = requesterName;
        this.createdTick = createdTick;
        this.lastStateChangeTick = createdTick;
        this.state = DelegatedJobState.ANALYZING;

        for (ItemStack ingredient : missingIngredients) {
            this.requiredIngredients.add(ingredient.copy());
        }
    }

    public UUID getJobId() {
        return jobId;
    }

    public ItemStack getTargetStack() {
        return targetStack;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public long getCreatedTick() {
        return createdTick;
    }

    public DelegatedJobState getState() {
        return state;
    }

    public void setState(DelegatedJobState state, long currentTick) {
        this.state = state;
        this.lastStateChangeTick = currentTick;
    }

    public long getLastStateChangeTick() {
        return lastStateChangeTick;
    }

    public List<ItemStack> getRequiredIngredients() {
        return Collections.unmodifiableList(requiredIngredients);
    }

    public Map<ItemStack, IToken<?>> getPendingIngredients() {
        return pendingIngredients;
    }

    public void trackIngredientToken(ItemStack stack, IToken<?> token) {
        pendingIngredients.put(stack, token);
    }

    public void onIngredientDelivered(IToken<?> token) {
        pendingIngredients.values().removeIf(t -> t.equals(token));
    }

    public boolean areAllIngredientsDelivered() {
        return pendingIngredients.isEmpty();
    }

    @Nullable
    public String getAe2JobId() {
        return ae2JobId;
    }

    public void setAe2JobId(@Nullable String ae2JobId) {
        this.ae2JobId = ae2JobId;
    }

    public boolean isTimedOut(long currentTick, long timeoutTicks) {
        return (currentTick - createdTick) > timeoutTicks;
    }
}
