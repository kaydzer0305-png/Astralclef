package com.ezquest.astralclef.world;

import com.ezquest.astralclef.inventory.InventoryHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ad Astra rocket checks. Inventory counts gate rockets/oxygen/fuel;
 * launch-pad presence is validated by scanning for the placed pad block
 * near the player (soft — false when Ad Astra is absent).
 */
public final class RocketHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/rocket");

	/** Ad Astra launch pad block id. */
	public static final String LAUNCH_PAD = "ad_astra:launch_pad";
	/** Ad Astra NASA workbench block id (rocket assembly station). */
	public static final String NASA_WORKBENCH = "ad_astra:nasa_workbench";
	/** Pad scan radius (blocks) around the player. */
	public static final int PAD_SCAN_RADIUS = 12;

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

	/**
	 * Whether a launch pad block exists near the player.
	 * Soft — false when the player is null or Ad Astra is absent/unregistered.
	 */
	public static boolean hasLaunchPad(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		boolean found = BlockPlacementHelper.findBlockNearby(player, LAUNCH_PAD, PAD_SCAN_RADIUS) != null;
		if (!found) {
			LOGGER.debug("no {} within {} blocks of player", LAUNCH_PAD, PAD_SCAN_RADIUS);
		}
		return found;
	}

	/**
	 * Whether a NASA workbench exists near the player (rocket assembly).
	 * Soft — false when the player is null or Ad Astra is absent/unregistered.
	 * Slot-driving the workbench needs Ad Astra internals (not on the compile
	 * classpath), so callers treat this as a guidance gate, not automation.
	 */
	public static boolean hasWorkbench(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		return BlockPlacementHelper.findBlockNearby(player, NASA_WORKBENCH, PAD_SCAN_RADIUS) != null;
	}
}
