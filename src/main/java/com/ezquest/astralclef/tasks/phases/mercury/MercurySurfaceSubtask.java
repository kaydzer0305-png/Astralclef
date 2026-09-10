package com.ezquest.astralclef.tasks.phases.mercury;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class MercurySurfaceSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mercury/surface");

	/** Calorite stockpile for the Ch6 singularity line. */
	private static final String CALORITE_INGOT = "ad_astra:calorite_ingot";
	private static final List<String> MERCURY_CALORITE_BLOCKS =
			List.of("ad_astra:mercury_calorite_ore", "ad_astra:venus_calorite_ore", "ad_astra:deepslate_calorite_ore");

	private enum Step { ESTABLISH_BASE, MINE_MERCURY_ORES, DONE }
	private Step step = Step.ESTABLISH_BASE;
	private Task gatherTask;

	@Override public boolean isEqual(Task other) { return other instanceof MercurySurfaceSubtask; }
	@Override protected void onStart() {
		step = Step.ESTABLISH_BASE;
		gatherTask = null;
		LOGGER.info("Mercury surface ops begun");
	}
	@Override protected Task onTick() {
		ServerPlayerEntity player = firstPlayer();
		switch (step) {
			case ESTABLISH_BASE:
				if (!isGlacioReached()) { LOGGER.debug("Mercury surface: Glacio dim {} not yet", com.ezquest.astralclef.quests.AstralQuests.CH5_GLACIO_DIMENSION); break; }
				step = Step.MINE_MERCURY_ORES; break;
			case MINE_MERCURY_ORES:
				if (player != null && !InventoryHelper.hasItem(player, CALORITE_INGOT, 4)) {
					if (gatherTask == null || gatherTask.isFinished()) {
						gatherTask = new GatherTask(CALORITE_INGOT, 8, MERCURY_CALORITE_BLOCKS, 32, 1200);
						LOGGER.info("Mercury surface: mining calorite via GatherTask");
					}
					return gatherTask;
				}
				gatherTask = null;
				step = Step.DONE;
				break;
			case DONE: break;
		}
		if (gatherTask != null && gatherTask.isFinished()) gatherTask = null;
		return null;
	}

	private boolean isGlacioReached() {
		try {
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx == null || !ctx.isValid() || ctx.getWorld() == null || ctx.getWorld().getServer() == null) return true;
			var server = ctx.getWorld().getServer();
			if (!com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestsPresent(server)) return true;
			var player = server.getPlayerManager().getPlayerList().isEmpty() ? null : server.getPlayerManager().getPlayerList().get(0);
			if (player == null) return true;
			return com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestComplete(server, player, com.ezquest.astralclef.quests.AstralQuests.CH5_GLACIO_DIMENSION);
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

	@Override protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("MercurySurface stopped at {} (interrupt={})", step, interrupt);
	}
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "MercurySurface/" + step; }
}
