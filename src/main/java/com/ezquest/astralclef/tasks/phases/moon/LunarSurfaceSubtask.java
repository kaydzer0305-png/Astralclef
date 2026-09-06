package com.ezquest.astralclef.tasks.phases.moon;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lunar surface ops: establish base, mine moon resources, tech uplift.
 * Stub — advances immediately; real ore/waypoint logic TODO.
 */
public final class LunarSurfaceSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/moon/surface");

	private enum Step {
		ESTABLISH_BASE,
		MINE_MOON_ORES,
		DONE
	}

	private Step step = Step.ESTABLISH_BASE;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof LunarSurfaceSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.ESTABLISH_BASE;
		LOGGER.info("Lunar surface ops begun");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case ESTABLISH_BASE:
				// Gate on Moon dimension FTB quest (soft — passes when FTB absent/no context)
				if (!isMoonReached()) {
					LOGGER.debug("Moon surface: Moon dimension quest {} not yet complete — waiting", com.ezquest.astralclef.quests.AstralQuests.CH3_MOON_DIMENSION);
					break;
				}
				step = Step.MINE_MOON_ORES;
				break;
			case MINE_MOON_ORES:
				// TODO: deepslate/cheese gather via GatherTask (desh etc after return)
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	private boolean isMoonReached() {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx == null || !ctx.isValid() || ctx.getWorld() == null || ctx.getWorld().getServer() == null) return true;
			var server = ctx.getWorld().getServer();
			if (!com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestsPresent(server)) return true;
			var player = server.getPlayerManager().getPlayerList().isEmpty() ? null : server.getPlayerManager().getPlayerList().get(0);
			if (player == null) return true;
			return com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestComplete(server, player, com.ezquest.astralclef.quests.AstralQuests.CH3_MOON_DIMENSION);
		} catch (Throwable t) { return true; }
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("LunarSurface stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MoonSurface/" + step;
	}
}
