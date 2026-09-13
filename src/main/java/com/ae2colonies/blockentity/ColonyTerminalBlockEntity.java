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
import com.ae2colonies.ae2.ColonyCraftingTracker;
import com.ae2colonies.block.ColonyTerminalBlock;
import com.ae2colonies.colony.WarehouseMEBridge;
import com.ae2colonies.init.ModBlockEntities;
import com.ae2colonies.menu.ColonyTerminalMenu;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ColonyTerminalBlockEntity extends AENetworkedBlockEntity
        implements ICraftingRequester, IActionHost, MenuProvider {

    private boolean allowDeposit = true;
    private boolean allowWithdraw = true;
    private boolean allowAutocraft = true;

    private int linkedColonyId = -1;
    @Nullable
    private BlockPos linkedWarehousePos = null;

    private final ColonyCraftingTracker craftingTracker = new ColonyCraftingTracker();
    private final IActionSource actionSource = IActionSource.ofMachine(this);

    public ColonyTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLONY_TERMINAL.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(1.5)
                .setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public void onReady() {
        super.onReady();
        WarehouseMEBridge.registerTerminal(this);
    }

    @Override
    public void setRemoved() {
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
    }

    public boolean isTerminalOnline() {
        return getMainNode().isOnline();
    }

    @Nullable
    public IGrid getGrid() {
        return getMainNode().getGrid();
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
        craftingTracker.readFromNBT(tag, provider, this);
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
