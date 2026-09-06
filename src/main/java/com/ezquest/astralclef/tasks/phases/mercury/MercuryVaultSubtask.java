package com.ezquest.astralclef.tasks.phases.mercury;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercuryVaultSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mercury/vault");
	private enum Step { LOCATE_VAULT, CLEAR_VAULT, RETURN, DONE }
	private Step step = Step.LOCATE_VAULT;

	@Override public boolean isEqual(Task other) { return other instanceof MercuryVaultSubtask; }
	@Override protected void onStart() { step = Step.LOCATE_VAULT; LOGGER.info("Mercury vault/return begun"); }
	@Override protected Task onTick() {
		switch (step) {
			case LOCATE_VAULT: step = Step.CLEAR_VAULT; break;
			case CLEAR_VAULT: step = Step.RETURN; break;
			case RETURN: LOGGER.info("Mercury return gate complete (stub)"); step = Step.DONE; break;
			case DONE: break;
		}
		return null;
	}
	@Override protected void onStop(Task interrupt) { LOGGER.debug("MercuryVault stopped at {} (interrupt={})", step, interrupt); }
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "MercuryVault/" + step; }
}
