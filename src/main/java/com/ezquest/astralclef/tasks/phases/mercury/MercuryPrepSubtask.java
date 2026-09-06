package com.ezquest.astralclef.tasks.phases.mercury;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.world.AdAstraRoutes;
import com.ezquest.astralclef.world.RocketHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercuryPrepSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mercury/prep");

	private enum Step { EXTREME_THERMAL, T4_ROCKET, FUEL_AND_PAD, LAUNCH, DONE }
	private Step step = Step.EXTREME_THERMAL;
	private Task gatherTask;

	@Override public boolean isEqual(Task other) { return other instanceof MercuryPrepSubtask; }

	@Override protected void onStart() {
		step = Step.EXTREME_THERMAL;
		LOGGER.info("Mercury prep: extreme thermal → T4 → fuel/pad → launch ({})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MERCURY));
	}

	@Override protected Task onTick() {
		var player = firstPlayer();
		switch (step) {
			case EXTREME_THERMAL:
				if (player != null && !RocketHelper.hasOxygenGear(player)) {
					if (gatherTask == null || gatherTask.isFinished()) gatherTask = new com.ezquest.astralclef.tasks.gather.GatherTask("ad_astra:oxygen_tank", 1);
					return gatherTask;
				}
				gatherTask = null; step = Step.T4_ROCKET; break;
			case T4_ROCKET:
				if (player != null && !RocketHelper.hasRocket(player, AdAstraRoutes.Destination.MERCURY)) {
					if (com.ezquest.astralclef.world.RocketCraftHelper.tryCraftRocket(AdAstraRoutes.Destination.MERCURY)
							&& !com.ezquest.astralclef.world.RocketCraftHelper.isCrafted(AdAstraRoutes.Destination.MERCURY)) break;
					if (com.ezquest.astralclef.world.RocketCraftHelper.isCrafted(AdAstraRoutes.Destination.MERCURY)) { gatherTask = null; step = Step.FUEL_AND_PAD; break; }
					if (gatherTask == null || gatherTask.isFinished()) gatherTask = new com.ezquest.astralclef.tasks.gather.GatherTask(RocketHelper.rocketIdFor(AdAstraRoutes.Destination.MERCURY), 1);
					return gatherTask;
				}
				gatherTask = null; step = Step.FUEL_AND_PAD; break;
			case FUEL_AND_PAD:
				if (player != null && !RocketHelper.hasFuel(player)) {
					if (gatherTask == null || gatherTask.isFinished()) gatherTask = new com.ezquest.astralclef.tasks.gather.GatherTask("ad_astra:oil_bucket", 1);
					return gatherTask;
				}
				gatherTask = null; step = Step.LAUNCH; break;
			case LAUNCH: LOGGER.info("Mercury launch committed (stub)"); step = Step.DONE; break;
			case DONE: break;
		}
		if (gatherTask != null && gatherTask.isFinished()) gatherTask = null;
		return null;
	}

	private net.minecraft.server.network.ServerPlayerEntity firstPlayer() {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) return list.get(0);
			}
		} catch (Throwable ignored) {}
		return null;
	}

	@Override protected void onStop(Task interrupt) { gatherTask = null; LOGGER.debug("MercuryPrep stopped at {} (interrupt={})", step, interrupt); }
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "MercuryPrep/" + step; }
}
