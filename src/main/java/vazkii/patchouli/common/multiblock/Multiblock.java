package vazkii.patchouli.common.multiblock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.IStateMatcher;
import vazkii.patchouli.common.util.RotationUtil;

public class Multiblock implements IMultiblock {

	final String[][] pattern;

	public ResourceLocation res;
	public IStateMatcher[][][] stateTargets;
	public int sizeX, sizeY, sizeZ;
	public int offX, offY, offZ;
	public int viewOffX, viewOffY, viewOffZ;
	int centerX, centerY, centerZ;
	boolean symmetrical;

	public Multiblock(String[][] pattern, Object... targets) {
		this.pattern = pattern;
		build(targets, getPatternDimensions());
	}

	@Override
	public Multiblock offset(int x, int y, int z) {
		return setOffset(offX + x, offY + y, offZ + z);
	}

	public Multiblock setOffset(int x, int y, int z) {
		offX = x;
		offY = y;
		offZ = z;
		return setViewOffset(x, y, z);
	}

	void setViewOffset() {
		setViewOffset(offX, offY, offZ);
	}

	@Override
	public Multiblock offsetView(int x, int y, int z) {
		return setViewOffset(viewOffX + x, viewOffY + y, viewOffZ + z);
	}

	public Multiblock setViewOffset(int x, int y, int z) {
		viewOffX = x;
		viewOffY = y;
		viewOffZ = z;
		return this;
	}

	@Override
	public Multiblock setSymmetrical(boolean symmetrical) {
		this.symmetrical = symmetrical;
		return this;
	}

	@Override
	public Multiblock setResourceLocation(ResourceLocation res) {
		this.res = res;
		return this;
	}

	@Override
	public void place(World world, BlockPos pos, Rotation rotation) {
		BlockPos start = pos.add(RotationUtil.x(rotation, -offX, -offZ), -offY, RotationUtil.z(rotation, -offX, -offZ));
		for(int x = 0; x < sizeX; x++)
			for(int y = 0; y < sizeY; y++)
				for(int z = 0; z < sizeZ; z++) {
					BlockPos placePos = start.add(RotationUtil.x(rotation, x, z), y, RotationUtil.z(rotation, x, z));
					IBlockState targetState = stateTargets[x][y][z].getDisplayedState().withRotation(rotation);
					Block targetBlock = targetState.getBlock();
					if(!targetBlock.isAir(targetState, world, placePos) && targetBlock.canPlaceBlockAt(world, placePos) && world.getBlockState(placePos).getBlock().isReplaceable(world, placePos))
						world.setBlockState(placePos, targetState);
				}
	}

	@Override
	public void forEach(World world, BlockPos pos, Rotation rotation, char c, Consumer<BlockPos> action) {
		forEachMatcher(world, pos, rotation, c, (start, actionPos, x, y, z, ch, matcher) -> {
		    action.accept(actionPos);
		    return true;
		});
	}

	@Override
	public boolean forEachMatcher(World world, BlockPos pos, Rotation rotation, char c, MatcherAcceptor acceptor){
		BlockPos start = pos.add(RotationUtil.x(rotation, -offX, -offZ), -offY, RotationUtil.z(rotation, -offX, -offZ));
		for(int x = 0; x < sizeX; x++)
			for(int y = 0; y < sizeY; y++)
				for(int z = 0; z < sizeZ; z++) {
					char currC = pattern[y][x].charAt(z);
					if(c == 0 || c == currC) {
						BlockPos actionPos = start.add(RotationUtil.x(rotation, x, z), y, RotationUtil.z(rotation, x, z));
						if(!acceptor.accepts(start, actionPos, x, y, z, currC, this.stateTargets[x][y][z])){
							return false;
						}
					}
				}
        return true;
	}

	@Override
	public boolean validate(World world, BlockPos pos) {
		if(symmetrical)
			return validate(world, pos, Rotation.NONE);

		else return validate(world, pos, Rotation.NONE)
			|| validate(world, pos, Rotation.CLOCKWISE_90)
			|| validate(world, pos, Rotation.CLOCKWISE_180)
			|| validate(world, pos, Rotation.COUNTERCLOCKWISE_90);
	}


