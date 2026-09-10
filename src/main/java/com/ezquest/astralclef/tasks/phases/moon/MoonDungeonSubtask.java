package com.ezquest.astralclef.tasks.phases.moon;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.movement.BaritoneHelper;
import com.ezquest.astralclef.quests.AstralQuests;
import com.ezquest.astralclef.quests.FtbQuestsHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import com.ezquest.astralclef.world.StructureLocator;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moon dungeon / return gate. Stub for the lunar dungeon boss or
 * collection gate that unlocks Mars progression.
 */
public final class MoonDungeonSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/moon/dungeon");

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
		return other instanceof MoonDungeonSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_DUNGEON;
		gatherTask = null;
		ticks = 0;
		LOGGER.info("Moon dungeon/return begun");
	}

	@Override
	protected Task onTick() {
		ticks++;
		var player = firstPlayer();
		switch (step) {
			case LOCATE_DUNGEON:
				if (player != null) {
					// Try common moon structure ids; fall back to stub
					String[] candidates = {"ad_astra:moon_ruins", "minecraft:desert_pyramid", "minecraft:village"};
					for (String sid : candidates) {
						dungeonPos = StructureLocator.locateNearest(player, sid);
						if (dungeonPos != null) break;
					}
					if (dungeonPos != null) {
						LOGGER.info("Moon dungeon located at {}", dungeonPos.toShortString());
						step = Step.TRAVEL_TO_DUNGEON;
						break;
					}
					LOGGER.info("Moon dungeon: no structure found via locate — advancing as stub");
				}
				step = Step.TRAVEL_TO_DUNGEON;
				break;
			case TRAVEL_TO_DUNGEON:
				if (dungeonPos != null && player != null) {
					if (player.getBlockPos().getSquaredDistance(dungeonPos) < 9) {
						LOGGER.info("Moon dungeon: arrived at {}", dungeonPos.toShortString());
						step = Step.CLEAR_DUNGEON;
						break;
					}
					if (BaritoneHelper.isPresent()) {
						BaritoneHelper.pathTo(player, dungeonPos);
						break;
					}
					// Without Baritone, we can't path — advance as stub after one tick
					LOGGER.info("Moon dungeon: Baritone absent, skipping travel for {}", dungeonPos.toShortString());
				}
				step = Step.CLEAR_DUNGEON;
				break;
			case CLEAR_DUNGEON:
				// Clear = hold moon loot (desh) or the Ch4 desh quest; otherwise
				// stockpile desh and wait (no automated combat in this stub).
				if (player == null
						|| InventoryHelper.hasAny(player, "ad_astra:desh_ingot", "ad_astra:raw_desh")
						|| isQuestDone(AstralQuests.CH4_DESH)) {
					gatherTask = null;
					step = Step.RETURN;
					break;
				}
				if (gatherTask == null || gatherTask.isFinished()) {
					gatherTask = new GatherTask("ad_astra:desh_ingot", 4);
					LOGGER.info("Moon dungeon: stockpiling desh loot via GatherTask");
				}
				if (ticks % 100 == 1) {
					LOGGER.info("Moon dungeon: clear the dungeon and loot desh (quest {})",
							AstralQuests.CH4_DESH);
				}
				return gatherTask;
			case RETURN:
				LOGGER.info("Moon return gate complete (soft — structure cases handled)");
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
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) return list.get(0);
			}
		} catch (Throwable ignored) {}
		return null;
	}

	private boolean isQuestDone(String questId) {
		try {
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx == null || !ctx.isValid() || ctx.getWorld() == null || ctx.getWorld().getServer() == null) {
				return false;
			}
			var server = ctx.getWorld().getServer();
			if (!FtbQuestsHelper.isQuestsPresent(server)) {
				return false;
			}
			var player = server.getPlayerManager().getPlayerList().isEmpty()
					? null : server.getPlayerManager().getPlayerList().get(0);
			if (player == null) {
				return false;
			}
			return FtbQuestsHelper.isQuestComplete(server, player, questId);
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("MoonDungeon stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MoonDungeon/" + step;
	}
}
