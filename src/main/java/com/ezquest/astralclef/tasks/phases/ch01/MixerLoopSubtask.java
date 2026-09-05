package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mixer loop: kinetics → Millstone / Press / Mixer+Basin → sheets and utilities.
 * <ol>
 *   <li>Hand Crank → Shafts/Cogwheels → Water Wheel SU</li>
 *   <li>Millstone → Mechanical Press → Mixer + Basin</li>
 *   <li>Iron Sheets; Chutes; Wrench; Gearbox / Encased Chain Drive / Clutch</li>
 *   <li>Fans (lava/water/campfire)</li>
 *   <li>Mechanical Drill + Saw (quartz via Diorite mill; Nether off)</li>
 *   <li>Andesite Dust via press-dust (cobble×4→dust→press)</li>
 * </ol>
 * Defer: trains, ComputerCraft, Astral Signals.
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
		LOGGER.info("Mixer loop: kinetics → mill/press/mixer → sheets/fans/drill → press-dust");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case KINETICS_POWER:
				// TODO: hand crank, shafts, cogwheels, water wheel SU
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/hand_crank");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/shaft");
				step = Step.MILL_PRESS_MIXER;
				break;
			case MILL_PRESS_MIXER:
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/millstone");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/mechanical_press");
				CreateRecipeKinds.mixerBasin("create:crafting/kinetics/mechanical_mixer");
				step = Step.SHEETS_AND_UTILS;
				break;
			case SHEETS_AND_UTILS:
				// TODO: iron sheets, chutes, wrench, gearbox, encased chain drive, clutch
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.BASIN,
						"create:pressing/iron_ingot");
				step = Step.FANS;
				break;
			case FANS:
				// TODO: encased fan + lava/water/campfire processing
				step = Step.DRILL_AND_SAW;
				break;
			case DRILL_AND_SAW:
				// TODO: mechanical drill + saw; quartz via diorite mill (Nether off)
				step = Step.PRESS_DUST;
				break;
			case PRESS_DUST:
				CreateRecipeKinds.pressDust("astral:pressing/andesite_dust");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
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
