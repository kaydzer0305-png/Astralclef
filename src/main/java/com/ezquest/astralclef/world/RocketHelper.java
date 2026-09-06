package com.ezquest.astralclef.world;

import com.ezquest.astralclef.inventory.InventoryHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ad Astra rocket checks. Uses inventory counts as soft gating; real
 * structure validation (pad, fuel) is TODO and will be wired when the
 * Ad Astra block entities are queryable.
 */
public final class RocketHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/rocket");

	private RocketHelper() {}

	/** Item ids for Ad Astra rockets by tier ( Astral pack uses Ad Astra). */
	public static String rocketIdFor(AdAstraRoutes.Destination dest) {
		return switch (dest) {
			case MOON -> "ad_astra:tier_2_rocket";
			case MARS -> "ad_astra:tier_3_rocket";
			case MERCURY, SINGULARITY -> "ad_astra:tier_4_rocket";
		};
	}

	public static boolean hasRocket(ServerPlayerEntity player, AdAstraRoutes.Destination dest) {
		String id = rocketIdFor(dest);
		boolean have = InventoryHelper.hasItem(player, id, 1);
		if (!have) {
			LOGGER.debug("Rocket check {}: missing {}", dest, id);
		}
		return have;
	}

	/** Whether player has oxygen gear (soft check — looks for common ids). */
	public static boolean hasOxygenGear(ServerPlayerEntity player) {
		return InventoryHelper.hasAny(player,
				"ad_astra:oxygen_tank",
				"ad_astra:oxygen_gear",
				"ad_astra:space_suit",
				"ad_astra:netherite_space_suit");
	}

	public static boolean hasFuel(ServerPlayerEntity player) {
		return InventoryHelper.hasAny(player,
				"ad_astra:fuel_bucket",
				"ad_astra:oil_bucket",
				"minecraft:lava_bucket");
	}
}
