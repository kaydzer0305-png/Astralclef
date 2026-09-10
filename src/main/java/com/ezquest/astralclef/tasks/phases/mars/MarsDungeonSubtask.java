package com.ezquest.astralclef.tasks.phases.mars;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.movement.BaritoneHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import com.ezquest.astralclef.world.StructureLocator;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MarsDungeonSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mars/dungeon");

	private enum Step {
		LOCATE_DUNGEON,
		TRAVEL_TO_DUNGEON,
		CLEAR_DUNGEON,
		RETURN,
		DONE
	}

	private Step step = Step.LOCATE_DUNGEON;
	private BlockPos dungeonPos;
	private Task gatherTask;
	private int ticks;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MarsDungeonSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_DUNGEON;
		gatherTask = null;
		ticks = 0;
		LOGGER.info("Mars dungeon/return begun");
	}

	@Override
	protected Task onTick() {
		ticks++;
		var player = firstPlayer();
		switch (step) {
			case LOCATE_DUNGEON:
				if (player != null) {
					String[] cands = {"ad_astra:mars_ruins", "minecraft:village", "minecraft:desert_pyramid"};
					for (String sid : cands) { dungeonPos = StructureLocator.locateNearest(player, sid); if (dungeonPos != null) break; }
					if (dungeonPos != null) { LOGGER.info("Mars dungeon at {}", dungeonPos.toShortString()); step = Step.TRAVEL_TO_DUNGEON; break; }
				}
				step = Step.TRAVEL_TO_DUNGEON;
				break;
			case TRAVEL_TO_DUNGEON:
				if (dungeonPos != null && player != null) {
					if (player.getBlockPos().getSquaredDistance(dungeonPos) < 9) { step = Step.CLEAR_DUNGEON; break; }
					if (BaritoneHelper.isPresent()) { BaritoneHelper.pathTo(player, dungeonPos); break; }
					LOGGER.info("Mars dungeon travel stub for {}", dungeonPos.toShortString());
				}
				step = Step.CLEAR_DUNGEON;
				break;
			case CLEAR_DUNGEON:
				// Clear = hold Mars loot (ostrum); otherwise stockpile ostrum and
				// wait (no automated combat in this stub).
				if (player == null
						|| InventoryHelper.hasAny(player, "ad_astra:ostrum_ingot", "ad_astra:raw_ostrum")) {
					gatherTask = null;
					step = Step.RETURN;
					break;
				}
				if (gatherTask == null || gatherTask.isFinished()) {
					gatherTask = new GatherTask("ad_astra:ostrum_ingot", 4);
					LOGGER.info("Mars dungeon: stockpiling ostrum loot via GatherTask");
				}
				if (ticks % 100 == 1) {
					LOGGER.info("Mars dungeon: clear the dungeon and loot ostrum");
				}
				return gatherTask;
			case RETURN:
				LOGGER.info("Mars return gate complete (soft)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		if (gatherTask != null && gatherTask.isFinished()) {
			gatherTask = null;
		}
		return null;
	}

	private ServerPlayerEntity firstPlayer() {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) return list.get(0);
			}
		} catch (Throwable ignored) {}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("MarsDungeon stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MarsDungeon/" + step;
	}
}
