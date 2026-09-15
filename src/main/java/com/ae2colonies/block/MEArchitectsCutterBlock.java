package com.ae2colonies.block;

import appeng.api.orientation.IOrientableBlock;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity;
import com.ae2colonies.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MEArchitectsCutterBlock extends AEBaseEntityBlock<MEArchitectsCutterBlockEntity> implements IOrientableBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public MEArchitectsCutterBlock() {
        super(metalProps().strength(2.2f, 11.0f));
        setBlockEntity(MEArchitectsCutterBlockEntity.class, null, null, null);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ONLINE, false));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, MEArchitectsCutterBlockEntity cutter) {
        return state.setValue(ONLINE, cutter.isMachineOnline());
    }

    @Override
    public BlockEntityType<MEArchitectsCutterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ME_ARCHITECTS_CUTTER.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ONLINE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state != null ? state.setValue(ONLINE, false) : defaultBlockState().setValue(ONLINE, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MEArchitectsCutterBlockEntity(pos, state);
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
            if (be instanceof MEArchitectsCutterBlockEntity cutter) {
                player.openMenu(cutter, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MEArchitectsCutterBlockEntity cutter) {
                    cutter.dropInventoryContents(level, pos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}