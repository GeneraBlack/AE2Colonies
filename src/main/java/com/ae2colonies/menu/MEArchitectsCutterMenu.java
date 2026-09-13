package com.ae2colonies.menu;

import com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity;
import com.ae2colonies.domum.DomumOrnamentumHelper;
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
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MEArchitectsCutterMenu extends AbstractContainerMenu {

    @Nullable
    private final MEArchitectsCutterBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    // Client constructor
    public MEArchitectsCutterMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntityFromBuf(playerInv, buf), new SimpleContainerData(3));
    }

    // Server constructor
    public MEArchitectsCutterMenu(int containerId, Inventory playerInv, MEArchitectsCutterBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity, blockEntity.getContainerData());
    }

    private MEArchitectsCutterMenu(
            int containerId,
            Inventory playerInv,
            @Nullable MEArchitectsCutterBlockEntity blockEntity,
            ContainerData data
    ) {
        super(ModMenuTypes.ME_ARCHITECTS_CUTTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.access = blockEntity != null && blockEntity.getLevel() != null
                ? ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos())
                : ContainerLevelAccess.NULL;

        addDataSlots(data);

        if (blockEntity != null) {
            // Slot 0: Template slot
            this.addSlot(new SlotItemHandler(blockEntity.getInventory(), MEArchitectsCutterBlockEntity.SLOT_TEMPLATE, 20, 35) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return DomumOrnamentumHelper.isDOBlock(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });

            // Slots 1-4: Material input slots (2x2 grid)
            this.addSlot(new SlotItemHandler(blockEntity.getInventory(), MEArchitectsCutterBlockEntity.SLOT_INPUT_1, 56, 26));
            this.addSlot(new SlotItemHandler(blockEntity.getInventory(), MEArchitectsCutterBlockEntity.SLOT_INPUT_2, 74, 26));
            this.addSlot(new SlotItemHandler(blockEntity.getInventory(), MEArchitectsCutterBlockEntity.SLOT_INPUT_3, 56, 44));
            this.addSlot(new SlotItemHandler(blockEntity.getInventory(), MEArchitectsCutterBlockEntity.SLOT_INPUT_4, 74, 44));

            // Slot 5: Output slot
            this.addSlot(new SlotItemHandler(blockEntity.getInventory(), MEArchitectsCutterBlockEntity.SLOT_OUTPUT, 126, 35) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return false;
                }
            });
        }

        // Player inventory (3 rows x 9 columns)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (9 columns)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    private static MEArchitectsCutterBlockEntity getBlockEntityFromBuf(Inventory playerInv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level().getBlockEntity(pos) instanceof MEArchitectsCutterBlockEntity be) {
            return be;
        }
        return null;
    }

    @Nullable
    public MEArchitectsCutterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    public boolean isMachineOnline() {
        return data.get(2) != 0;
    }

    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            // From machine slots (0..5) to player inventory (6..41)
            if (index < 6) {
                if (!this.moveItemStackTo(stackInSlot, 6, 42, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, itemstack);
            }
            // From player inventory to machine slots
            else {
                if (DomumOrnamentumHelper.isDOBlock(stackInSlot)) {
                    // Try template slot first if empty
                    if (!this.slots.get(0).hasItem()) {
                        ItemStack singleCopy = stackInSlot.copyWithCount(1);
                        this.slots.get(0).set(singleCopy);
                        stackInSlot.shrink(1);
                        slot.setChanged();
                        return itemstack;
                    }
                    // Otherwise try inputs 1..4
                    if (!this.moveItemStackTo(stackInSlot, 1, 5, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Try inputs 1..4
                    if (!this.moveItemStackTo(stackInSlot, 1, 5, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stackInSlot);
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(this.access, player, ModBlocks.ME_ARCHITECTS_CUTTER.get());
    }
}