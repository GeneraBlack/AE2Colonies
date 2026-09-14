package com.ae2colonies.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import com.ae2colonies.AE2Colonies;
import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.ae2.ColonyCraftingTracker;
import com.ae2colonies.block.ColonyTerminalBlock;
import com.ae2colonies.colony.WarehouseMEBridge;
import com.ae2colonies.domum.DomumOrnamentumHelper;
import com.ae2colonies.init.ModBlockEntities;
import com.ae2colonies.menu.ColonyTerminalMenu;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

public class ColonyTerminalBlockEntity extends AENetworkedBlockEntity
        implements ICraftingRequester, IActionHost, MenuProvider {

    public static class PendingCalculation {
        private final Future<ICraftingPlan> future;
        private final ItemStack stack;
        private final long amount;
        private final String requesterName;
        private final long createdTick;

        public PendingCalculation(Future<ICraftingPlan> future, ItemStack stack, long amount, String requesterName, long createdTick) {
            this.future = future;
            this.stack = stack;
            this.amount = amount;
            this.requesterName = requesterName;
            this.createdTick = createdTick;
        }

        public Future<ICraftingPlan> getFuture() { return future; }
        public ItemStack getStack() { return stack; }
        public long getAmount() { return amount; }
        public String getRequesterName() { return requesterName; }
        public long getCreatedTick() { return createdTick; }
    }

    private boolean allowDeposit = true;
    private boolean allowWithdraw = true;
    private boolean allowAutocraft = true;

    private int linkedColonyId = -1;
    @Nullable
    private BlockPos linkedWarehousePos = null;

    private final ColonyCraftingTracker craftingTracker = new ColonyCraftingTracker();
    private final List<PendingCalculation> pendingCalculations = new CopyOnWriteArrayList<>();
    private final IActionSource actionSource = IActionSource.ofMachine(this);
    
    private final java.util.Map<java.util.function.Predicate<ItemStack>, Long> failedCrafts = new java.util.concurrent.ConcurrentHashMap<>();

    public void markCraftingFailed(ItemStack stack) {
        failedCrafts.put(s -> ItemStack.isSameItemSameComponents(s, stack), System.currentTimeMillis());
    }

    public boolean isCraftingFailedRecently(ItemStack stack) {
        long now = System.currentTimeMillis();
        failedCrafts.entrySet().removeIf(e -> now - e.getValue() > 30000); // 30 second cooldown
        for (java.util.function.Predicate<ItemStack> p : failedCrafts.keySet()) {
            if (p.test(stack)) return true;
        }
        return false;
    }

    public ColonyTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLONY_TERMINAL.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(1.5)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingRequester.class, this);
    }

    private int tickCounter = 0;

    @Override
    public void onReady() {
        super.onReady();
        WarehouseMEBridge.registerTerminal(this);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Don't do any work during server shutdown
        if (WarehouseMEBridge.isShuttingDown()) {
            return;
        }

        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) {
            task.run();
        }

        tickCounter++;
        // If not yet linked, retry every 40 ticks (2 seconds)
        // If linked, periodically re-verify every 200 ticks (10 seconds)
        if (linkedWarehousePos == null) {
            if (tickCounter % 40 == 0) {
                WarehouseMEBridge.registerTerminal(this);
            }
        } else {
            if (tickCounter % 200 == 0) {
                WarehouseMEBridge.registerTerminal(this);
            }
        }

        // Process pending crafting calculations asynchronously
        if (!pendingCalculations.isEmpty()) {
            IGrid grid = getGrid();
            for (PendingCalculation pending : pendingCalculations) {
                if (pending.getFuture().isDone()) {
                    pendingCalculations.remove(pending);
                    if (grid != null) {
                        try {
                            ICraftingPlan plan = pending.getFuture().get();
                            if (plan != null) {
                                AE2Colonies.LOGGER.info("Crafting plan completed for {}. Simulation: {}, Missing: {}", pending.getStack(), plan.simulation(), plan.missingItems() != null ? plan.missingItems().size() : 0);
                                
                                int cpuCount = grid.getCraftingService().getCpus().size();
                                ICraftingSubmitResult result = AE2IntegrationHelper.submitCraftingJob(
                                        grid,
                                        plan,
                                        this,
                                        getActionSource()
                                );

                                if (result != null && result.successful() && result.link() != null) {
                                    AE2Colonies.LOGGER.info("submitJob successful for {}. Tracking job...", pending.getStack());
                                    craftingTracker.trackJob(
                                            result.link(),
                                            java.util.UUID.randomUUID().toString(),
                                            pending.getStack(),
                                            pending.getAmount(),
                                            pending.getRequesterName()
                                    );
                                    setChanged();
                                } else {
                                    markCraftingFailed(pending.getStack());
                                    AE2Colonies.LOGGER.warn("submitJob failed for {} (errorCode: {}, active CPUs on grid: {})", 
                                            pending.getStack(), result != null ? result.errorCode() : "null", cpuCount);
                                    if (result != null && result.errorCode() == appeng.api.networking.crafting.CraftingSubmitErrorCode.NO_CPU_FOUND) {
                                        AE2Colonies.LOGGER.warn(">>> WARNING: No Crafting CPU found on ME network! Autocrafting requires at least one Crafting CPU (Crafting Storage multiblock) connected to the ME network. <<<");
                                    }
                                }
                            } else {
                                AE2Colonies.LOGGER.info("Crafting plan was null for {}", pending.getStack());
                            }
                        } catch (Exception e) {
                            AE2Colonies.LOGGER.error("Failed to complete crafting calculation for {}", pending.getStack(), e);
                        }
                    }
                } else if (tickCounter - pending.getCreatedTick() > 200) {
                    AE2Colonies.LOGGER.info("Canceling pending calculation for {} after timeout", pending.getStack());
                    pendingCalculations.remove(pending);
                    pending.getFuture().cancel(true);
                }
            }
        }
    }

    @Override
    public void setRemoved() {
        // Cancel all pending crafting calculations
        for (PendingCalculation pending : pendingCalculations) {
            pending.getFuture().cancel(true);
        }
        pendingCalculations.clear();
        WarehouseMEBridge.unregisterTerminal(this);
        super.setRemoved();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State state) {
        super.onMainNodeStateChanged(state);
        if (level != null && !level.isClientSide()) {
            boolean online = getMainNode().isOnline();
            BlockState currentState = getBlockState();
            if (currentState.hasProperty(ColonyTerminalBlock.ONLINE)
                    && currentState.getValue(ColonyTerminalBlock.ONLINE) != online) {
                level.setBlock(worldPosition, currentState.setValue(ColonyTerminalBlock.ONLINE, online), 3);
            }
        }
    }

    // --- ICraftingRequester Implementation ---

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return craftingTracker.getRequestedJobs();
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        if (mode == Actionable.MODULATE) {
            craftingTracker.onCrafted(link, amount);
            IGrid grid = getMainNode().getGrid();
            if (grid != null) {
                grid.getStorageService().getInventory().insert(what, amount, Actionable.MODULATE, getActionSource());
            }
        }
        return amount;
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        if (link.isDone()) {
            craftingTracker.onJobComplete(link);
        } else if (link.isCanceled()) {
            craftingTracker.onJobCanceled(link);
        }
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    public IActionSource getActionSource() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            IGridNode node = getMainNode().getNode();
            if (node != null) {
                java.util.UUID ownerId = node.getOwningPlayerProfileId();
                if (ownerId != null) {
                    Player player = serverLevel.getPlayerByUUID(ownerId);
                    if (player != null) {
                        return IActionSource.ofPlayer(player, this);
                    }
                }
            }
            if (!serverLevel.players().isEmpty()) {
                return IActionSource.ofPlayer(serverLevel.players().get(0), this);
            }
        }
        return actionSource;
    }

    public ColonyCraftingTracker getCraftingTracker() {
        return craftingTracker;
    }

    // --- Configuration & Colony Association ---

    public boolean isAllowDeposit() {
        return allowDeposit;
    }

    public void setAllowDeposit(boolean allowDeposit) {
        this.allowDeposit = allowDeposit;
        setChanged();
    }

    public boolean isAllowWithdraw() {
        return allowWithdraw;
    }

    public void setAllowWithdraw(boolean allowWithdraw) {
        this.allowWithdraw = allowWithdraw;
        setChanged();
    }

    public boolean isAllowAutocraft() {
        return allowAutocraft;
    }

    public void setAllowAutocraft(boolean allowAutocraft) {
        this.allowAutocraft = allowAutocraft;
        setChanged();
    }

    public int getLinkedColonyId() {
        return linkedColonyId;
    }

    @Nullable
    public BlockPos getLinkedWarehousePos() {
        return linkedWarehousePos;
    }

    public void setLinkedWarehouse(int colonyId, @Nullable BlockPos warehousePos) {
        this.linkedColonyId = colonyId;
        this.linkedWarehousePos = warehousePos;
        setChanged();
        markForUpdate();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeInt(linkedColonyId);
        data.writeBoolean(linkedWarehousePos != null);
        if (linkedWarehousePos != null) {
            data.writeBlockPos(linkedWarehousePos);
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int oldColony = this.linkedColonyId;
        BlockPos oldWh = this.linkedWarehousePos;
        this.linkedColonyId = data.readInt();
        boolean hasWh = data.readBoolean();
        this.linkedWarehousePos = hasWh ? data.readBlockPos() : null;
        return changed || oldColony != this.linkedColonyId || (oldWh == null ? this.linkedWarehousePos != null : !oldWh.equals(this.linkedWarehousePos));
    }

    public boolean isTerminalOnline() {
        return getMainNode().isOnline();
    }

    @Nullable
    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    public boolean isCrafting(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ColonyCraftingTracker.CraftingJobInfo job : craftingTracker.getActiveJobs()) {
            if (ItemStack.isSameItemSameComponents(job.getStack(), stack)
                    || (job.getStack().getItem() == stack.getItem())) {
                return true;
            }
        }
        for (PendingCalculation pending : pendingCalculations) {
            if (ItemStack.isSameItemSameComponents(pending.getStack(), stack)
                    || (pending.getStack().getItem() == stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> mainThreadTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public void queueCraftingRequest(@NotNull ItemStack stack, long amount, @NotNull String requesterName) {
        mainThreadTasks.add(() -> requestCrafting(stack, amount, requesterName));
    }

    public void requestCrafting(@NotNull ItemStack stack, long amount, @NotNull String requesterName) {
        AE2Colonies.LOGGER.info("Entering requestCrafting for {} x{} (requester: {})", stack, amount, requesterName);
        if (!isTerminalOnline() || !isAllowAutocraft() || stack.isEmpty()) {
            AE2Colonies.LOGGER.info("requestCrafting blocked: online={}, autocraft={}, isEmpty={}", isTerminalOnline(), isAllowAutocraft(), stack.isEmpty());
            return;
        }
        if (isCrafting(stack)) {
            AE2Colonies.LOGGER.info("requestCrafting blocked: isCrafting() returned true for {}", stack);
            return;
        }
        IGrid grid = getGrid();
        if (grid == null || level == null) {
            AE2Colonies.LOGGER.info("requestCrafting blocked: grid or level is null");
            return;
        }

        Future<ICraftingPlan> future = AE2IntegrationHelper.requestCraftingCalculation(
                level,
                grid,
                this,
                stack,
                amount
        );
        if (future != null) {
            pendingCalculations.add(new PendingCalculation(
                    future,
                    stack.copyWithCount((int) Math.min(amount, stack.getMaxStackSize())),
                    amount,
                    requesterName,
                    tickCounter
            ));
            AE2Colonies.LOGGER.info("Queued crafting calculation for {} x{} (by {})", stack, amount, requesterName);
        } else {
            AE2Colonies.LOGGER.info("requestCrafting future was null for {}", stack);
        }
    }

    public boolean canSynthesizeDOBlock(@NotNull ItemStack stack, int count) {
        if (!isTerminalOnline() || !isAllowAutocraft() || stack.isEmpty()) {
            return false;
        }
        if (!DomumOrnamentumHelper.isDOBlock(stack)) {
            return false;
        }
        IGrid grid = getGrid();
        if (grid == null || level == null) {
            return false;
        }
        if (grid.getActiveMachines(MEArchitectsCutterBlockEntity.class).isEmpty()) {
            return false;
        }

        DomumOrnamentumHelper.DOMaterialCost cost = DomumOrnamentumHelper.getMaterialCost(level, stack);
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

    public boolean synthesizeDOBlock(@NotNull ItemStack stack, int count) {
        if (!canSynthesizeDOBlock(stack, count)) {
            return false;
        }
        IGrid grid = getGrid();
        if (grid == null || level == null) {
            return false;
        }

        DomumOrnamentumHelper.DOMaterialCost cost = DomumOrnamentumHelper.getMaterialCost(level, stack);
        if (cost == null) {
            return false;
        }

        int batches = (int) Math.ceil((double) count / cost.getYield());
        List<ItemStack> extractedIngredients = new ArrayList<>();
        boolean success = true;

        for (ItemStack ingredient : cost.getIngredients()) {
            int needed = batches * ingredient.getCount();
            ItemStack toExtract = ingredient.copyWithCount(needed);
            ItemStack extracted = AE2IntegrationHelper.extractItem(grid, toExtract, getActionSource());
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
                AE2IntegrationHelper.insertItem(grid, rollback, getActionSource());
            }
            return false;
        }

        grid.getEnergyService().extractAEPower(
                20.0 * count,
                Actionable.MODULATE,
                PowerMultiplier.CONFIG
        );

        ItemStack synthesized = DomumOrnamentumHelper.synthesizeDOBlock(stack, count);
        AE2IntegrationHelper.insertItem(grid, synthesized, getActionSource());
        AE2Colonies.LOGGER.info("Synthesized DO Block {} x{} into AE2 storage", stack, count);
        return true;
    }

    // --- NBT Serialization ---

    @Override
    public void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("AllowDeposit", allowDeposit);
        tag.putBoolean("AllowWithdraw", allowWithdraw);
        tag.putBoolean("AllowAutocraft", allowAutocraft);
        tag.putInt("ColonyId", linkedColonyId);
        if (linkedWarehousePos != null) {
            tag.putInt("WhX", linkedWarehousePos.getX());
            tag.putInt("WhY", linkedWarehousePos.getY());
            tag.putInt("WhZ", linkedWarehousePos.getZ());
        }
        craftingTracker.writeToNBT(tag, provider);
    }

    @Override
    public void loadTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.loadTag(tag, provider);
        if (tag.contains("AllowDeposit")) allowDeposit = tag.getBoolean("AllowDeposit");
        if (tag.contains("AllowWithdraw")) allowWithdraw = tag.getBoolean("AllowWithdraw");
        if (tag.contains("AllowAutocraft")) allowAutocraft = tag.getBoolean("AllowAutocraft");
        if (tag.contains("ColonyId")) linkedColonyId = tag.getInt("ColonyId");
        if (tag.contains("WhX")) {
            linkedWarehousePos = new BlockPos(tag.getInt("WhX"), tag.getInt("WhY"), tag.getInt("WhZ"));
        }
        try {
            craftingTracker.readFromNBT(tag, provider, this);
        } catch (Exception e) {
            AE2Colonies.LOGGER.warn("Failed to load crafting tracker data for Colony Terminal at {}", worldPosition, e);
        }
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ae2colonies.colony_terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ColonyTerminalMenu(containerId, playerInventory, this);
    }
}
