package net.teamterminus.machineessentials.network;

import com.llamalad7.mixinextras.lib.apache.commons.ArrayUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.world.World;
import net.modificationstation.stationapi.api.util.math.Direction;
import net.modificationstation.stationapi.api.util.math.Vec3i;
import net.teamterminus.machineessentials.MachineEssentials;
import net.teamterminus.machineessentials.util.IConduitTile;


import java.util.*;

public class NetworkWalker<T extends NetworkComponentTile> {

	protected NetworkWalker<T> root;
	private final World world;
	private Set<T> walkedConduits;
	private final List<Direction> nextConduitDirections = new ArrayList<>(Direction.values().length-1);
	private final List<T> nextConduits = new ArrayList<>(Direction.values().length-1);
	private List<NetworkWalker<T>> walkers;
	private Vec3i currentPos;
	private T currentConduit;
	private Direction from = null;
	private int walkedBlocks;
	private boolean used;
	private boolean running;
	private boolean failed = false;

	private T[] conduits;
	private final List<NetworkPath> routes;

	public NetworkWalker(World world, Vec3i source, int walkedBlocks, List<NetworkPath> routes){
		this.world = world;
		this.walkedBlocks = walkedBlocks;
		this.currentPos = source;
		this.root = this;
		this.routes = routes;
	}

	public static <T extends NetworkComponentTile> List<NetworkPath> createNetworkPaths(World world, Vec3i source){
		if(world.getBlockEntity(source.getX(), source.getY(), source.getZ()) instanceof NetworkComponentTile){
			NetworkWalker<T> walker = new NetworkWalker<>(world,source,1,new ArrayList<>());
			walker.traverse();
			return walker.isFailed() ? null : walker.routes;
		}
		return null;
	}

	protected NetworkWalker<T> createSubWalker(World world, Direction nextDir, Vec3i nextPos, int walkedBlocks){
		NetworkWalker<T> subWalker = new NetworkWalker<>(world, nextPos, walkedBlocks, routes);
		subWalker.conduits = conduits;
		return subWalker;
	}

	public void traverse(){
		traverse(32768);
	}

	public void traverse(int max){
		if(used){
			throw new IllegalStateException("Walker already used!");
		}
		root = this;
		walkedConduits = new HashSet<>();
		int i = 0;
		running = true;
		//runs purely on side effects
		//noinspection StatementWithEmptyBody
		while(running && !walk() && i++ < max);
		running = false;
		walkedConduits = null;
		if(i >= max){
			MachineEssentials.LOGGER.error("Walker reached maximum amount of walks: {}", i);
		}
		used = true;
	}

	private boolean walk(){
		if(walkers == null){
			if(!checkPos()){
				this.root.failed = true;
				return true;
			}

			if(nextConduitDirections.isEmpty()){
				return true;
			}
			if(nextConduitDirections.size() == 1){
				currentPos = nextConduits.get(0).getPosition();
				currentConduit = nextConduits.get(0);
				from = nextConduitDirections.get(0).getOpposite();
				walkedBlocks++;
				return !isRunning();
			}

			walkers = new ArrayList<>();
			for (int i = 0; i < nextConduitDirections.size(); i++) {
				Direction direction = nextConduitDirections.get(i);
				NetworkWalker<T> walker = createSubWalker(world, direction, currentPos.add(new Vec3i(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ())), walkedBlocks + 1);
				walker.root = root;
				walker.currentConduit = nextConduits.get(i);
				walker.from = direction.getOpposite();
				walkers.add(walker);
			}
		}
		Iterator<NetworkWalker<T>> iterator = walkers.iterator();
		while(iterator.hasNext()){
			NetworkWalker<T> next = iterator.next();
			if(next.walk()){
				onRemoveSubWalker(next);
				iterator.remove();
			}
		}

		return !isRunning() || walkers.isEmpty();
	}


	private boolean checkPos(){
		nextConduitDirections.clear();
		nextConduits.clear();
		if(currentConduit == null){
			BlockEntity thisConduit = world.getBlockEntity(currentPos.getX(), currentPos.getY(), currentPos.getZ());
			if(!(thisConduit instanceof IConduitTile)){
				return false;
			}
			currentConduit = (T) thisConduit;
		}
		checkConduit(currentConduit,currentPos);
		root.walkedConduits.add(currentConduit);

		for (Direction direction : getAllowedDirections()) {
			if(direction == from || !currentConduit.isConnected(direction)){
				continue;
			}

			BlockEntity tile = world.getBlockEntity(direction.getOffsetX() + ((BlockEntity) currentConduit).x, direction.getOffsetY() + ((BlockEntity) currentConduit).y, direction.getOffsetZ() + ((BlockEntity) currentConduit).z);
			if(tile instanceof IConduitTile){
				T otherConduit = (T) tile;
				if(!otherConduit.isConnected(direction.getOpposite()) || isWalked(otherConduit)){
					continue;
				}
				if(isValid(currentConduit,otherConduit,currentPos,direction)){
					nextConduitDirections.add(direction);
					nextConduits.add(otherConduit);
					continue;
				}
			}
			checkNeighbour(currentConduit, currentPos, direction, tile);
		}
		return true;
	}

	protected void checkNeighbour(T conduit, Vec3i pos, Direction dirToNeighbour, BlockEntity neighbour){
		if(conduit != conduits[conduits.length -1]) throw new IllegalStateException("Current conduit is not the last one added, you dun goofed.");
		if(!(neighbour instanceof IConduitTile) && neighbour.getBlock() instanceof NetworkComponent){
			NetworkComponentTile[] path = new NetworkComponentTile[conduits.length+1];
			System.arraycopy(conduits, 0, path, 0, conduits.length);
			path[path.length-1] = (NetworkComponentTile) neighbour;
			routes.add(new NetworkPath(dirToNeighbour, path, getWalkedBlocks()));
		}
	}

	protected boolean isValid(T conduit, T neighbourConduit, Vec3i pos, Direction dirToNeighbour){
		return conduit.getType() == neighbourConduit.getType();
	}

	protected Direction[] getAllowedDirections() {
		return Direction.values();
	}

	private void checkConduit(T currentConduit, Vec3i currentPos) {
		conduits = ArrayUtils.add(conduits, currentConduit);
	}

	protected void onRemoveSubWalker(NetworkWalker<T> subWalker){}

	protected boolean isWalked(T conduit) {
		return root.walkedConduits.contains(conduit);
	}

	public void stop() {
		root.running = false;
	}

	public int getWalkedBlocks() {
		return walkedBlocks;
	}

	public boolean isRoot() {
		return this.root == this;
	}

	public boolean isFailed() {
		return failed;
	}

	public boolean isRunning() {
		return root.running;
	}

}
