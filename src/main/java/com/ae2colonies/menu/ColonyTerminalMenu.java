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

    @Nullable
    private final ColonyTerminalBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    // Client constructor
    public ColonyTerminalMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntityFromBuf(playerInv, buf), new SimpleContainerData(4));
    }

    // Server constructor
    public ColonyTerminalMenu(int containerId, Inventory playerInv, ColonyTerminalBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> blockEntity.isAllowDeposit() ? 1 : 0;
                    case 1 -> blockEntity.isAllowWithdraw() ? 1 : 0;
                    case 2 -> blockEntity.isAllowAutocraft() ? 1 : 0;
                    case 3 -> blockEntity.isTerminalOnline() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> blockEntity.setAllowDeposit(value != 0);
                    case 1 -> blockEntity.setAllowWithdraw(value != 0);
                    case 2 -> blockEntity.setAllowAutocraft(value != 0);
                }
            }

            @Override
            public int getCount() {
                return 4;
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
        return data.get(0) != 0;
    }

    public boolean isAllowWithdraw() {
        return data.get(1) != 0;
    }

    public boolean isAllowAutocraft() {
        return data.get(2) != 0;
    }

    public boolean isTerminalOnline() {
        return data.get(3) != 0;
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
