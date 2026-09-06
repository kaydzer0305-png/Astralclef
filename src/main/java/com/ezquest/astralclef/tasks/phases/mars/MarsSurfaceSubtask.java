package com.ezquest.astralclef.tasks.phases.mars;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MarsSurfaceSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mars/surface");

	private enum Step {
		ESTABLISH_BASE,
		MINE_MARS_ORES,
		DONE
	}

	private Step step = Step.ESTABLISH_BASE;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MarsSurfaceSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.ESTABLISH_BASE;
		LOGGER.info("Mars surface ops begun");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case ESTABLISH_BASE:
				// TODO: shelter, thermal regulation
				step = Step.MINE_MARS_ORES;
				break;
			case MINE_MARS_ORES:
				// TODO: mars-specific ores, quests
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
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
