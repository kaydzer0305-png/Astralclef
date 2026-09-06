package com.ezquest.astralclef.tasks.phases.mars;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.world.AdAstraRoutes;
import com.ezquest.astralclef.world.RocketHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MarsPrepSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mars/prep");

	private enum Step {
		THERMAL_AND_OXYGEN,
		T3_ROCKET,
		FUEL_AND_PAD,
		LAUNCH,
		DONE
	}

	private Step step = Step.THERMAL_AND_OXYGEN;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof MarsPrepSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.THERMAL_AND_OXYGEN;
		LOGGER.info("Mars prep: thermal/oxygen → T3 → fuel/pad → launch ({})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MARS));
	}

	@Override
	protected Task onTick() {
		var player = firstPlayer();
		switch (step) {
			case THERMAL_AND_OXYGEN:
				if (player != null && !RocketHelper.hasOxygenGear(player)) {
					LOGGER.info("Mars prep: missing thermal/oxygen — gathering");
					break;
				}
				step = Step.T3_ROCKET;
				break;
			case T3_ROCKET:
				if (player != null && !RocketHelper.hasRocket(player, AdAstraRoutes.Destination.MARS)) {
					LOGGER.info("Mars prep: missing {}", RocketHelper.rocketIdFor(AdAstraRoutes.Destination.MARS));
					break;
				}
				step = Step.FUEL_AND_PAD;
				break;
			case FUEL_AND_PAD:
				if (player != null && !RocketHelper.hasFuel(player)) {
					LOGGER.info("Mars prep: missing fuel");
					break;
				}
				step = Step.LAUNCH;
				break;
			case LAUNCH:
				LOGGER.info("Mars launch committed (stub)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
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

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("MarsPrep stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MarsPrep/" + step;
	}
}
