package com.ezquest.astralclef.tasks.phases.mars;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.world.AdAstraRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MarsPrepSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mars/prep");

	private enum Step {
		THERMAL_AND_OXYGEN,
		T3_ROCKET,
		FUEL_AND_PAD,
		LAUNCH,
		DONE
	}

	private Step step = Step.THERMAL_AND_OXYGEN;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MarsPrepSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.THERMAL_AND_OXYGEN;
		LOGGER.info("Mars prep: thermal/oxygen → T3 → fuel/pad → launch ({})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MARS));
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case THERMAL_AND_OXYGEN:
				// TODO: thermal padding, oxygen upgrades
				step = Step.T3_ROCKET;
				break;
			case T3_ROCKET:
				// TODO: Ad Astra T3 assembly
				step = Step.FUEL_AND_PAD;
				break;
			case FUEL_AND_PAD:
				step = Step.LAUNCH;
				break;
			case LAUNCH:
				LOGGER.info("Mars launch committed (stub)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("MarsPrep stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MarsPrep/" + step;
	}
}
