package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grout via Mixer — Chapter 2 (Andesite World) unlock gate.
 * Binding: {@link Ch01RecipeIds#GROUT}
 * (andesite_alloy + zinc_ingot + 8 gravel → 8 tconstruct:grout).
 * FTB: quests25.
 */
public final class GroutGateSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/grout");

	private enum Step {
		MIX_GROUT,
		DONE
	}

	private Step step = Step.MIX_GROUT;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof GroutGateSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.MIX_GROUT;
		LOGGER.info("Grout gate: Mixer grout {} → Chapter 2 unlock", Ch01RecipeIds.GROUT);
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case MIX_GROUT:
				CreateRecipeKinds.grout(Ch01RecipeIds.GROUT);
				CreateRecipeExecutor exec = CreateRecipeExecutor.getInstance();
				if (!exec.isDone(CreateRecipeKinds.Kind.GROUT, Ch01RecipeIds.GROUT)) {
					break;
				}
				if (!exec.isSuccess(CreateRecipeKinds.Kind.GROUT, Ch01RecipeIds.GROUT)) {
					LOGGER.warn("Grout job finished without success — continuing to unlock gate");
				}
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Grout gate stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "GroutGate/" + step;
	}
}
