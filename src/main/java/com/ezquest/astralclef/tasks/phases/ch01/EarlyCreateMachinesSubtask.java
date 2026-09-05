package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ch0.5–1 phase: craft/place early Create kinetic chain
 * (shaft, cogwheel, hand crank / water wheel, mechanical press).
 */
public final class EarlyCreateMachinesSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/machines");

	private enum Step {
		CRAFT_KINETICS,
		PLACE_POWER,
		PLACE_PRESS,
		DONE
	}

	private Step step = Step.CRAFT_KINETICS;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof EarlyCreateMachinesSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.CRAFT_KINETICS;
		LOGGER.info("Early Create machines: kinetics + press");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case CRAFT_KINETICS:
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/shaft");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/cogwheel");
				step = Step.PLACE_POWER;
				break;
			case PLACE_POWER:
				// TODO: place hand crank / water wheel and connect shafts
				step = Step.PLACE_PRESS;
				break;
			case PLACE_PRESS:
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/mechanical_press");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.SEQUENCED_ASSEMBLY,
						"create:sequenced_assembly/precision_mechanism");
				// Filling hook reserved for early fluid recipes (e.g. honey/lava stubs)
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.FILLING,
						"create:filling/sweet_roll");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Early Create machines stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "EarlyCreateMachines/" + step;
	}
}
