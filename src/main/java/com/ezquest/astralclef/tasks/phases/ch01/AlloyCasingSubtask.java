package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alloy / Casing: Welcome to Create → Bronze → Compound craft → Compound smelt → Alloy → Casing.
 * <ul>
 *   <li>Bronze = smith Copper+Tin ({@link Ch01RecipeIds#BRONZE_SMITH})</li>
 *   <li>Andesite Compound shaped BBB/AAA/CCC ({@link Ch01RecipeIds#ANDESITE_COMPOUND_SHAPED})</li>
 *   <li>Compound → furnace/blast alloy (quests26) — stock Create alloy recipes removed in Astral</li>
 *   <li>Andesite Casing (strip log + R-click alloy)</li>
 * </ul>
 */
public final class AlloyCasingSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/alloy");

	private enum Step {
		WELCOME_CREATE,
		BRONZE_SMITH,
		COMPOUND_SHAPED,
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
		LOGGER.info("Alloy/Casing: Bronze → Compound shaped → Smelt/Blast → Alloy → Casing");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case WELCOME_CREATE:
				step = Step.BRONZE_SMITH;
				break;
			case BRONZE_SMITH:
				if (!awaitKind(CreateRecipeKinds.Kind.BRONZE_SMITH, Ch01RecipeIds.BRONZE_SMITH,
						() -> CreateRecipeKinds.bronzeSmith(Ch01RecipeIds.BRONZE_SMITH))) {
					break;
				}
				step = Step.COMPOUND_SHAPED;
				break;
			case COMPOUND_SHAPED:
				if (!awaitKind(CreateRecipeKinds.Kind.MECHANICAL_CRAFTING, Ch01RecipeIds.ANDESITE_COMPOUND_SHAPED,
						() -> CreateRecipeKinds.compoundShaped(Ch01RecipeIds.ANDESITE_COMPOUND_SHAPED))) {
					break;
				}
				step = Step.COMPOUND_SMELT;
				break;
			case COMPOUND_SMELT:
				CreateRecipeKinds.compoundBlast(Ch01RecipeIds.ANDESITE_COMPOUND_BLAST);
				if (!awaitKind(CreateRecipeKinds.Kind.COMPOUND_SMELT, Ch01RecipeIds.ANDESITE_COMPOUND_SMELT,
						() -> CreateRecipeKinds.compoundSmelt(Ch01RecipeIds.ANDESITE_COMPOUND_SMELT))) {
					break;
				}
				step = Step.ALLOY_STOCKPILE;
				break;
			case ALLOY_STOCKPILE:
				// Stockpile is inventory goal; smelt/blast already produce alloy
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

	/**
	 * Fire recipe kind and wait until the job is done (success or fail).
	 * @return true when finished and caller may advance
	 */
	private boolean awaitKind(CreateRecipeKinds.Kind kind, String bindId, Runnable fire) {
		fire.run();
		CreateRecipeExecutor exec = CreateRecipeExecutor.getInstance();
		if (!exec.isDone(kind, bindId)) {
			return false;
		}
		if (!exec.isSuccess(kind, bindId)) {
			LOGGER.warn("Alloy step {} finished without success — continuing", bindId);
		}
		return true;
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
