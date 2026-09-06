package com.ezquest.astralclef.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Player inventory checks. Soft — returns false when player null or
 * item not resolvable. No Baritone dependency.
 */
public final class InventoryHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/inv");

	private InventoryHelper() {}

	public static boolean hasItem(ServerPlayerEntity player, String itemId, int minCount) {
		if (player == null || itemId == null || minCount <= 0) {
			return false;
		}
		if (itemId.startsWith("#")) {
			return false; // tag check TODO
		}
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) {
			return false;
		}
		Item item = Registry.ITEM.get(id);
		if (!id.equals(Registry.ITEM.getId(item))) {
			return false;
		}
		int have = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (!s.isEmpty() && s.getItem() == item) {
				have += s.getCount();
				if (have >= minCount) {
					return true;
				}
			}
		}
		LOGGER.debug("hasItem {} x{} — have {}", itemId, minCount, have);
		return have >= minCount;
	}

	public static int countItem(ServerPlayerEntity player, String itemId) {
		if (player == null || itemId == null) {
			return 0;
		}
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) {
			return 0;
		}
		Item item = Registry.ITEM.get(id);
		if (!id.equals(Registry.ITEM.getId(item))) {
			return 0;
		}
		int total = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (!s.isEmpty() && s.getItem() == item) {
				total += s.getCount();
			}
		}
		return total;
	}

	public static boolean hasAny(ServerPlayerEntity player, String... itemIds) {
		if (player == null || itemIds == null) {
			return false;
		}
		for (String id : itemIds) {
			if (hasItem(player, id, 1)) {
				return true;
			}
		}
		return false;
	}
}
