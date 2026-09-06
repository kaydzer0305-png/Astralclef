package com.ezquest.astralclef.tasks.phases;

import com.ezquest.astralclef.combat.GreatBeastPhase;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.phases.singularity.CraftSingularitySubtask;
import com.ezquest.astralclef.tasks.phases.singularity.SingularityQuestSubtask;
import com.ezquest.astralclef.world.AdAstraRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chapter 6 — Astral Singularity win condition (FTB Quests).
 * <p>
 * Gated behind Mercury vault. Requires great beast kill + final
 * Create/Ad Astra crafts + FTB Quests completion. Each phase is
 * a Task so TaskRunner drives it via /astralclef singularity.
 */
public class ChAstralSingularityTask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/singularity");

	public enum Phase {
		GREAT_BEAST,
		CRAFT_SINGULARITY,
		QUEST_COMPLETION,
		COMPLETE
	}

	private Phase phase = Phase.GREAT_BEAST;
	private Task activeSubtask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof ChAstralSingularityTask;
	}

	@Override
	protected void onStart() {
		phase = Phase.GREAT_BEAST;
		activeSubtask = null;
		AdAstraRoutes.ensureCatalogued();
		LOGGER.info("Astral Singularity (Ch6) begun — beast → craft → quest (route: {})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.SINGULARITY));
	}

	@Override
	protected Task onTick() {
		switch (phase) {
			case GREAT_BEAST:
				return drive(new GreatBeastPhase(), Phase.CRAFT_SINGULARITY);
			case CRAFT_SINGULARITY:
				return drive(new CraftSingularitySubtask(), Phase.QUEST_COMPLETION);
			case QUEST_COMPLETION:
				return drive(new SingularityQuestSubtask(), Phase.COMPLETE);
			case COMPLETE:
				return null;
		}
		return null;
	}

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
		LOGGER.info("Singularity phase {} -> {}", phase, next);
		phase = next;
		activeSubtask = null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.info("Singularity stopped at phase {} (interrupt={})", phase, interrupt);
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
		return "ChSingularity/" + phase;
	}
}
