package com.ae2colonies.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.ae2colonies.block.MEArchitectsCutterBlock;
import com.ae2colonies.domum.DomumOrnamentumHelper;
import com.ae2colonies.init.ModBlockEntities;
import com.ae2colonies.menu.MEArchitectsCutterMenu;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MEArchitectsCutterBlockEntity extends AENetworkedBlockEntity
        implements IActionHost, MenuProvider {

    public static final int SLOT_TEMPLATE = 0;
    public static final int SLOT_INPUT_1 = 1;
    public static final int SLOT_INPUT_2 = 2;
    public static final int SLOT_INPUT_3 = 3;
    public static final int SLOT_INPUT_4 = 4;
    public static final int SLOT_OUTPUT = 5;
    public static final int TOTAL_SLOTS = 6;

    public static final int MAX_PROGRESS = 20;

    private int progress = 0;
    private final IActionSource actionSource = IActionSource.ofMachine(this);

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        public int getSlotLimit(int slot) {
            if (slot == SLOT_TEMPLATE) {
                return 1;
            }
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == SLOT_OUTPUT) {
                return false;
            }
            if (slot == SLOT_TEMPLATE) {
                return DomumOrnamentumHelper.isDOBlock(stack);
            }
            return true;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> MAX_PROGRESS;
                case 2 -> isMachineOnline() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public MEArchitectsCutterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ME_ARCHITECTS_CUTTER.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(2.0)
                .setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getContainerData() {
        return containerData;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return MAX_PROGRESS;
    }

    public boolean isMachineOnline() {
        return getMainNode().isOnline();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    public IActionSource getActionSource() {
        return actionSource;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State state) {
        super.onMainNodeStateChanged(state);
        if (level != null && !level.isClientSide()) {
            boolean online = getMainNode().isOnline();
            BlockState currentState = getBlockState();
            if (currentState.hasProperty(MEArchitectsCutterBlock.ONLINE)
                    && currentState.getValue(MEArchitectsCutterBlock.ONLINE) != online) {
                level.setBlock(worldPosition, currentState.setValue(MEArchitectsCutterBlock.ONLINE, online), 3);
            }
        }
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        boolean online = getMainNode().isOnline();
        BlockState currentState = getBlockState();
        if (currentState.hasProperty(MEArchitectsCutterBlock.ONLINE)
                && currentState.getValue(MEArchitectsCutterBlock.ONLINE) != online) {
            level.setBlock(worldPosition, currentState.setValue(MEArchitectsCutterBlock.ONLINE, online), 3);
        }

        if (!online) {
            if (progress > 0) {
                progress = 0;
                setChanged();
            }
            return;
        }

        autoEjectOutput();

        ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
        if (!template.isEmpty() && DomumOrnamentumHelper.isDOBlock(template)) {
            List<ItemStack> inputs = new ArrayList<>(4);
            for (int i = 1; i <= 4; i++) {
                inputs.add(inventory.getStackInSlot(i));
            }

            ItemStack preview = DomumOrnamentumHelper.craftDOBlock(level, template, inputs);
            if (!preview.isEmpty()) {
                ItemStack currentOutput = inventory.getStackInSlot(SLOT_OUTPUT);
                boolean canFit = currentOutput.isEmpty()
                        || (ItemStack.isSameItemSameComponents(currentOutput, preview)
                        && currentOutput.getCount() + preview.getCount() <= currentOutput.getMaxStackSize());

                if (canFit) {
                    IGrid grid = getMainNode().getGrid();
                    if (grid != null) {
                        double extracted = grid.getEnergyService().extractAEPower(10.0, Actionable.MODULATE, PowerMultiplier.CONFIG);
                        if (extracted >= 9.0) {
                            progress++;
                            if (progress >= MAX_PROGRESS) {
                                progress = 0;
                                IMateriallyTexturedBlock mtb = DomumOrnamentumHelper.getDOBlock(template);
                                int compCount = (mtb != null) ? mtb.getComponents().size() : 1;
                                for (int i = 0; i < compCount && i < 4; i++) {
                                    ItemStack in = inventory.getStackInSlot(i + 1);
                                    if (!in.isEmpty()) {
                                        in.shrink(1);
                                    }
                                }

                                if (currentOutput.isEmpty()) {
                                    inventory.setStackInSlot(SLOT_OUTPUT, preview);
                                } else {
                                    currentOutput.grow(preview.getCount());
                                }
                                setChanged();
                                autoEjectOutput();
                            }
                            setChanged();
                            return;
                        }
                    }
                }
            }
        }

        if (progress > 0) {
            progress = 0;
            setChanged();
        }
    }

    private void autoEjectOutput() {
        ItemStack out = inventory.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty() || level == null) {
            return;
        }

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            IItemHandler neighborHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, dir.getOpposite());
            if (neighborHandler != null) {
                ItemStack remaining = ItemHandlerHelper.insertItem(neighborHandler, out, false);
                if (remaining.getCount() != out.getCount()) {
                    inventory.setStackInSlot(SLOT_OUTPUT, remaining);
                    setChanged();
                    if (remaining.isEmpty()) {
                        break;
                    }
                    out = remaining;
                }
            }
        }
    }

    public void dropInventoryContents(Level level, BlockPos pos) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return TOTAL_SLOTS;
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return inventory.getStackInSlot(slot);
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (slot == SLOT_OUTPUT) {
                    return stack;
                }
                if (slot == SLOT_TEMPLATE && !DomumOrnamentumHelper.isDOBlock(stack)) {
                    return stack;
                }
                return inventory.insertItem(slot, stack, simulate);
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (side != null && slot != SLOT_OUTPUT) {
                    return ItemStack.EMPTY;
                }
                return inventory.extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return inventory.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return inventory.isItemValid(slot, stack);
            }
        };
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("Progress", progress);

        ListTag list = new ListTag();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                Tag encoded = ItemStack.OPTIONAL_CODEC.encodeStart(
                        provider.createSerializationContext(NbtOps.INSTANCE), stack
                ).getOrThrow();
                itemTag.put("Item", encoded);
                list.add(itemTag);
            }
        }
        tag.put("Inventory", list);
    }

    @Override
    public void loadTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.loadTag(tag, provider);
        if (tag.contains("Progress")) {
            progress = tag.getInt("Progress");
        }

        if (tag.contains("Inventory", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Inventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getByte("Slot");
                if (slot >= 0 && slot < inventory.getSlots()) {
                    ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(
                            provider.createSerializationContext(NbtOps.INSTANCE), itemTag.get("Item")
                    ).result().orElse(ItemStack.EMPTY);
                    inventory.setStackInSlot(slot, stack);
                }
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ae2colonies.me_architects_cutter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MEArchitectsCutterMenu(containerId, playerInventory, this);
    }
}