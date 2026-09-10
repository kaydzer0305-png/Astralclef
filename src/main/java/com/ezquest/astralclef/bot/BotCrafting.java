package com.ezquest.astralclef.bot;

import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Server-side crafting — the bot's "workbench".
 * <p>
 * Crafts against the <b>live</b> {@code RecipeManager} (pack/KubeJS recipes
 * included) using only real items from the player's inventory: a transient
 * 3×3 grid is filled from a template, matched, consumed from the player, and
 * the result + remainders are given back. No screen ever opens, no items are
 * conjured. Returns {@link ItemStack#EMPTY} when nothing matched or the
 * player lacks ingredients (caller falls back to machine jobs / waiting).
 */
public final class BotCrafting {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/bot-craft");

	private BotCrafting() {}

	/**
	 * Try each 3×3 template (single-count stacks; EMPTY = hole) until one
	 * matches a live crafting recipe, then craft it for real.
	 *
	 * @return the crafted stack (already given to the player), or EMPTY
	 */
	public static ItemStack craftFirstMatch(ServerPlayerEntity player, ItemStack[]... templates) {
		if (player == null || templates == null) {
			return ItemStack.EMPTY;
		}
		for (ItemStack[] template : templates) {
			ItemStack made = craftShaped(player, template);
			if (!made.isEmpty()) {
				return made;
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * Craft one 3×3 template (9 single-count stacks; EMPTY = hole).
	 *
	 * @return the crafted stack (already given to the player), or EMPTY
	 */
	public static ItemStack craftShaped(ServerPlayerEntity player, ItemStack[] template) {
		if (player == null || template == null || template.length != 9) {
			return ItemStack.EMPTY;
		}
		try {
			ServerWorld world = (ServerWorld) player.getWorld();
			CraftingInventory grid = newGrid();
			for (int i = 0; i < 9; i++) {
				ItemStack t = template[i];
				if (t == null || t.isEmpty()) {
					grid.setStack(i, ItemStack.EMPTY);
				} else {
					ItemStack one = t.copy();
					one.setCount(1);
					grid.setStack(i, one);
				}
			}
			Optional<CraftingRecipe> match = world.getServer().getRecipeManager()
					.getFirstMatch(RecipeType.CRAFTING, grid, world);
			if (match.isEmpty()) {
				return ItemStack.EMPTY;
			}
			CraftingRecipe recipe = match.get();
			if (!hasIngredients(player.getInventory(), template)) {
				LOGGER.debug("Craft {}: recipe matched but ingredients missing", recipe.getId());
				return ItemStack.EMPTY;
			}
			consumeIngredients(player.getInventory(), template);
			ItemStack result = recipe.craft(grid);
			giveToPlayer(player, result);
			DefaultedList<ItemStack> remainder = recipe.getRemainder(grid);
			for (ItemStack rem : remainder) {
				if (rem != null && !rem.isEmpty()) {
					giveToPlayer(player, rem.copy());
				}
			}
			syncInventory(player);
			LOGGER.info("Crafted {} x{} via live recipe {}", result.getItem(), result.getCount(), recipe.getId());
			return result;
		} catch (Throwable t) {
			LOGGER.debug("craftShaped failed: {}", t.toString());
			return ItemStack.EMPTY;
		}
	}

	/** 3×3 grid with three full rows (row order as given). */
	public static ItemStack[] grid3(String rowA, String rowB, String rowC) {
		ItemStack[] g = new ItemStack[9];
		String[] rows = {rowA, rowB, rowC};
		for (int r = 0; r < 3; r++) {
			ItemStack s = stackOf(rows[r], 1);
			g[r * 3] = s.copy();
			g[r * 3 + 1] = s.copy();
			g[r * 3 + 2] = s.copy();
		}
		return g;
	}

	public static ItemStack stackOf(String itemId, int count) {
		if (itemId == null || itemId.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try {
			var id = net.minecraft.util.Identifier.tryParse(itemId);
			if (id == null) {
				return ItemStack.EMPTY;
			}
			var item = net.minecraft.util.registry.Registry.ITEM.get(id);
			if (!id.equals(net.minecraft.util.registry.Registry.ITEM.getId(item))) {
				return ItemStack.EMPTY;
			}
			return new ItemStack(item, Math.max(1, count));
		} catch (Throwable t) {
			return ItemStack.EMPTY;
		}
	}

	private static CraftingInventory newGrid() {
		ScreenHandler dummy = new ScreenHandler(ScreenHandlerType.CRAFTING, 0) {
			@Override
			public boolean canUse(PlayerEntity player) {
				return true;
			}
		};
		return new CraftingInventory(dummy, 3, 3);
	}

	private static boolean hasIngredients(Inventory inv, ItemStack[] template) {
		for (ItemStack t : template) {
			if (t == null || t.isEmpty()) {
				continue;
			}
			int need = t.getCount();
			for (int i = 0; i < inv.size(); i++) {
				ItemStack s = inv.getStack(i);
				if (!s.isEmpty() && ItemStack.canCombine(s, t)) {
					need -= s.getCount();
					if (need <= 0) {
						break;
					}
				}
			}
			if (need > 0) {
				return false;
			}
		}
		return true;
	}

	private static void consumeIngredients(Inventory inv, ItemStack[] template) {
		for (ItemStack t : template) {
			if (t == null || t.isEmpty()) {
				continue;
			}
			int need = t.getCount();
			for (int i = 0; i < inv.size() && need > 0; i++) {
				ItemStack s = inv.getStack(i);
				if (!s.isEmpty() && ItemStack.canCombine(s, t)) {
					int take = Math.min(s.getCount(), need);
					s.decrement(take);
					need -= take;
				}
			}
		}
		inv.markDirty();
	}

	/** Merge into existing stacks, then empty slots, then drop at feet. */
	public static void giveToPlayer(ServerPlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return;
		}
		try {
			Inventory inv = player.getInventory();
			// Merge first.
			for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
				ItemStack s = inv.getStack(i);
				if (!s.isEmpty() && ItemStack.canCombine(s, stack) && s.getCount() < s.getMaxCount()) {
					int room = s.getMaxCount() - s.getCount();
					int move = Math.min(room, stack.getCount());
					s.increment(move);
					stack.decrement(move);
				}
			}
			// Then empty slots.
			for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
				if (inv.getStack(i).isEmpty()) {
					inv.setStack(i, stack.copy());
					stack.setCount(0);
				}
			}
			inv.markDirty();
			if (!stack.isEmpty()) {
				ServerWorld world = (ServerWorld) player.getWorld();
				var pos = player.getPos();
				world.spawnEntity(new ItemEntity(world, pos.x, pos.y + 0.5, pos.z, stack.copy()));
				stack.setCount(0);
			}
			syncInventory(player);
		} catch (Throwable t) {
			LOGGER.debug("giveToPlayer failed: {}", t.toString());
		}
	}

	private static void syncInventory(ServerPlayerEntity player) {
		try {
			player.currentScreenHandler.sendContentUpdates();
			player.playerScreenHandler.syncState();
		} catch (Throwable ignored) {}
	}
}
