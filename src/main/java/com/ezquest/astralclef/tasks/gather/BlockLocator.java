package com.ezquest.astralclef.tasks.gather;

import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nearest-block scan around a player. Reuses the cube-scan pattern from
 * CreateMachineLocator but for arbitrary block ids (ores, gravel, etc).
 */
public final class BlockLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/block-locate");

	private BlockLocator() {}

	public record Result(BlockPos pos, String blockId, double distSq) {}

	public static Result findNearest(ServerPlayerEntity player, List<String> blockIds, int radius) {
		if (player == null || blockIds == null || blockIds.isEmpty() || radius <= 0) {
			return null;
		}
		ServerWorld world = (ServerWorld) player.getWorld();
		BlockPos origin = player.getBlockPos();
		Set<Block> targets = resolve(blockIds);
		if (targets.isEmpty()) {
			return null;
		}
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockPos best = null;
		String bestId = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					var state = world.getBlockState(m);
					Block b = state.getBlock();
					if (!targets.contains(b)) {
						continue;
					}
					double dist = origin.getSquaredDistance(m);
					if (dist < bestDist) {
						bestDist = dist;
						best = m.toImmutable();
						bestId = Registry.BLOCK.getId(b).toString();
					}
				}
			}
		}
		if (best != null) {
			LOGGER.debug("BlockLocator found {} at {} dist {}", bestId, best.toShortString(), Math.sqrt(bestDist));
			return new Result(best, bestId, bestDist);
		}
		return null;
	}

	private static Set<Block> resolve(List<String> ids) {
		Set<Block> out = new HashSet<>();
		for (String s : ids) {
			Identifier id = Identifier.tryParse(s);
			if (id == null) continue;
			Block b = Registry.BLOCK.get(id);
			if (id.equals(Registry.BLOCK.getId(b))) {
				out.add(b);
			}
		}
		return out;
	}
}
