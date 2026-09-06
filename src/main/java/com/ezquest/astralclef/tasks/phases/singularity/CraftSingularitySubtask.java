package com.ezquest.astralclef.tasks.phases.singularity;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CraftSingularitySubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/singularity/craft");
	private enum Step { GATHER_SINGULARITY_INPUTS, CREATE_SINGULARITY, DONE }
	private Step step = Step.GATHER_SINGULARITY_INPUTS;

	@Override public boolean isEqual(Task other) { return other instanceof CraftSingularitySubtask; }
	@Override protected void onStart() { step = Step.GATHER_SINGULARITY_INPUTS; LOGGER.info("Singularity craft: gather → create"); }
	@Override protected Task onTick() {
		switch (step) {
			case GATHER_SINGULARITY_INPUTS: // TODO: mercury/mars mats, Create sequenced assembly
				step = Step.CREATE_SINGULARITY; break;
			case CREATE_SINGULARITY: // TODO: astral singularity craft via CreateRecipeKinds
				LOGGER.info("Astral Singularity craft complete (stub)"); step = Step.DONE; break;
			case DONE: break;
		}
		return null;
	}
	@Override protected void onStop(Task interrupt) { LOGGER.debug("CraftSingularity stopped at {} (interrupt={})", step, interrupt); }
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "SingularityCraft/" + step; }
}
