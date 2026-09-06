package com.ezquest.astralclef.tasks.gather;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.movement.BaritoneHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Generic gather primitive: wait until player has {@code count}×{@code itemId},
 * optionally pathing to the nearest matching block via Baritone.
 * <p>
 * Stall-safe: if no player/world context, log and keep waiting (no hard crash).
 * Returns success immediately once inventory satisfies the requirement.
 * Maps common items to mineable blocks for block locate (extend as needed).
 */
public final class GatherTask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/gather");

	private final String itemId;
	private final int count;
	private final List<String> blockIds;
	private final int searchRadius;
	private final int timeoutTicks;

	private int ticks;
	private boolean warned;

	public GatherTask(String itemId, int count) {
		this(itemId, count, blockIdsFor(itemId), 24, 600);
	}

	public GatherTask(String itemId, int count, List<String> blockIds, int searchRadius, int timeoutTicks) {
		if (itemId == null || itemId.isEmpty()) {
			throw new IllegalArgumentException("itemId");
		}
		this.itemId = itemId;
		this.count = Math.max(1, count);
		this.blockIds = blockIds == null ? List.of() : List.copyOf(blockIds);
		this.searchRadius = Math.max(8, searchRadius);
		this.timeoutTicks = Math.max(20, timeoutTicks);
	}

	public String getItemId() { return itemId; }
	public int getCount() { return count; }

	@Override
	public boolean isEqual(Task other) {
		if (!(other instanceof GatherTask g)) {
			return false;
		}
		return itemId.equals(g.itemId) && count == g.count;
	}

	@Override
	protected void onStart() {
		ticks = 0;
		warned = false;
		LOGGER.info("Gather {} x{} — searching {} within {} (Baritone={})",
				itemId, count, blockIds.isEmpty() ? "inventory only" : blockIds, searchRadius, BaritoneHelper.isPresent());
	}

	@Override
	protected Task onTick() {
		ticks++;
		ServerPlayerEntity player = firstPlayer();
		if (player != null && InventoryHelper.hasItem(player, itemId, count)) {
			LOGGER.info("Gather {} x{} satisfied (have {} ≥ {})", itemId, count, InventoryHelper.countItem(player, itemId), count);
			return null;
		}
		if (ticks >= timeoutTicks) {
			if (!warned) {
				LOGGER.warn("Gather {} x{} timed out after {} ticks — have {} (no auto-complete)", itemId, count, ticks,
						player == null ? "?" : InventoryHelper.countItem(player, itemId));
				warned = true;
			}
			// Keep waiting rather than silently succeeding; the parent phase will show stuck in /astralclef status.
			// Return null to stay alive; isFinished stays false.
			return null;
		}
		if (player != null && !blockIds.isEmpty() && ticks % 20 == 1) {
			BlockLocator.Result found = BlockLocator.findNearest(player, blockIds, searchRadius);
			if (found != null) {
				LOGGER.info("Gather {}: nearest {} at {} (dist {})", itemId, found.blockId(), found.pos().toShortString(), String.format("%.1f", Math.sqrt(found.distSq())));
				if (BaritoneHelper.isPresent()) {
					BaritoneHelper.pathTo(player, found.pos());
				}
			} else if (ticks % 100 == 1) {
				LOGGER.debug("Gather {}: no {} within {}", itemId, blockIds, searchRadius);
			}
		}
		if (ticks % 100 == 1 && player != null) {
			LOGGER.info("Gather {} x{} waiting — have {} / {} (tick {}/{})", itemId, count, InventoryHelper.countItem(player, itemId), count, ticks, timeoutTicks);
		}
		return null;
	}

	@Override
	public boolean isFinished() {
		ServerPlayerEntity player = firstPlayer();
		if (player != null && InventoryHelper.hasItem(player, itemId, count)) {
			return true;
		}
		return false;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Gather {} stopped at {}/{} (interrupt={})", itemId, ticks, timeoutTicks, interrupt);
	}

	@Override
	protected String toDebugString() {
		ServerPlayerEntity p = firstPlayer();
		int have = p == null ? 0 : InventoryHelper.countItem(p, itemId);
		return "Gather/" + itemId + ":" + have + "/" + count + "@" + ticks;
	}

	private ServerPlayerEntity firstPlayer() {
		try {
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) return list.get(0);
			}
		} catch (Throwable ignored) {}
		return null;
	}

	/** Common item → mineable block mappings. Extend for Astral ores (desh, etc). */
	public static List<String> blockIdsFor(String itemId) {
		if (itemId == null) return List.of();
		return switch (itemId) {
			case "minecraft:iron_ingot" -> List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore");
			case "minecraft:copper_ingot" -> List.of("minecraft:copper_ore", "minecraft:deepslate_copper_ore");
			case "minecraft:gold_ingot" -> List.of("minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore");
			case "minecraft:diamond" -> List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore");
			case "minecraft:netherite_ingot" -> List.of("minecraft:ancient_debris");
			case "minecraft:netherite_sword", "minecraft:diamond_sword" -> List.of("minecraft:diamond_ore", "minecraft:ancient_debris");
			case "ad_astra:desh_ingot" -> List.of("ad_astra:moon_desh_ore", "ad_astra:mars_desh_ore", "ad_astra:desh_ore");
			case "ad_astra:ostrum_ingot" -> List.of("ad_astra:mars_ostrum_ore");
			case "ad_astra:calorite_ingot" -> List.of("ad_astra:venus_calorite_ore", "ad_astra:mercury_calorite_ore");
			case "minecraft:gravel" -> List.of("minecraft:gravel");
			case "minecraft:clay_ball" -> List.of("minecraft:clay");
			case "techreborn:tin_ingot" -> List.of("techreborn:tin_ore");
			default -> List.of();
		};
	}
}
