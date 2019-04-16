package vazkii.quark.experimental.features;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockPistonStructureHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import vazkii.quark.automation.feature.PistonsMoveTEs;
import vazkii.quark.base.module.Feature;
import vazkii.quark.base.module.ModuleLoader;

import java.util.*;

public class CollateralPistonMovement extends Feature {

	public static Map<ResourceLocation, MoveAction> blockActions;

	@Override
	public void setupConfig() {
		blockActions = new HashMap();
		
		String desc = "An array of actions to apply to blocks. Each element is in the following format:\n"
				+ "blockid=action\n"
				+ "Where blockid is the ID of the block to apply the action to (e.g. minecraft:stone)\n"
				+ "And action is one of the following:\n"
				+ "above - moves all equal blocks above it\n"
				+ "below - moves all equal blocks below it\n"
				+ "above_below - moves all equal blocks above and below it\n"
				+ "above_edge - moves all equal blocks above it, and one more block of any type that's above the stack\n"
				+ "below_edge - moves all equal blocks below it, and one more block of any type that's below the stack\n"
				+ "above_below_edge - moves all equal blocks above and below it, and one more block of any type that's above or below the stack\n"
				+ "directional - moves the block its facing to (only works on directional blocks)\n"
				+ "sides - moves the blocks on all adjacent sides\n"
				+ "horiozntals - moves the blocks on all adjacent horizontal sides\n"
				+ "verticals - moves the blocks on all adjacent vertical sides";
		
		String[] actionArr = loadPropStringList("Actions", desc, new String[] {
				"quark:chain=below_edge"
		});
		
		for(String s : actionArr) {
			String[] toks = s.split("=");
			if(toks.length == 2) {
				String res = toks[0];
				String actionStr = toks[1];
				
				MoveAction action = null;
				switch(actionStr) {
				case "above": action = CollateralPistonMovement::moveAllAbove; break;
				case "below": action = CollateralPistonMovement::moveAllBelow; break;
				case "above_below": action = CollateralPistonMovement::moveAllAboveBelow; break;
				
				case "above_edge": action = CollateralPistonMovement::moveAllAboveAndEdge; break;
				case "below_edge": action = CollateralPistonMovement::moveAllAboveAndEdge; break;
				case "above_below_edge": action = CollateralPistonMovement::moveAllAboveBelowAndEdge; break;
				
				case "directional": action = CollateralPistonMovement::moveNextDirectional; break;
				case "sides": action = CollateralPistonMovement::moveSides; break;
				case "horizontals": action = CollateralPistonMovement::moveHorizontals; break;
				case "verticals": action = CollateralPistonMovement::moveVerticals; break;
				}
				
				if(action != null)
					blockActions.put(new ResourceLocation(res), action);
			}
		}
	}
	
	public static void applyCollateralMovements(World world, BlockPos sourcePos, BlockPistonStructureHelper helper, EnumFacing facing, boolean extending) {
		if(!ModuleLoader.isFeatureEnabled(CollateralPistonMovement.class))
			return;

		List<BlockPos> moveList = helper.getBlocksToMove();
		List<BlockPos> additions = new LinkedList<>();
		
		for(BlockPos move : moveList) {
			IBlockState moveState = world.getBlockState(move);
			Block moveBlock = moveState.getBlock();
			MoveAction action = blockActions.get(moveBlock.getRegistryName());
			if(action == null)
				continue;
			
			action.add(world, move, moveState, facing, extending, additions);
		}
		
		for(BlockPos add : additions) {
			BlockPos check = add.offset(facing);
			if(moveList.contains(check) || additions.contains(check))
				continue;
			
			IBlockState checkState = world.getBlockState(check);
			if(checkState.getBlock().isAir(checkState, world, check) || checkState.getBlock().isReplaceable(world, check))
				continue;
			
			return;
		}
		
		moveList.addAll(additions);
	}
	
