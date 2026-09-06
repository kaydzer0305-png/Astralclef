package com.ezquest.astralclef.tasks.phases;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full pack progression: Ch01 → Moon → Mars → Mercury → Singularity.
 * Single entry for {@code /astralclef auto}. Each phase is itself a Task
 * so TaskRunner drives it tick-by-tick and {@code /astralclef status}
 * shows {@code Auto/PHASE}.
 */
public class FullProgressionTask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/auto");

	public enum Phase {
		CH01,
		MOON,
		MARS,
		MERCURY,
		SINGULARITY,
		COMPLETE
	}

	private Phase phase = Phase.CH01;
	private Task activeSubtask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof FullProgressionTask;
	}

	@Override
	protected void onStart() {
		phase = Phase.CH01;
		activeSubtask = null;
		LOGGER.info("Full progression (auto) begun — ch01 → moon → mars → mercury → singularity");
	}

	@Override
	protected Task onTick() {
		switch (phase) {
			case CH01:
				return drive(new Ch01GettingStartedTask(), Phase.MOON);
			case MOON:
				return drive(new ChMoonTask(), Phase.MARS);
			case MARS:
				return drive(new ChMarsTask(), Phase.MERCURY);
			case MERCURY:
				return drive(new ChMercuryTask(), Phase.SINGULARITY);
			case SINGULARITY:
				return drive(new ChAstralSingularityTask(), Phase.COMPLETE);
			case COMPLETE:
				return null;
		}
		return null;
	}

	private Task drive(Task candidate, Phase next) {
		if (activeSubtask != null && activeSubtask.isEqual(candidate) && activeSubtask.isFinished()) {
			advance(next);
			return null;
		}
		if (activeSubtask == null || !activeSubtask.isEqual(candidate)) {
			activeSubtask = candidate;
		}
		return activeSubtask;
	}

	private void advance(Phase next) {
		LOGGER.info("Auto progression {} -> {}", phase, next);
		phase = next;
		activeSubtask = null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.info("Auto progression stopped at {} (interrupt={})", phase, interrupt);
		activeSubtask = null;
	}

	@Override
	public boolean isFinished() {
		return phase == Phase.COMPLETE;
	}

	public Phase getPhase() {
		return phase;
	}

	@Override
	protected String toDebugString() {
		return "Auto/" + phase;
	}
}
