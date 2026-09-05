package com.ezquest.astralclef.tasks.phases;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import com.ezquest.astralclef.tasks.phases.ch01.AlloyCasingSubtask;
import com.ezquest.astralclef.tasks.phases.ch01.Ch05UnlockSubtask;
import com.ezquest.astralclef.tasks.phases.ch01.GroutGateSubtask;
import com.ezquest.astralclef.tasks.phases.ch01.MixerLoopSubtask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chapter 0.5–1 Create loop (ship stub order):
 * Ch0.5 unlock → Alloy/Casing → Mixer loop → Grout gate.
 * <p>
 * FTB ids (SNBT edges unverified): quests31 (Ch1 unlock / Essential Materials),
 * quests26 (Andesite Compound smelt), quests5 (Andesite Alloy stockpile),
 * quests25 (Grout / Chapter 2 unlock).
 * <p>
 * Does not implement Moon/Mars/Mercury/Singularity. Defers trains, ComputerCraft,
 * Astral Signals.
 */
public class Ch01GettingStartedTask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01");

	/** Named phases matching ship order. */
	public enum Phase {
		/** Ch0.5 unlock — tools, furnace, metals, essential materials (quests31). */
		CH05_UNLOCK,
		/** Bronze, Compound smelt (quests26), Alloy (quests5), Andesite Casing. */
		ALLOY_CASING,
		/** Kinetics → Mill/Press/Mixer → sheets/fans/drill → press-dust. */
		MIXER_LOOP,
		/** Grout via Mixer (quests25) = Chapter 2 unlock. */
		GROUT_GATE,
		COMPLETE
	}

	private Phase phase = Phase.CH05_UNLOCK;
	/** Runner-owned subtask instance; do not replace with a fresh equal candidate. */
	private Task activeSubtask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof Ch01GettingStartedTask;
	}

	@Override
	protected void onStart() {
		phase = Phase.CH05_UNLOCK;
		activeSubtask = null;
		CreateRecipeKinds.init();
		LOGGER.info("Ch0.5–1 Getting Started begun (ship order)");
	}

	@Override
	protected Task onTick() {
		switch (phase) {
			case CH05_UNLOCK:
				return drive(new Ch05UnlockSubtask(), Phase.ALLOY_CASING);
			case ALLOY_CASING:
				return drive(new AlloyCasingSubtask(), Phase.MIXER_LOOP);
			case MIXER_LOOP:
				return drive(new MixerLoopSubtask(), Phase.GROUT_GATE);
			case GROUT_GATE:
				return drive(new GroutGateSubtask(), Phase.COMPLETE);
			case COMPLETE:
				return null;
		}
		return null;
	}

	/**
	 * Return {@code candidate} until the runner-held equal instance finishes, then advance.
	 */
	private Task drive(Task candidate, Phase next) {
		if (activeSubtask != null && activeSubtask.isEqual(candidate) && activeSubtask.isFinished()) {
			advance(next);
			return null;
		}
		if (activeSubtask == null || !activeSubtask.isEqual(candidate)) {
			activeSubtask = candidate;
		}
		return activeSubtask;
	}

	private void advance(Phase next) {
		LOGGER.info("Ch01 phase {} -> {}", phase, next);
		phase = next;
		activeSubtask = null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.info("Ch0.5–1 stopped at phase {} (interrupt={})", phase, interrupt);
		activeSubtask = null;
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
