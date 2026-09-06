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
				// TODO: shelter, oxygen sealing, waypoints
				step = Step.MINE_MOON_ORES;
				break;
			case MINE_MOON_ORES:
				// TODO: deepslate/cheese, FTB quest triggers
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
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
