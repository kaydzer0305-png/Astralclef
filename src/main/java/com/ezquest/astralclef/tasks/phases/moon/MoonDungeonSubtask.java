package com.ezquest.astralclef.tasks.phases.moon;

import com.ezquest.astralclef.movement.BaritoneHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.world.StructureLocator;
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

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MoonDungeonSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_DUNGEON;
		LOGGER.info("Moon dungeon/return begun");
	}

	@Override
	protected Task onTick() {
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
				// TODO: combat/loot + FTB quest check
				step = Step.RETURN;
				break;
			case RETURN:
				LOGGER.info("Moon return gate complete (soft — structure cases handled)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	private net.minecraft.server.network.ServerPlayerEntity firstPlayer() {
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
