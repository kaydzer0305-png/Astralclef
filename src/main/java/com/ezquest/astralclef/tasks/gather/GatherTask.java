package com.ezquest.astralclef.tasks.gather;

import com.ezquest.astralclef.bot.BotActions;
import com.ezquest.astralclef.bot.BotMovement;
import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.movement.BaritoneHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	/** Unbreakable/unreachable targets to skip while scanning. */
	private final Set<BlockPos> skip = new HashSet<>();
	private final Map<BlockPos, Integer> breakFails = new HashMap<>();
	private BlockPos moveTarget;
	private int moveAttempts;

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
		skip.clear();
		breakFails.clear();
		moveTarget = null;
		moveAttempts = 0;
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
		if (player == null || blockIds.isEmpty()) {
			if (ticks % 100 == 1 && player != null) {
				LOGGER.info("Gather {} x{} waiting — have {} / {} (tick {}/{})", itemId, count, InventoryHelper.countItem(player, itemId), count, ticks, timeoutTicks);
			}
			return null;
		}
		BotActions.eatIfHungry(player);
		// Throttle world scans + movement to every 5 ticks.
		if (ticks % 5 == 1) {
			workTarget(player);
		}
		if (ticks % 100 == 1) {
			LOGGER.info("Gather {} x{} waiting — have {} / {} (tick {}/{})", itemId, count, InventoryHelper.countItem(player, itemId), count, ticks, timeoutTicks);
		}
		return null;
	}

	/** One scan → move → break cycle toward the nearest matching block. */
	private void workTarget(ServerPlayerEntity player) {
		BlockLocator.Result found = BlockLocator.findNearest(player, blockIds, searchRadius, skip);
		if (found == null) {
			if (ticks % 100 == 1) {
				LOGGER.debug("Gather {}: no {} within {}", itemId, blockIds, searchRadius);
			}
			return;
		}
		if (ticks % 20 == 1) {
			LOGGER.info("Gather {}: nearest {} at {} (dist {})", itemId, found.blockId(), found.pos().toShortString(), String.format("%.1f", Math.sqrt(found.distSq())));
		}
		if (BaritoneHelper.isPresent()) {
			// Try MineProcess first (mines the ore), fall back to pathTo
			java.util.List<net.minecraft.block.Block> blocks = resolveBlocks(blockIds);
			if (!blocks.isEmpty() && BaritoneHelper.mineBlocks(player, blocks, count)) {
				LOGGER.info("Gather {}: Baritone MineProcess queued for {}", itemId, blocks);
			} else {
				BaritoneHelper.pathTo(player, found.pos());
			}
			return;
		}
		// Autonomous fallback: walk into vacuum range, then survival-break.
		double distCenter = Math.sqrt(player.squaredDistanceTo(Vec3d.ofCenter(found.pos())));
		if (distCenter > 2.2) {
			if (!found.pos().equals(moveTarget)) {
				moveTarget = found.pos();
				moveAttempts = 0;
			}
			moveAttempts++;
			if (moveAttempts > 80) {
				LOGGER.info("Gather {}: cannot reach {} at {} — skipping", itemId, found.blockId(), found.pos().toShortString());
				skip.add(found.pos());
				moveTarget = null;
				moveAttempts = 0;
				return;
			}
			BotMovement.stepToward(player, found.pos(), 2.0, 3.0);
			return;
		}
		moveTarget = null;
		moveAttempts = 0;
		if (BotActions.breakBlock(player, found.pos())) {
			breakFails.remove(found.pos());
		} else {
			int fails = breakFails.getOrDefault(found.pos(), 0) + 1;
			if (fails >= 3) {
				LOGGER.info("Gather {}: giving up on {} at {} (unbreakable?)", itemId, found.blockId(), found.pos().toShortString());
				skip.add(found.pos());
				breakFails.remove(found.pos());
			} else {
				breakFails.put(found.pos(), fails);
			}
		}
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

	private java.util.List<net.minecraft.block.Block> resolveBlocks(List<String> ids) {
		java.util.List<net.minecraft.block.Block> out = new java.util.ArrayList<>();
		for (String s : ids) {
			net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(s);
			if (id == null) continue;
			net.minecraft.block.Block b = net.minecraft.util.registry.Registry.BLOCK.get(id);
			if (id.equals(net.minecraft.util.registry.Registry.BLOCK.getId(b))) out.add(b);
		}
		return out;
	}

	/** Common item → mineable block mappings. Extend for Astral ores (desh, etc). */
	public static List<String> blockIdsFor(String itemId) {
		if (itemId == null) return List.of();
		return switch (itemId) {
			case "minecraft:iron_ingot", "minecraft:raw_iron", "minecraft:iron_nugget" ->
					List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore");
			case "minecraft:copper_ingot", "minecraft:raw_copper" ->
					List.of("minecraft:copper_ore", "minecraft:deepslate_copper_ore");
			case "minecraft:gold_ingot", "minecraft:raw_gold", "minecraft:gold_nugget" ->
					List.of("minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore");
			case "minecraft:diamond" -> List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore");
			case "minecraft:netherite_ingot", "minecraft:netherite_scrap" -> List.of("minecraft:ancient_debris");
			case "minecraft:netherite_sword", "minecraft:diamond_sword" -> List.of("minecraft:diamond_ore", "minecraft:ancient_debris");
			case "ad_astra:desh_ingot", "ad_astra:raw_desh" ->
					List.of("ad_astra:moon_desh_ore", "ad_astra:mars_desh_ore", "ad_astra:desh_ore", "ad_astra:deepslate_desh_ore");
			case "ad_astra:ostrum_ingot", "ad_astra:raw_ostrum" ->
					List.of("ad_astra:mars_ostrum_ore", "ad_astra:deepslate_ostrum_ore");
			case "ad_astra:calorite_ingot", "ad_astra:raw_calorite" ->
					List.of("ad_astra:venus_calorite_ore", "ad_astra:mercury_calorite_ore", "ad_astra:deepslate_calorite_ore");
			case "minecraft:gravel" -> List.of("minecraft:gravel");
			case "minecraft:clay_ball" -> List.of("minecraft:clay");
			case "minecraft:andesite" -> List.of("minecraft:andesite");
			case "minecraft:cobblestone" -> List.of("minecraft:cobblestone", "minecraft:stone");
			case "minecraft:coal" -> List.of("minecraft:coal_ore", "minecraft:deepslate_coal_ore");
			case "create:zinc_ingot", "create:zinc_nugget", "create:raw_zinc" ->
					List.of("create:zinc_ore", "create:deepslate_zinc_ore");
			case "techreborn:tin_ingot", "techreborn:raw_tin" -> List.of("techreborn:tin_ore");
			default -> List.of();
		};
	}
}
