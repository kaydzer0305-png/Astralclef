package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mixer loop: kinetics → Millstone / Press / Mixer+Basin → sheets and utilities.
 * <p>
 * Early mixer uses {@link Ch01RecipeIds#MIXER_BASIN_MIX}
 * (andesite+nugget+clay → {@code kubejs:compound_mixture}) — NOT direct mixer→alloy.
 * Press-dust: cobble → dust; compact 4×dust → andesite.
 */
public final class MixerLoopSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/mixer");

	private enum Step {
		KINETICS_POWER,
		MILL_PRESS_MIXER,
		SHEETS_AND_UTILS,
		FANS,
		DRILL_AND_SAW,
		PRESS_DUST,
		COMPACT_ANDESITE,
		MIXER_COMPOUND,
		DONE
	}

	private Step step = Step.KINETICS_POWER;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MixerLoopSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.KINETICS_POWER;
		LOGGER.info("Mixer loop: kinetics → mill/press/mixer → press-dust → compact → mixture");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case KINETICS_POWER:
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/hand_crank");
				step = Step.MILL_PRESS_MIXER;
				break;
			case MILL_PRESS_MIXER:
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/millstone");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/mechanical_press");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/mechanical_mixer");
				step = Step.SHEETS_AND_UTILS;
				break;
			case SHEETS_AND_UTILS:
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.BASIN,
						"create:pressing/iron_ingot");
				step = Step.FANS;
				break;
			case FANS:
				step = Step.DRILL_AND_SAW;
				break;
			case DRILL_AND_SAW:
				step = Step.PRESS_DUST;
				break;
			case PRESS_DUST:
				if (!awaitKind(CreateRecipeKinds.Kind.PRESS_DUST, Ch01RecipeIds.PRESS_DUST,
						() -> CreateRecipeKinds.pressDust(Ch01RecipeIds.PRESS_DUST))) {
					break;
				}
				step = Step.COMPACT_ANDESITE;
				break;
			case COMPACT_ANDESITE:
				if (!awaitKind(CreateRecipeKinds.Kind.BASIN, Ch01RecipeIds.COMPACT_ANDESITE,
						() -> CreateRecipeKinds.compactAndesite(Ch01RecipeIds.COMPACT_ANDESITE))) {
					break;
				}
				step = Step.MIXER_COMPOUND;
				break;
			case MIXER_COMPOUND:
				if (!awaitKind(CreateRecipeKinds.Kind.MIXER_BASIN, Ch01RecipeIds.MIXER_BASIN_MIX,
						() -> CreateRecipeKinds.mixerBasin(Ch01RecipeIds.MIXER_BASIN_MIX))) {
					break;
				}
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	private boolean awaitKind(CreateRecipeKinds.Kind kind, String bindId, Runnable fire) {
		fire.run();
		CreateRecipeExecutor exec = CreateRecipeExecutor.getInstance();
		if (!exec.isDone(kind, bindId)) {
			return false;
		}
		if (!exec.isSuccess(kind, bindId)) {
			LOGGER.warn("Mixer step {} finished without success — continuing", bindId);
		}
		return true;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Mixer loop stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MixerLoop/" + step;
	}
}
