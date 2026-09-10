package com.ezquest.astralclef.tasks.phases.mars;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class MarsSurfaceSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mars/surface");

	/** Ostrum stockpile for the Mercury gate (T4 rocket line). */
	private static final String OSTRUM_INGOT = "ad_astra:ostrum_ingot";
	private static final List<String> MARS_OSTRUM_BLOCKS =
			List.of("ad_astra:mars_ostrum_ore", "ad_astra:deepslate_ostrum_ore");

	private enum Step {
		ESTABLISH_BASE,
		MINE_MARS_ORES,
		DONE
	}

	private Step step = Step.ESTABLISH_BASE;
	private Task gatherTask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MarsSurfaceSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.ESTABLISH_BASE;
		gatherTask = null;
		LOGGER.info("Mars surface ops begun");
	}

	@Override
	protected Task onTick() {
		ServerPlayerEntity player = firstPlayer();
		switch (step) {
			case ESTABLISH_BASE:
				if (!isMarsReached()) {
					LOGGER.debug("Mars surface: Mars dim {} not yet complete", com.ezquest.astralclef.quests.AstralQuests.CH4_MARS_DIMENSION);
					break;
				}
				step = Step.MINE_MARS_ORES;
				break;
			case MINE_MARS_ORES:
				if (player != null && !InventoryHelper.hasItem(player, OSTRUM_INGOT, 4)) {
					if (gatherTask == null || gatherTask.isFinished()) {
						gatherTask = new GatherTask(OSTRUM_INGOT, 8, MARS_OSTRUM_BLOCKS, 32, 1200);
						LOGGER.info("Mars surface: mining ostrum via GatherTask");
					}
					return gatherTask;
				}
				gatherTask = null;
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

	private boolean isMarsReached() {
		try {
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx == null || !ctx.isValid() || ctx.getWorld() == null || ctx.getWorld().getServer() == null) return true;
			var server = ctx.getWorld().getServer();
			if (!com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestsPresent(server)) return true;
			var player = server.getPlayerManager().getPlayerList().isEmpty() ? null : server.getPlayerManager().getPlayerList().get(0);
			if (player == null) return true;
			return com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestComplete(server, player, com.ezquest.astralclef.quests.AstralQuests.CH4_MARS_DIMENSION);
		} catch (Throwable t) { return true; }
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

	@Override
	protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("MarsSurface stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MarsSurface/" + step;
	}
}
