package com.ezquest.astralclef.tasks.phases.moon;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moon dungeon / return gate. Stub for the lunar dungeon boss or
 * collection gate that unlocks Mars progression.
 */
public final class MoonDungeonSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/moon/dungeon");

	private enum Step {
		LOCATE_DUNGEON,
		CLEAR_DUNGEON,
		RETURN,
		DONE
	}

	private Step step = Step.LOCATE_DUNGEON;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MoonDungeonSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_DUNGEON;
		LOGGER.info("Moon dungeon/return begun");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case LOCATE_DUNGEON:
				// TODO: locate dungeon structure
				step = Step.CLEAR_DUNGEON;
				break;
			case CLEAR_DUNGEON:
				// TODO: combat/loot checks
				step = Step.RETURN;
				break;
			case RETURN:
				LOGGER.info("Moon return gate complete (stub)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("MoonDungeon stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MoonDungeon/" + step;
	}
}
