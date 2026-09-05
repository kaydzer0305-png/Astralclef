package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alloy / Casing stub: Welcome to Create → Bronze → Compound → Alloy → Casing.
 * <ul>
 *   <li>Bronze = smith Copper+Tin (not Brass)</li>
 *   <li>Andesite Compound → furnace smelt (quests26)</li>
 *   <li>Andesite Alloy stockpile (quests5)</li>
 *   <li>Andesite Casing (strip log + R-click alloy)</li>
 * </ul>
 * SNBT edges unverified for quests26 / quests5.
 */
public final class AlloyCasingSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/alloy");

	private enum Step {
		WELCOME_CREATE,
		BRONZE_SMITH,
		COMPOUND_SMELT,
		ALLOY_STOCKPILE,
		ANDESITE_CASING,
		DONE
	}

	private Step step = Step.WELCOME_CREATE;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof AlloyCasingSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.WELCOME_CREATE;
		LOGGER.info("Alloy/Casing: Bronze → Compound (quests26) → Alloy (quests5) → Casing");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case WELCOME_CREATE:
				// TODO: Welcome to Create quest handshake
				step = Step.BRONZE_SMITH;
				break;
			case BRONZE_SMITH:
				CreateRecipeKinds.bronzeSmith("astral:smithing/bronze");
				step = Step.COMPOUND_SMELT;
				break;
			case COMPOUND_SMELT:
				CreateRecipeKinds.compoundSmelt("astral:smelting/andesite_compound");
				step = Step.ALLOY_STOCKPILE;
				break;
			case ALLOY_STOCKPILE:
				// Early craft path; mixer alloy cheaper later
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/materials/andesite_alloy");
				step = Step.ANDESITE_CASING;
				break;
			case ANDESITE_CASING:
				// TODO: strip log + right-click andesite alloy → casing
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Alloy/Casing stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "AlloyCasing/" + step;
	}
}
