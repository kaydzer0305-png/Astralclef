package com.ezquest.astralclef.tasks.phases.mars;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MarsDungeonSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mars/dungeon");

	private enum Step {
		LOCATE_DUNGEON,
		CLEAR_DUNGEON,
		RETURN,
		DONE
	}

	private Step step = Step.LOCATE_DUNGEON;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MarsDungeonSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_DUNGEON;
		LOGGER.info("Mars dungeon/return begun");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case LOCATE_DUNGEON:
				step = Step.CLEAR_DUNGEON;
				break;
			case CLEAR_DUNGEON:
				step = Step.RETURN;
				break;
			case RETURN:
				LOGGER.info("Mars return gate complete (stub)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("MarsDungeon stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MarsDungeon/" + step;
	}
}
