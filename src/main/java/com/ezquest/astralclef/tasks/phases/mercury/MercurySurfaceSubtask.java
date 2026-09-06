package com.ezquest.astralclef.tasks.phases.mercury;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercurySurfaceSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mercury/surface");
	private enum Step { ESTABLISH_BASE, MINE_MERCURY_ORES, DONE }
	private Step step = Step.ESTABLISH_BASE;

	@Override public boolean isEqual(Task other) { return other instanceof MercurySurfaceSubtask; }
	@Override protected void onStart() { step = Step.ESTABLISH_BASE; LOGGER.info("Mercury surface ops begun"); }
	@Override protected Task onTick() {
		switch (step) {
			case ESTABLISH_BASE:
				if (!isGlacioReached()) { LOGGER.debug("Mercury surface: Glacio dim {} not yet", com.ezquest.astralclef.quests.AstralQuests.CH5_GLACIO_DIMENSION); break; }
				step = Step.MINE_MERCURY_ORES; break;
			case MINE_MERCURY_ORES: step = Step.DONE; break;
			case DONE: break;
		}
		return null;
	}

	private boolean isGlacioReached() {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx == null || !ctx.isValid() || ctx.getWorld() == null || ctx.getWorld().getServer() == null) return true;
			var server = ctx.getWorld().getServer();
			if (!com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestsPresent(server)) return true;
			var player = server.getPlayerManager().getPlayerList().isEmpty() ? null : server.getPlayerManager().getPlayerList().get(0);
			if (player == null) return true;
			return com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestComplete(server, player, com.ezquest.astralclef.quests.AstralQuests.CH5_GLACIO_DIMENSION);
		} catch (Throwable t) { return true; }
	}
	@Override protected void onStop(Task interrupt) { LOGGER.debug("MercurySurface stopped at {} (interrupt={})", step, interrupt); }
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "MercurySurface/" + step; }
}
