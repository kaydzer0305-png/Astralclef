package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ch0.5 unlock critical path (assorted_goals) — skip food/alcohol/Chipped.
 * <ol>
 *   <li>Crafting Table → Hephaestus (Patterns → Part Builder → Tinker Station) or copper tools</li>
 *   <li>Furnace</li>
 *   <li>Mine Iron + Tin + Copper (Pickadze wood-tier only)</li>
 *   <li>Essential Materials — Tin, Copper, Andesite, Clay</li>
 * </ol>
 * FTB: quests31 (Ch1 unlock). SNBT edges unverified.
 */
public final class Ch05UnlockSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/ch05");

	private enum Step {
		CRAFTING_AND_TOOLS,
		FURNACE,
		MINE_METALS,
		ESSENTIAL_MATERIALS,
		DONE
	}

	private Step step = Step.CRAFTING_AND_TOOLS;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof Ch05UnlockSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.CRAFTING_AND_TOOLS;
		LOGGER.info("Ch0.5 unlock: tools → furnace → Fe/Sn/Cu → essential materials (quests31)");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case CRAFTING_AND_TOOLS:
				// TODO: crafting table; Hephaestus patterns/part builder/tinker station OR copper tools
				step = Step.FURNACE;
				break;
			case FURNACE:
				// TODO: place/smelt furnace
				step = Step.MINE_METALS;
				break;
			case MINE_METALS:
				// TODO: mine iron + tin + copper with wood-tier Pickadze only
				step = Step.ESSENTIAL_MATERIALS;
				break;
			case ESSENTIAL_MATERIALS:
				// TODO: stock Tin, Copper, Andesite, Clay — quests31 Ch1 unlock
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Ch0.5 unlock stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "Ch05Unlock/" + step;
	}
}
