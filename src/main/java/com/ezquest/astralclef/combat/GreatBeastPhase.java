package com.ezquest.astralclef.combat;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Great Beast combat phase (Ch6 gate before singularity craft).
 * Runs as a {@link Task} so it can be driven by TaskRunner or
 * embedded inside {@link com.ezquest.astralclef.tasks.phases.ChAstralSingularityTask}.
 */
public class GreatBeastPhase extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/combat/beast");

	private enum Step { LOCATE_BEAST, ENGAGE, LOOT, DONE }
	private Step step = Step.LOCATE_BEAST;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof GreatBeastPhase;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_BEAST;
		LOGGER.info("Great Beast phase begun — locate → engage → loot");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case LOCATE_BEAST:
				// TODO: locate Great Beast arena/entity
				step = Step.ENGAGE;
				break;
			case ENGAGE:
				// TODO: combat loop (kite, weapon, dodge)
				LOGGER.info("Great Beast engage (stub — no combat AI yet)");
				step = Step.LOOT;
				break;
			case LOOT:
				// TODO: verify drop / quest trigger
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	public void engage() {
		LOGGER.info("Great Beast engage() called (TaskRunner path preferred)");
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("GreatBeast stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "GreatBeast/" + step;
	}
}
