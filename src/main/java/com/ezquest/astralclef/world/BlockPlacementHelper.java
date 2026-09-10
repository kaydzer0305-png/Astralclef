package com.ezquest.astralclef.world;

import com.ezquest.astralclef.inventory.InventoryHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft block placement helper. Consumes one item from player inventory and
 * sets the block in world if the target pos is replaceable. No hard dep on
 * any automation mod; fails gracefully when inventory/world missing.
 * <p>
 * Prefers a survival-legit click ({@code BotActions.placeBlock}); falls back
 * to direct state set (same item consumption) when no support face exists.
 */
public final class BlockPlacementHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/place");

	private BlockPlacementHelper() {}

	public static boolean place(ServerPlayerEntity player, String blockId, BlockPos pos) {
		if (player == null || blockId == null || pos == null) {
			return false;
		}
		try {
			if (com.ezquest.astralclef.bot.BotActions.placeBlock(player, blockId, pos)) {
				return true;
			}
		} catch (Throwable t) {
			LOGGER.debug("place survival click failed for {}: {}", blockId, t.toString());
		}
		Identifier id = Identifier.tryParse(blockId);
		if (id == null) {
			return false;
		}
		Block block = Registry.BLOCK.get(id);
		if (!id.equals(Registry.BLOCK.getId(block))) {
			LOGGER.debug("Block not registered: {}", blockId);
			return false;
		}
		ServerWorld world = (ServerWorld) player.getWorld();
		BlockState existing = world.getBlockState(pos);
		if (!existing.isAir() && !existing.getMaterial().isReplaceable()) {
			LOGGER.debug("Place {} blocked at {} by {}", blockId, pos.toShortString(), existing.getBlock());
			return false;
		}
		// Consume one item if available (use item id matching block id where possible)
		String itemId = blockId;
		if (!InventoryHelper.hasItem(player, itemId, 1)) {
			// Try common item fallback (block item may differ)
			if (!InventoryHelper.hasItem(player, blockId.replace("create:", "create:"), 1)) {
				LOGGER.info("Place {}: missing item in inventory at {}", blockId, pos.toShortString());
				return false;
			}
		}
		// Remove one from inventory
		for (int i = 0; i < player.getInventory().size(); i++) {
			var stack = player.getInventory().getStack(i);
			if (!stack.isEmpty() && Registry.ITEM.getId(stack.getItem()).equals(id)) {
				stack.decrement(1);
				break;
			}
		}
		BlockState state = block.getDefaultState();
		boolean ok = world.setBlockState(pos, state);
		LOGGER.info("Place {} at {} -> {}", blockId, pos.toShortString(), ok ? "ok" : "failed");
		return ok;
	}
	public static BlockPos findPlacePos(ServerPlayerEntity player, int radius) {
		if (player == null) return null;
		ServerWorld world = (ServerWorld) player.getWorld();
		BlockPos origin = player.getBlockPos();
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int r = 1; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dy = -1; dy <= 1; dy++) {
						m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
						if (world.getBlockState(m).isAir()) {
							return m.toImmutable();
						}
					}
				}
			}
		}
		return origin.up();
	}

	/**
	 * Nearest block matching {@code blockId} inside a cube around the player.
	 * Soft — {@code null} when the player/id is missing, the block is
	 * unregistered (mod absent), or nothing is found in range.
	 */
	public static BlockPos findBlockNearby(ServerPlayerEntity player, String blockId, int radius) {
		if (player == null || blockId == null || radius <= 0) {
			return null;
		}
		Identifier id = Identifier.tryParse(blockId);
		if (id == null) {
			return null;
		}
		Block block = Registry.BLOCK.get(id);
		if (!id.equals(Registry.BLOCK.getId(block))) {
			LOGGER.debug("findBlockNearby: block not registered: {}", blockId);
			return null;
		}
		ServerWorld world = (ServerWorld) player.getWorld();
		BlockPos origin = player.getBlockPos();
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (!world.getBlockState(m).isOf(block)) {
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
		return best;
	}
}
