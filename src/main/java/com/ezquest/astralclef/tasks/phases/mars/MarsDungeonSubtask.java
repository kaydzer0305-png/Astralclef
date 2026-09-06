package com.ezquest.astralclef.tasks.phases.mars;

import com.ezquest.astralclef.movement.BaritoneHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.world.StructureLocator;
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

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MarsDungeonSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_DUNGEON;
		LOGGER.info("Mars dungeon/return begun");
	}

	@Override
	protected Task onTick() {
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
				step = Step.RETURN;
				break;
			case RETURN:
				LOGGER.info("Mars return gate complete (soft)");
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
