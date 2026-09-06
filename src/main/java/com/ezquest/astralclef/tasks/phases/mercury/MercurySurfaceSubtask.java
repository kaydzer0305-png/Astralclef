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
			case ESTABLISH_BASE: step = Step.MINE_MERCURY_ORES; break;
			case MINE_MERCURY_ORES: step = Step.DONE; break;
			case DONE: break;
		}
		return null;
	}
	@Override protected void onStop(Task interrupt) { LOGGER.debug("MercurySurface stopped at {} (interrupt={})", step, interrupt); }
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "MercurySurface/" + step; }
}
