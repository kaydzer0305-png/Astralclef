package com.ezquest.astralclef.tasks.phases.moon;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.world.AdAstraRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launch prep: oxygen, suit, rocket assembly, fueling, launch pad.
 * Stub steps advance tick-by-tick so the task is observable via
 * {@code /astralclef status}; real inventory/gating TODO.
 */
public final class LaunchPrepSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/moon/launch");

	private enum Step {
		OXYGEN_AND_SUIT,
		ROCKET_ASSEMBLY,
		FUEL_AND_PAD,
		LAUNCH,
		DONE
	}

	private Step step = Step.OXYGEN_AND_SUIT;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof LaunchPrepSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.OXYGEN_AND_SUIT;
		LOGGER.info("Moon launch prep: oxygen/suit → rocket → fuel/pad → launch ({})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MOON));
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case OXYGEN_AND_SUIT:
				// TODO: oxygen distributor/collector, suit, air
				LOGGER.debug("launch prep: oxygen/suit check");
				step = Step.ROCKET_ASSEMBLY;
				break;
			case ROCKET_ASSEMBLY:
				// TODO: Ad Astra T2 rocket + structure
				step = Step.FUEL_AND_PAD;
				break;
			case FUEL_AND_PAD:
				// TODO: fuel, pad validation
				step = Step.LAUNCH;
				break;
			case LAUNCH:
				LOGGER.info("Moon launch committed (stub — no actual entity launch)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("LaunchPrep stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MoonLaunch/" + step;
	}
}
