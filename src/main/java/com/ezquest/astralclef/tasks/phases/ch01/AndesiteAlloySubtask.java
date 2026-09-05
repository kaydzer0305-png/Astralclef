package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ch0.5–1 phase: andesite + iron nuggets / zinc path → andesite alloy.
 * Hooks Create recipe kinds (crafting / basin) as stubs.
 */
public final class AndesiteAlloySubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/andesite");

	private enum Step {
		COLLECT_ANDESITE,
		COLLECT_NUGGETS,
		CRAFT_ALLOY,
		DONE
	}

	private Step step = Step.COLLECT_ANDESITE;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof AndesiteAlloySubtask;
	}

	@Override
	protected void onStart() {
		step = Step.COLLECT_ANDESITE;
		LOGGER.info("Andesite alloy path started");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case COLLECT_ANDESITE:
				// TODO: locate/mine andesite
				step = Step.COLLECT_NUGGETS;
				break;
			case COLLECT_NUGGETS:
				// TODO: iron nuggets or zinc (Create Astral pack recipes)
				step = Step.CRAFT_ALLOY;
				break;
			case CRAFT_ALLOY:
				// Prefer mechanical crafting when available; basin mix is a fallback hook.
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/materials/andesite_alloy");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.BASIN,
						"create:mixing/andesite_alloy");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Andesite alloy stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "AndesiteAlloy/" + step;
	}
}
