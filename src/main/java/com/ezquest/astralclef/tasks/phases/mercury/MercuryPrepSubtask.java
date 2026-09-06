package com.ezquest.astralclef.tasks.phases.mercury;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.world.AdAstraRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercuryPrepSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mercury/prep");

	private enum Step { EXTREME_THERMAL, T4_ROCKET, FUEL_AND_PAD, LAUNCH, DONE }
	private Step step = Step.EXTREME_THERMAL;

	@Override public boolean isEqual(Task other) { return other instanceof MercuryPrepSubtask; }

	@Override protected void onStart() {
		step = Step.EXTREME_THERMAL;
		LOGGER.info("Mercury prep: extreme thermal → T4 → fuel/pad → launch ({})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MERCURY));
	}

	@Override protected Task onTick() {
		switch (step) {
			case EXTREME_THERMAL: step = Step.T4_ROCKET; break;
			case T4_ROCKET: step = Step.FUEL_AND_PAD; break;
			case FUEL_AND_PAD: step = Step.LAUNCH; break;
			case LAUNCH: LOGGER.info("Mercury launch committed (stub)"); step = Step.DONE; break;
			case DONE: break;
		}
		return null;
	}

	@Override protected void onStop(Task interrupt) { LOGGER.debug("MercuryPrep stopped at {} (interrupt={})", step, interrupt); }
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "MercuryPrep/" + step; }
}
