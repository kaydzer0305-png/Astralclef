package com.ezquest.astralclef.tasks.phases;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import com.ezquest.astralclef.tasks.phases.ch01.AndesiteAlloySubtask;
import com.ezquest.astralclef.tasks.phases.ch01.EarlyCreateMachinesSubtask;
import com.ezquest.astralclef.tasks.phases.ch01.GatherBasicsSubtask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chapter 0.5–1: Getting Started → early Andesite / Create.
 * Phase state machine that returns nested subtasks to {@link com.ezquest.astralclef.task.TaskRunner}.
 * Does not implement Moon/Mars/Mercury/Singularity.
 */
public class Ch01GettingStartedTask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01");

	public enum Phase {
		GATHER_BASICS,
		ANDESITE_ALLOY,
		EARLY_CREATE_MACHINES,
		COMPLETE
	}

	private Phase phase = Phase.GATHER_BASICS;
	private Task currentSubtask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof Ch01GettingStartedTask;
	}

	@Override
	protected void onStart() {
		phase = Phase.GATHER_BASICS;
		currentSubtask = new GatherBasicsSubtask();
		CreateRecipeKinds.init();
		LOGGER.info("Ch0.5–1 Getting Started begun");
	}

	@Override
	protected Task onTick() {
		if (phase == Phase.COMPLETE) {
			return null;
		}

		if (currentSubtask != null && currentSubtask.isFinished()) {
			advance();
		}

		if (phase == Phase.COMPLETE) {
			return null;
		}

		if (currentSubtask == null) {
			currentSubtask = createSubtaskFor(phase);
		}

		return currentSubtask;
	}

	private void advance() {
		Phase previous = phase;
		switch (phase) {
			case GATHER_BASICS:
				phase = Phase.ANDESITE_ALLOY;
				currentSubtask = new AndesiteAlloySubtask();
				break;
			case ANDESITE_ALLOY:
				phase = Phase.EARLY_CREATE_MACHINES;
				currentSubtask = new EarlyCreateMachinesSubtask();
				break;
			case EARLY_CREATE_MACHINES:
				phase = Phase.COMPLETE;
				currentSubtask = null;
				break;
			case COMPLETE:
				currentSubtask = null;
				break;
		}
		LOGGER.info("Ch01 phase {} -> {}", previous, phase);
	}

	private static Task createSubtaskFor(Phase phase) {
		switch (phase) {
			case GATHER_BASICS:
				return new GatherBasicsSubtask();
			case ANDESITE_ALLOY:
				return new AndesiteAlloySubtask();
			case EARLY_CREATE_MACHINES:
				return new EarlyCreateMachinesSubtask();
			default:
				return null;
		}
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.info("Ch0.5–1 stopped at phase {} (interrupt={})", phase, interrupt);
		currentSubtask = null;
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
		return "Ch01GettingStarted/" + phase;
	}
}