	private static BlockPos moveAllEqualSide(World world, BlockPos pos, IBlockState state, EnumFacing side, List<BlockPos> list) {
		BlockPos curr = pos.offset(side);
		IBlockState stateAt = world.getBlockState(curr);
		
		while(stateAt.equals(state)) {
			if(!canMove(stateAt, world, curr))
				return curr;
			
			list.add(curr);
			curr = curr.offset(side);
			stateAt = world.getBlockState(curr);
		}
		
		return curr;
	}
	
	private static void moveAllEqualSideAndOneMore(World world, BlockPos pos, IBlockState state, EnumFacing side, List<BlockPos> list) {
		BlockPos edge = moveAllEqualSide(world, pos, state, side, list);
		IBlockState edgeState = world.getBlockState(edge);
		if(canMove(edgeState, world, edge))
			list.add(edge);
	}
	
	private static void moveAllAbove(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveAllEqualSide(world, pos, state, EnumFacing.UP, list);
	}
	
	private static void moveAllBelow(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveAllEqualSide(world, pos, state, EnumFacing.DOWN, list);
	}
	
	private static void moveAllAboveBelow(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveAllEqualSide(world, pos, state, EnumFacing.UP, list);
		moveAllEqualSide(world, pos, state, EnumFacing.DOWN, list);
	}

	private static void moveAllAboveAndEdge(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveAllEqualSideAndOneMore(world, pos, state, EnumFacing.UP, list);
	}
	
	private static void moveAllBelowAndEdge(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveAllEqualSideAndOneMore(world, pos, state, EnumFacing.DOWN, list);
	}
	
	private static void moveAllAboveBelowAndEdge(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveAllEqualSideAndOneMore(world, pos, state, EnumFacing.UP, list);
		moveAllEqualSideAndOneMore(world, pos, state, EnumFacing.DOWN, list);
	}
	
	private static void moveNextDirectional(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		EnumFacing direction = getStateFacing(state);
		if(direction != null)
			moveSideIterable(world, pos, facing, list, new EnumFacing[] { direction });
	}
	
	private static void moveSides(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveSideIterable(world, pos, facing, list, EnumFacing.VALUES);
	}
	
	private static void moveHorizontals(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveSideIterable(world, pos, facing, list, EnumFacing.HORIZONTALS);
	}
	
	private static void moveVerticals(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list) {
		moveSideIterable(world, pos, facing, list, new EnumFacing[] { EnumFacing.UP, EnumFacing.DOWN });
	}
	
	private static void moveSideIterable(World world, BlockPos pos, EnumFacing facing, List<BlockPos> list, EnumFacing[] directions) {
		for(EnumFacing direction : directions)
			if(direction != facing.getOpposite()) {
				BlockPos nextPos = pos.offset(direction);
				IBlockState nextState = world.getBlockState(nextPos);
				if(canMove(nextState, world, nextPos))
					list.add(nextPos);
			}
	}
	
	private static boolean canMove(IBlockState state, World world, BlockPos pos) { // TODO change to isAir
		Block block = state.getBlock();
		if(block == Blocks.PISTON || block == Blocks.STICKY_PISTON)
			return !state.getValue(BlockPistonBase.EXTENDED);
			
		return !block.isAir(state, world, pos) && state.getMobilityFlag() == EnumPushReaction.NORMAL && (!block.hasTileEntity() || !PistonsMoveTEs.shouldMoveTE(true, state));
	}
	
	private static EnumFacing getStateFacing(IBlockState state) {
		Collection<IProperty<?>> props = state.getPropertyKeys();
		for(IProperty prop : props)
			if(prop.getName().equals("facing")) {
				Object obj = state.getValue(prop);
				if(obj instanceof EnumFacing)
					return (EnumFacing) obj;
			}
		
		return null;
	}

	private static interface MoveAction {
		void add(World world, BlockPos pos, IBlockState state, EnumFacing facing, boolean extending, List<BlockPos> list);
	}
	
}
