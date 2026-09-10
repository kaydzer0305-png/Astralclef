package com.ezquest.astralclef.bot;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.world.BlockPlacementHelper;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side smelting — the bot's "furnace tender".
 * <p>
 * Uses a real placed furnace near the player: moves smeltable + fuel from the
 * player's inventory into the block entity, lets it cook at vanilla speed,
 * and pulls the product back out. Call {@link #tickSmelt} every tick until it
 * reports {@link SmeltState#DONE}. All items are real; nothing is conjured.
 */
public final class BotSmelting {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/bot-smelt");

	public enum SmeltState {
		DONE,
		WORKING,
		NEED_INPUT,
		NEED_FUEL,
		NO_FURNACE
	}

	/** Fuel preference order (ids must exist in the pack to be used). */
	private static final String[] FUELS = {
			"minecraft:coal",
			"minecraft:charcoal",
			"minecraft:oak_log",
			"minecraft:spruce_log",
			"minecraft:birch_log",
			"minecraft:oak_planks",
	};

	private static final int FURNACE_SCAN_RADIUS = 8;

	private BotSmelting() {}

	/**
	 * Advance one smelting goal: {@code rawId} → {@code productId} until the
	 * player holds {@code want} of the product.
	 */
	public static SmeltState tickSmelt(ServerPlayerEntity player, String rawId, String productId, int want) {
		if (player == null || rawId == null || productId == null || want <= 0) {
			return SmeltState.DONE;
		}
		try {
			if (InventoryHelper.hasItem(player, productId, want)) {
				return SmeltState.DONE;
			}
			ServerWorld world = (ServerWorld) player.getWorld();
			AbstractFurnaceBlockEntity furnace = findFurnace(world, player);
			if (furnace == null) {
				return SmeltState.NO_FURNACE;
			}
			Item raw = itemOf(rawId);
			Item product = itemOf(productId);
			if (raw == null || product == null) {
				return SmeltState.NEED_INPUT;
			}
			// Pull finished product out first (frees the slot, counts progress).
			ItemStack out = furnace.getStack(2);
			if (!out.isEmpty() && out.isOf(product)) {
				int move = out.getCount();
				furnace.setStack(2, ItemStack.EMPTY);
				furnace.markDirty();
				BotCrafting.giveToPlayer(player, new ItemStack(product, move));
				LOGGER.debug("Smelt: pulled {} x{} from furnace", productId, move);
				if (InventoryHelper.hasItem(player, productId, want)) {
					return SmeltState.DONE;
				}
			}
			// Ensure smeltable input — only stage what is still needed so the
			// furnace is free for the next metal once this one is DONE.
			ItemStack input = furnace.getStack(0);
			boolean inputOk = !input.isEmpty() && input.isOf(raw);
			int need = want - InventoryHelper.countItem(player, productId)
					- (inputOk ? input.getCount() : 0);
			if (!inputOk) {
				if (!input.isEmpty()) {
					// Furnace busy with something else — wait rather than steal it.
					return SmeltState.WORKING;
				}
				if (need <= 0) {
					return SmeltState.WORKING;
				}
				if (!InventoryHelper.hasItem(player, rawId, 1) || !isSmeltable(world, raw)) {
					return SmeltState.NEED_INPUT;
				}
				moveFromPlayer(player, furnace, 0, raw, Math.min(need, 64));
			} else if (need > 0) {
				moveFromPlayer(player, furnace, 0, raw, Math.min(need, 64));
			}
			// Ensure fuel (or an already-burning furnace keeps going on its own).
			if (furnace.getStack(1).isEmpty()) {
				if (!stokeFromPlayer(player, furnace)) {
					return SmeltState.NEED_FUEL;
				}
			}
			return SmeltState.WORKING;
		} catch (Throwable t) {
			LOGGER.debug("tickSmelt {} → {} failed: {}", rawId, productId, t.toString());
			return SmeltState.WORKING;
		}
	}

	private static AbstractFurnaceBlockEntity findFurnace(ServerWorld world, ServerPlayerEntity player) {
		for (String id : new String[]{"minecraft:furnace", "minecraft:blast_furnace"}) {
			BlockPos pos = BlockPlacementHelper.findBlockNearby(player, id, FURNACE_SCAN_RADIUS);
			if (pos == null) {
				continue;
			}
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof AbstractFurnaceBlockEntity furnace && !furnace.isRemoved()) {
				return furnace;
			}
		}
		return null;
	}

	private static Item itemOf(String id) {
		Identifier parsed = Identifier.tryParse(id);
		if (parsed == null) {
			return null;
		}
		Item item = Registry.ITEM.get(parsed);
		return parsed.equals(Registry.ITEM.getId(item)) ? item : null;
	}

	private static boolean isSmeltable(ServerWorld world, Item raw) {
		try {
			return world.getServer().getRecipeManager()
					.getFirstMatch(RecipeType.SMELTING, new SimpleInventory(new ItemStack(raw)), world)
					.isPresent();
		} catch (Throwable t) {
			return false;
		}
	}

	/** Move up to {@code max} of {@code item} from player into furnace slot. */
	private static void moveFromPlayer(ServerPlayerEntity player, AbstractFurnaceBlockEntity furnace, int slot, Item item, int max) {
		ItemStack dest = furnace.getStack(slot);
		int room = dest.isEmpty() ? max : (dest.isOf(item) ? max - dest.getCount() : 0);
		if (room <= 0) {
			return;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.size() && room > 0; i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && s.isOf(item)) {
				int take = Math.min(s.getCount(), room);
				if (dest.isEmpty()) {
					furnace.setStack(slot, new ItemStack(item, take));
					dest = furnace.getStack(slot);
				} else {
					dest.increment(take);
				}
				s.decrement(take);
				room -= take;
			}
		}
		inv.markDirty();
		furnace.markDirty();
	}

	/** Move one fuel stack from player into the furnace fuel slot. */
	private static boolean stokeFromPlayer(ServerPlayerEntity player, AbstractFurnaceBlockEntity furnace) {
		var inv = player.getInventory();
		for (String fuelId : FUELS) {
			Item fuel = itemOf(fuelId);
			if (fuel == null) {
				continue;
			}
			for (int i = 0; i < inv.size(); i++) {
				ItemStack s = inv.getStack(i);
				if (!s.isEmpty() && s.isOf(fuel) && AbstractFurnaceBlockEntity.canUseAsFuel(s)) {
					if (stokeBE(player, furnace, fuel)) {
						s.decrement(1);
						inv.markDirty();
						LOGGER.info("Smelt: stoked furnace with {}", fuelId);
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Stoke a specific furnace block (used by Create furnace jobs, which insert
	 * the smeltable but no fuel). Best-effort: false when the block is not a
	 * furnace, the fuel slot is full of something else, or the player has no fuel.
	 */
	public static boolean stokeFurnaceAt(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		if (world == null || pos == null || player == null) {
			return false;
		}
		try {
			BlockEntity be = world.getBlockEntity(pos);
			if (!(be instanceof AbstractFurnaceBlockEntity furnace) || furnace.isRemoved()) {
				return false;
			}
			if (!furnace.getStack(1).isEmpty()) {
				return true; // already fueled
			}
			var inv = player.getInventory();
			for (String fuelId : FUELS) {
				Item fuel = itemOf(fuelId);
				if (fuel == null) {
					continue;
				}
				for (int i = 0; i < inv.size(); i++) {
					ItemStack s = inv.getStack(i);
					if (!s.isEmpty() && s.isOf(fuel) && AbstractFurnaceBlockEntity.canUseAsFuel(s)) {
						if (stokeBE(player, furnace, fuel)) {
							s.decrement(1);
							inv.markDirty();
							LOGGER.info("Smelt: stoked job furnace at {} with {}",
									pos.toShortString(), fuelId);
							return true;
						}
					}
				}
			}
			return false;
		} catch (Throwable t) {
			LOGGER.debug("stokeFurnaceAt {} failed: {}", pos.toShortString(), t.toString());
			return false;
		}
	}

	/** Put one {@code fuel} item into the furnace fuel slot (slot must fit). */
	private static boolean stokeBE(ServerPlayerEntity player, AbstractFurnaceBlockEntity furnace, Item fuel) {
		try {
			ItemStack inSlot = furnace.getStack(1);
			if (inSlot.isEmpty()) {
				furnace.setStack(1, new ItemStack(fuel, 1));
			} else if (inSlot.isOf(fuel) && inSlot.getCount() < inSlot.getMaxCount()) {
				inSlot.increment(1);
			} else {
				return false;
			}
			furnace.markDirty();
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}
