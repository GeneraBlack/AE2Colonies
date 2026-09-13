package com.ae2colonies.block;

import appeng.block.AEBaseEntityBlock;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.colony.WarehouseMEBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import com.ae2colonies.init.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ColonyTerminalBlock extends AEBaseEntityBlock<ColonyTerminalBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public ColonyTerminalBlock() {
        super(metalProps().strength(2.2f, 11.0f));
        setBlockEntity(ColonyTerminalBlockEntity.class, null, null, null);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ONLINE, false));
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, ColonyTerminalBlockEntity terminal) {
        return state.setValue(ONLINE, terminal.isTerminalOnline());
    }

    @Override
    public BlockEntityType<ColonyTerminalBlockEntity> getBlockEntityType() {
        return ModBlockEntities.COLONY_TERMINAL.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ONLINE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ONLINE, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColonyTerminalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ColonyTerminalBlockEntity terminal) {
                if (terminal.getLinkedWarehousePos() == null) {
                    WarehouseMEBridge.registerTerminal(terminal);
                }
                player.openMenu(terminal, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ColonyTerminalBlockEntity terminal) {
                WarehouseMEBridge.registerTerminal(terminal);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ColonyTerminalBlockEntity terminal) {
                    WarehouseMEBridge.unregisterTerminal(terminal);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
