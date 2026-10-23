package com.shouyun.dimensioncollapse.block;

import com.shouyun.dimensioncollapse.collapse.TwoDimensional;
import com.shouyun.dimensioncollapse.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What remains of a block after the collapse: a sheet of colour on the collapse plane.
 * COLOR is the original block's map colour id, SHADE a {@link MapColor.Brightness} id encoding
 * whether the matter came from above or below the plane.
 * Untouchable from three dimensions; only two-dimensional players can peel it off as flat matter.
 */
public class FlattenedBlock extends Block {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 63);
    public static final IntegerProperty SHADE = IntegerProperty.create("shade", 0, 3);

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final MapColor.Brightness[] BRIGHTNESS = MapColor.Brightness.values();
    private static final float TWO_DIMENSIONAL_BREAK_PROGRESS = 0.05f;

    public FlattenedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLOR, 0).setValue(SHADE, MapColor.Brightness.NORMAL.id));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, SHADE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return TwoDimensional.is(player) ? TWO_DIMENSIONAL_BREAK_PROGRESS : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && TwoDimensional.is(player) && !player.isCreative()) {
            popResource(level, pos, new ItemStack(ModItems.FLAT_MATTER.get()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public BlockState paint(MapColor color, MapColor.Brightness brightness) {
        return defaultBlockState().setValue(COLOR, color.id).setValue(SHADE, brightness.id);
    }

    public static int rgb(BlockState state) {
        int col = MapColor.byId(state.getValue(COLOR)).col;
        int modifier = BRIGHTNESS[state.getValue(SHADE)].modifier;
        int r = (col >> 16 & 0xFF) * modifier / 255;
        int g = (col >> 8 & 0xFF) * modifier / 255;
        int b = (col & 0xFF) * modifier / 255;
        return r << 16 | g << 8 | b;
    }
}
