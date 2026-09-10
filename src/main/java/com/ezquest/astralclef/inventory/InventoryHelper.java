package com.ezquest.astralclef.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

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
			return countTagged(player, itemId) >= minCount;
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
		if (itemId.startsWith("#")) {
			return countTagged(player, itemId);
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

	/**
	 * Count inventory items matching an item tag ({@code #namespace:path}).
	 * Soft — 0 when the tag is unparseable, empty, or the lookup fails.
	 */
	private static int countTagged(ServerPlayerEntity player, String tagId) {
		try {
			Identifier id = Identifier.tryParse(tagId.substring(1));
			if (id == null) {
				return 0;
			}
			TagKey<Item> tag = TagKey.of(Registry.ITEM_KEY, id);
			Set<Item> members = new HashSet<>();
			for (RegistryEntry<Item> entry : Registry.ITEM.getOrCreateEntryList(tag)) {
				members.add(entry.value());
			}
			if (members.isEmpty()) {
				LOGGER.debug("tag {} resolved to no items", tagId);
				return 0;
			}
			int total = 0;
			for (int i = 0; i < player.getInventory().size(); i++) {
				ItemStack s = player.getInventory().getStack(i);
				if (!s.isEmpty() && members.contains(s.getItem())) {
					total += s.getCount();
				}
			}
			return total;
		} catch (Throwable t) {
			LOGGER.debug("tag check {} failed: {}", tagId, t.toString());
			return 0;
		}
	}
}
