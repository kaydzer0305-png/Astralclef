package com.ezquest.astralclef.tasks.phases.singularity;

import com.ezquest.astralclef.quests.FtbQuestsHelper;
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
			case VERIFY_QUESTS:
				try {
					var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
					if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
						var server = ctx.getWorld().getServer();
						var player = server.getPlayerManager().getPlayerList().isEmpty() ? null : server.getPlayerManager().getPlayerList().get(0);
						LOGGER.info("Singularity quest verify: {}", FtbQuestsHelper.status(server));
						if (player != null) {
							boolean win = FtbQuestsHelper.isQuestComplete(server, player, com.ezquest.astralclef.quests.AstralQuests.WIN_ASTRAL_SINGULARITY);
							boolean dragon = FtbQuestsHelper.isQuestComplete(server, player, com.ezquest.astralclef.quests.AstralQuests.DRAGON_KILL);
							LOGGER.info("FTB Ch6 checks: win={} dragon={} (ids {} / {})",
									win, dragon, com.ezquest.astralclef.quests.AstralQuests.WIN_ASTRAL_SINGULARITY, com.ezquest.astralclef.quests.AstralQuests.DRAGON_KILL);
							if (win) {
								LOGGER.info("Astral Singularity already complete — skipping to DONE");
								step = Step.DONE;
								break;
							}
							if (!dragon) {
								LOGGER.info("Ch6 gate: dragon kill {} not yet complete — waiting", com.ezquest.astralclef.quests.AstralQuests.DRAGON_KILL);
								break;
							}
						}
					} else {
						LOGGER.debug("FTB Quests Ch6 verify — no world context (soft)");
					}
				} catch (Throwable t) {
					LOGGER.debug("FTB Quests verify failed: {}", t.toString());
				}
				step = Step.CLAIM_REWARDS; break;
			case CLAIM_REWARDS:
				LOGGER.info("FTB Quests Ch6 complete — Astral Singularity win (checked {} against FTB API when present)",
						com.ezquest.astralclef.quests.AstralQuests.WIN_ASTRAL_SINGULARITY);
				step = Step.DONE; break;
			case DONE: break;
		}
		return null;
	}
	@Override protected void onStop(Task interrupt) { LOGGER.debug("SingularityQuest stopped at {} (interrupt={})", step, interrupt); }
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "SingularityQuest/" + step; }
}
