package com.ae2colonies.menu;

import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.init.ModBlocks;
import com.ae2colonies.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ColonyTerminalMenu extends AbstractContainerMenu {

    public static final int BUTTON_TOGGLE_DEPOSIT = 0;
    public static final int BUTTON_TOGGLE_WITHDRAW = 1;
    public static final int BUTTON_TOGGLE_AUTOCRAFT = 2;

    public static final int DATA_ALLOW_DEPOSIT = 0;
    public static final int DATA_ALLOW_WITHDRAW = 1;
    public static final int DATA_ALLOW_AUTOCRAFT = 2;
    public static final int DATA_IS_ONLINE = 3;
    public static final int DATA_HAS_WAREHOUSE = 4;
    public static final int DATA_WH_X_LOW = 5;
    public static final int DATA_WH_X_HIGH = 6;
    public static final int DATA_WH_Y = 7;
    public static final int DATA_WH_Z_LOW = 8;
    public static final int DATA_WH_Z_HIGH = 9;
    public static final int DATA_ACTIVE_CRAFTS = 10;
    public static final int TOTAL_DATA_COUNT = 11;

    @Nullable
    private final ColonyTerminalBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    // Client constructor
    public ColonyTerminalMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntityFromBuf(playerInv, buf), new SimpleContainerData(TOTAL_DATA_COUNT));
    }

    // Server constructor
    public ColonyTerminalMenu(int containerId, Inventory playerInv, ColonyTerminalBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity, new ContainerData() {
            @Override
            public int get(int index) {
                BlockPos wh = blockEntity != null ? blockEntity.getLinkedWarehousePos() : null;
                return switch (index) {
                    case DATA_ALLOW_DEPOSIT -> (blockEntity != null && blockEntity.isAllowDeposit()) ? 1 : 0;
                    case DATA_ALLOW_WITHDRAW -> (blockEntity != null && blockEntity.isAllowWithdraw()) ? 1 : 0;
                    case DATA_ALLOW_AUTOCRAFT -> (blockEntity != null && blockEntity.isAllowAutocraft()) ? 1 : 0;
                    case DATA_IS_ONLINE -> (blockEntity != null && blockEntity.isTerminalOnline()) ? 1 : 0;
                    case DATA_HAS_WAREHOUSE -> wh != null ? 1 : 0;
                    case DATA_WH_X_LOW -> wh != null ? (short) (wh.getX() & 0xFFFF) : 0;
                    case DATA_WH_X_HIGH -> wh != null ? (short) ((wh.getX() >> 16) & 0xFFFF) : 0;
                    case DATA_WH_Y -> wh != null ? (short) wh.getY() : 0;
                    case DATA_WH_Z_LOW -> wh != null ? (short) (wh.getZ() & 0xFFFF) : 0;
                    case DATA_WH_Z_HIGH -> wh != null ? (short) ((wh.getZ() >> 16) & 0xFFFF) : 0;
                    case DATA_ACTIVE_CRAFTS -> blockEntity != null ? blockEntity.getCraftingTracker().getActiveJobs().size() : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (blockEntity != null) {
                    switch (index) {
                        case DATA_ALLOW_DEPOSIT -> blockEntity.setAllowDeposit(value != 0);
                        case DATA_ALLOW_WITHDRAW -> blockEntity.setAllowWithdraw(value != 0);
                        case DATA_ALLOW_AUTOCRAFT -> blockEntity.setAllowAutocraft(value != 0);
                    }
                }
            }

            @Override
            public int getCount() {
                return TOTAL_DATA_COUNT;
            }
        });
    }

    private ColonyTerminalMenu(int containerId, Inventory playerInv, @Nullable ColonyTerminalBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.COLONY_TERMINAL.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.access = blockEntity != null && blockEntity.getLevel() != null
                ? ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos())
                : ContainerLevelAccess.NULL;

        addDataSlots(data);

        // Player Inventory slots
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    private static ColonyTerminalBlockEntity getBlockEntityFromBuf(Inventory playerInv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level().getBlockEntity(pos) instanceof ColonyTerminalBlockEntity be) {
            return be;
        }
        return null;
    }

    @Nullable
    public ColonyTerminalBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isAllowDeposit() {
        return data.get(DATA_ALLOW_DEPOSIT) != 0;
    }

    public boolean isAllowWithdraw() {
        return data.get(DATA_ALLOW_WITHDRAW) != 0;
    }

    public boolean isAllowAutocraft() {
        return data.get(DATA_ALLOW_AUTOCRAFT) != 0;
    }

    public boolean isTerminalOnline() {
        return data.get(DATA_IS_ONLINE) != 0;
    }

    public boolean hasLinkedWarehouse() {
        return data.get(DATA_HAS_WAREHOUSE) != 0;
    }

    @Nullable
    public BlockPos getLinkedWarehousePos() {
        if (!hasLinkedWarehouse()) {
            return null;
        }
        int xLow = data.get(DATA_WH_X_LOW);
        int xHigh = data.get(DATA_WH_X_HIGH);
        int x = (xHigh << 16) | (xLow & 0xFFFF);

        int y = data.get(DATA_WH_Y);

        int zLow = data.get(DATA_WH_Z_LOW);
        int zHigh = data.get(DATA_WH_Z_HIGH);
        int z = (zHigh << 16) | (zLow & 0xFFFF);

        return new BlockPos(x, y, z);
    }

    public int getActiveCraftsCount() {
        return data.get(DATA_ACTIVE_CRAFTS);
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (blockEntity != null && !player.level().isClientSide()) {
            switch (id) {
                case BUTTON_TOGGLE_DEPOSIT -> blockEntity.setAllowDeposit(!blockEntity.isAllowDeposit());
                case BUTTON_TOGGLE_WITHDRAW -> blockEntity.setAllowWithdraw(!blockEntity.isAllowWithdraw());
                case BUTTON_TOGGLE_AUTOCRAFT -> blockEntity.setAllowAutocraft(!blockEntity.isAllowAutocraft());
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(this.access, player, ModBlocks.COLONY_TERMINAL.get());
    }
}