	protected boolean validate(World world, BlockPos pos, Rotation rotation) {
		BlockPos start = pos.add(RotationUtil.x(rotation, -offX, -offZ), -offY, RotationUtil.z(rotation, -offX, -offZ));
		if(!test(world, start, centerX, centerY, centerZ, rotation))
			return false;

		for(int x = 0; x < sizeX; x++)
			for(int y = 0; y < sizeY; y++)
				for(int z = 0; z < sizeZ; z++)
					if(!test(world, start, x, y, z, rotation))
						return false;

		return true;
	}

	@Override
	public boolean test(World world, BlockPos start, int x, int y, int z, Rotation rotation) {
		BlockPos checkPos = start.add(RotationUtil.x(rotation, x, z), y, RotationUtil.z(rotation, x, z));
		Predicate<IBlockState> pred = stateTargets[x][y][z].getStatePredicate();
		IBlockState state = world.getBlockState(checkPos).withRotation(RotationUtil.fixHorizontal(rotation));

		return pred.test(state);
	}

	void build(Object[] targets, int[] dimensions) {
		if(targets.length % 2 == 1)
			throw new IllegalArgumentException("Illegal argument length for targets array " + targets.length);

		Map<Character, IStateMatcher> stateMap = new HashMap();
		for(int i = 0; i < targets.length / 2; i++) {
			char c = (Character) targets[i * 2];
			Object o = targets[i * 2 + 1];
			IStateMatcher state = null;

			if(o instanceof Block)
				state = StateMatcher.fromBlockLoose((Block) o);
			else if(o instanceof IBlockState)
				state = StateMatcher.fromState((IBlockState) o);
			else if(o instanceof IStateMatcher)
				state = (IStateMatcher) o;
			else throw new IllegalArgumentException("Invalid target " + o);

			stateMap.put(c, state);
		}

		if(!stateMap.containsKey('_'))
			stateMap.put('_', StateMatcher.ANY);
		if(!stateMap.containsKey(' '))
			stateMap.put(' ', StateMatcher.AIR);
		if(!stateMap.containsKey('0'))
			stateMap.put('0', StateMatcher.AIR);

		boolean foundCenter = false;

		sizeX = dimensions[1];
		sizeY = dimensions[0];
		sizeZ = dimensions[2];
		stateTargets = new IStateMatcher[dimensions[1]][dimensions[0]][dimensions[2]];
		for(int y = 0; y < dimensions[0]; y++)
			for(int x = 0; x < dimensions[1]; x++)
				for(int z = 0; z < dimensions[2]; z++) {
					char c = pattern[y][x].charAt(z);
					if(!stateMap.containsKey(c))
						throw new IllegalArgumentException("Character " + c + " isn't mapped");

					IStateMatcher matcher = stateMap.get(c);
					if(c == '0') {
						if(foundCenter)
							throw new IllegalArgumentException("A structure can't have two centers");
						foundCenter = true;
						offX = centerX = x;
						offY = centerY = sizeY - y - 1;
						offZ = centerZ = z;
						setViewOffset();
					}

					stateTargets[x][sizeY - y - 1][z] = matcher;
				}

		if(!foundCenter)
			throw new IllegalArgumentException("A structure can't have no center");
	}

	int[] getPatternDimensions() {
		int expectedLenX = -1;
		int expectedLenZ = -1;
		for(String[] arr : pattern) {
			if(expectedLenX == -1)
				expectedLenX = arr.length;
			if(arr.length != expectedLenX)
				throw new IllegalArgumentException("Inconsistent array length. Expected" + expectedLenX + ", got " + arr.length);

			for(String s : arr) {
				if(expectedLenZ == -1)
					expectedLenZ = s.length();
				if(s.length() != expectedLenZ)
					throw new IllegalArgumentException("Inconsistent array length. Expected" + expectedLenX + ", got " + arr.length);
			}
		}

		return new int[] { pattern.length, expectedLenX, expectedLenZ };
	}

	@Override
	public boolean isSymmetrical() {
		return symmetrical;
	}

}
