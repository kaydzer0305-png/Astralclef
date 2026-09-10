package com.ezquest.astralclef.bot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side embodiment primitives — the bot's "hands".
 * <p>
 * Unlike the creative-style {@code world.setBlockState} placement used for
 * setup blocks, everything here goes through survival mechanics: breaking
 * drops real items with tool wear, eating consumes real food. This is what
 * lets the bot play the world legitimately, Altoclef-style, without Baritone.
 */
public final class BotActions {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/bot-actions");

	/** Survival reach (squared) for breaking. */
	private static final double REACH_SQ = 4.5 * 4.5;

	private BotActions() {}

	/** Whether the block can ever be broken in survival (bedrock etc. excluded). */
	public static boolean isBreakable(ServerWorld world, BlockPos pos) {
		try {
			BlockState state = world.getBlockState(pos);
			if (state.isAir()) {
				return false;
			}
			return state.getHardness(world, pos) >= 0.0F;
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * Break a block in survival via the player's interaction manager
	 * (drops spawn, tool takes damage). Selects a suitable hotbar tool first.
	 *
	 * @return true when the block is gone (or was already air)
	 */
	public static boolean breakBlock(ServerPlayerEntity player, BlockPos pos) {
		if (player == null || pos == null) {
			return false;
		}
		ServerWorld world = (ServerWorld) player.getWorld();
		if (world.getBlockState(pos).isAir()) {
			return true;
		}
		if (!isBreakable(world, pos)) {
			return false;
		}
		if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > REACH_SQ) {
			return false;
		}
		try {
			selectToolFor(player, world.getBlockState(pos));
			return player.interactionManager.tryBreakBlock(pos);
		} catch (Throwable t) {
			LOGGER.debug("breakBlock {} failed: {}", pos.toShortString(), t.toString());
			return false;
		}
	}

	/**
	 * Pick the best hotbar tool for a block (anything reporting
	 * {@code isSuitableFor}, else keep the current slot).
	 */
	public static void selectToolFor(ServerPlayerEntity player, BlockState state) {
		try {
			var inv = player.getInventory();
			int best = -1;
			for (int i = 0; i < 9; i++) {
				ItemStack stack = inv.getStack(i);
				if (!stack.isEmpty() && stack.isSuitableFor(state)) {
					best = i;
					break;
				}
			}
			if (best >= 0 && inv.selectedSlot != best) {
				inv.selectedSlot = best;
				player.playerScreenHandler.syncState();
			}
		} catch (Throwable t) {
			LOGGER.debug("selectToolFor failed: {}", t.toString());
		}
	}

	/**
	 * Place a block in survival via the player's interaction manager
	 * (proper facing/support rules, consumes the item). Sneaks during the
	 * click so interactable neighbors don't swallow the use action.
	 *
	 * @return true when the block ended up placed
	 */
	public static boolean placeBlock(ServerPlayerEntity player, String blockId, BlockPos pos) {
		if (player == null || blockId == null || pos == null) {
			return false;
		}
		try {
			Identifier id = Identifier.tryParse(blockId);
			if (id == null) {
				return false;
			}
			Block block = Registry.BLOCK.get(id);
			if (!id.equals(Registry.BLOCK.getId(block))) {
				return false;
			}
			ServerWorld world = (ServerWorld) player.getWorld();
			BlockState existing = world.getBlockState(pos);
			if (!existing.isAir() && !existing.getMaterial().isReplaceable()) {
				return world.getBlockState(pos).isOf(block);
			}
			var inv = player.getInventory();
			int slot = -1;
			for (int i = 0; i < 9; i++) {
				ItemStack s = inv.getStack(i);
				if (!s.isEmpty() && Registry.ITEM.getId(s.getItem()).equals(id)) {
					slot = i;
					break;
				}
			}
			if (slot < 0) {
				return false;
			}
			for (Direction d : Direction.values()) {
				BlockPos neighbor = pos.offset(d);
				BlockState ns = world.getBlockState(neighbor);
				if (!ns.isSideSolidFullSquare(world, neighbor, d.getOpposite())) {
					continue;
				}
				Vec3d hitVec = Vec3d.ofCenter(neighbor)
						.add(new Vec3d(d.getOpposite().getOffsetX() * 0.5,
								d.getOpposite().getOffsetY() * 0.5,
								d.getOpposite().getOffsetZ() * 0.5));
				BlockHitResult hit = new BlockHitResult(hitVec, d.getOpposite(), neighbor, false);
				int prev = inv.selectedSlot;
				boolean wasSneaking = player.isSneaking();
				try {
					inv.selectedSlot = slot;
					player.setSneaking(true);
					ActionResult res = player.interactionManager.interactBlock(
							player, world, inv.getStack(slot), Hand.MAIN_HAND, hit);
					if (res.isAccepted() || world.getBlockState(pos).isOf(block)) {
						LOGGER.info("Placed {} at {} (survival click)", blockId, pos.toShortString());
						return true;
					}
				} finally {
					inv.selectedSlot = prev;
					player.setSneaking(wasSneaking);
				}
				return false;
			}
			return false;
		} catch (Throwable t) {
			LOGGER.debug("placeBlock {} failed: {}", blockId, t.toString());
			return false;
		}
	}

	/**
	 * Eat if hungry: consumes one real food item from inventory and applies
	 * its {@link FoodComponent} values. Skips the use-animation (server has
	 * no key input) but the economy is honest — no free saturation.
	 *
	 * @return true when the player is fed (or had no need / no food found)
	 */
	public static boolean eatIfHungry(ServerPlayerEntity player) {
		if (player == null) {
			return true;
		}
		try {
			var hunger = player.getHungerManager();
			if (hunger.getFoodLevel() > 12) {
				return true;
			}
			var inv = player.getInventory();
			for (int i = 0; i < inv.size(); i++) {
				ItemStack stack = inv.getStack(i);
				if (stack.isEmpty()) {
					continue;
				}
				FoodComponent food = stack.getItem().getFoodComponent();
				if (food == null) {
					continue;
				}
				stack.decrement(1);
				hunger.add(food.getHunger(), food.getSaturationModifier());
				LOGGER.info("Bot ate {} (food now {})", stack.getItem(), hunger.getFoodLevel());
				return true;
			}
			LOGGER.debug("Bot hungry (food level {}) but no food in inventory", hunger.getFoodLevel());
			return false;
		} catch (Throwable t) {
			LOGGER.debug("eatIfHungry failed: {}", t.toString());
			return false;
		}
	}
}
