package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ch0.5–1 phase: gather wood, stone, food, and basic tools.
 * World actions are stubs — structure only until pathing/inventory are wired.
 */
public final class GatherBasicsSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/gather");

	private int ticks;
	private boolean done;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof GatherBasicsSubtask;
	}

	@Override
	protected void onStart() {
		ticks = 0;
		done = false;
		LOGGER.info("Gather basics: wood, cobble, food, crafting table");
	}

	@Override
	protected Task onTick() {
		ticks++;
		// TODO: inventory checks + mine/collect via bot controller
		if (ticks >= 2) {
			done = true;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Gather basics stopped (done={}, interrupt={})", done, interrupt);
	}

	@Override
	public boolean isFinished() {
		return done;
	}

	@Override
	protected String toDebugString() {
		return "GatherBasics";
	}
}
