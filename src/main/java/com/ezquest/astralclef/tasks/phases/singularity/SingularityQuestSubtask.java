package com.ezquest.astralclef.tasks.phases.singularity;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SingularityQuestSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/singularity/quest");
	private enum Step { VERIFY_QUESTS, CLAIM_REWARDS, DONE }
	private Step step = Step.VERIFY_QUESTS;

	@Override public boolean isEqual(Task other) { return other instanceof SingularityQuestSubtask; }
	@Override protected void onStart() { step = Step.VERIFY_QUESTS; LOGGER.info("Singularity quest completion begun"); }
	@Override protected Task onTick() {
		switch (step) {
			case VERIFY_QUESTS: // TODO: FTB Quests Ch6 check (SNBT/quest API when available)
				LOGGER.debug("FTB Quests Ch6 verify (stub — no API bound)");
				step = Step.CLAIM_REWARDS; break;
			case CLAIM_REWARDS: // TODO: claim/verify singularity quest
				LOGGER.info("FTB Quests Ch6 complete — Astral Singularity win (stub)"); step = Step.DONE; break;
			case DONE: break;
		}
		return null;
	}
	@Override protected void onStop(Task interrupt) { LOGGER.debug("SingularityQuest stopped at {} (interrupt={})", step, interrupt); }
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "SingularityQuest/" + step; }
}
