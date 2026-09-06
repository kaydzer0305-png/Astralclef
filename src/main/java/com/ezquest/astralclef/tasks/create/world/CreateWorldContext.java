package com.ezquest.astralclef.tasks.create.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * World search context for Create machine locate / I/O.
 * Set from TaskRunner commands or auto-bound from a nearby player.
 */
public final class CreateWorldContext {
	public static final int DEFAULT_SEARCH_RADIUS = 16;

	private ServerWorld world;
	private BlockPos origin;
	private int searchRadius = DEFAULT_SEARCH_RADIUS;

	public CreateWorldContext() {}

	public CreateWorldContext(ServerWorld world, BlockPos origin, int searchRadius) {
		this.world = world;
		this.origin = origin;
		this.searchRadius = searchRadius > 0 ? searchRadius : DEFAULT_SEARCH_RADIUS;
	}

	public boolean isValid() {
		return world != null && origin != null;
	}

	public ServerWorld getWorld() {
		return world;
	}

	public void setWorld(ServerWorld world) {
		this.world = world;
	}

	public BlockPos getOrigin() {
		return origin;
	}

	public void setOrigin(BlockPos origin) {
		this.origin = origin;
	}

	public int getSearchRadius() {
		return searchRadius;
	}

	public void setSearchRadius(int searchRadius) {
		this.searchRadius = searchRadius > 0 ? searchRadius : DEFAULT_SEARCH_RADIUS;
	}

	public void set(ServerWorld world, BlockPos origin) {
		this.world = world;
		this.origin = origin;
	}

	public void set(ServerWorld world, BlockPos origin, int searchRadius) {
		set(world, origin);
		setSearchRadius(searchRadius);
	}

	public void clear() {
		this.world = null;
		this.origin = null;
	}

	@Override
	public String toString() {
		if (!isValid()) {
			return "CreateWorldContext{unset}";
		}
		return "CreateWorldContext{world=" + world.getRegistryKey().getValue()
				+ ", origin=" + origin.toShortString()
				+ ", radius=" + searchRadius + "}";
	}
}
