package com.ezquest.astralclef.tasks.create.world;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans a cube around {@link CreateWorldContext} origin for Create / vanilla machine blocks.
 * Matches by Registry block id (Create {@code AllBlocks} equivalents: create:basin, etc.).
 */
public final class CreateMachineLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-locate");

	private CreateMachineLocator() {}

	public static Optional<BlockPos> locate(CreateWorldContext ctx, CreateMachineType type) {
		if (ctx == null || !ctx.isValid() || type == null) {
			return Optional.empty();
		}
		ServerWorld world = ctx.getWorld();
		BlockPos origin = ctx.getOrigin();
		int r = ctx.getSearchRadius();
		Set<Block> targets = resolveBlocks(type);
		if (targets.isEmpty()) {
			LOGGER.warn("No registry blocks resolved for {}", type);
			return Optional.empty();
		}
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState state = world.getBlockState(m);
					if (!targets.contains(state.getBlock())) {
						continue;
					}
					double dist = origin.getSquaredDistance(m);
					if (dist < bestDist) {
						bestDist = dist;
						best = m.toImmutable();
					}
				}
			}
		}
		if (best != null) {
			BlockEntity be = world.getBlockEntity(best);
			LOGGER.info("Located {} at {} (be={})", type, best.toShortString(),
					be != null ? be.getType().toString() : "none");
		}
		return Optional.ofNullable(best);
	}


	/** Locate nearest machine among kind candidates. */
	public static Optional<BlockPos> locateForKind(CreateWorldContext ctx, CreateRecipeKinds.Kind kind) {
		if (ctx == null || !ctx.isValid() || kind == null) {
			return Optional.empty();
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		CreateMachineType bestType = null;
		for (CreateMachineType type : CreateMachineType.candidatesFor(kind)) {
			Optional<BlockPos> found = locate(ctx, type);
			if (found.isEmpty()) {
				continue;
			}
			double dist = ctx.getOrigin().getSquaredDistance(found.get());
			if (dist < bestDist) {
				bestDist = dist;
				best = found.get();
				bestType = type;
			}
		}
		if (best != null) {
			LOGGER.info("locateForKind {} chose {} at {}", kind, bestType, best.toShortString());
		}
		return Optional.ofNullable(best);
	}

	private static Set<Block> resolveBlocks(CreateMachineType type) {
		Set<Block> out = new HashSet<>();
		for (String id : type.blockIds()) {
			Identifier ident = Identifier.tryParse(id);
			if (ident == null) {
				continue;
			}
			Block block = Registry.BLOCK.get(ident);
			Identifier got = Registry.BLOCK.getId(block);
			if (ident.equals(got)) {
				out.add(block);
			}
		}
		return out;
	}
}
