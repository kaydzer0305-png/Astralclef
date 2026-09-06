package com.ezquest.astralclef.tasks.phases.moon;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.world.AdAstraRoutes;
import com.ezquest.astralclef.world.RocketHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launch prep: oxygen, suit, rocket assembly, fueling, launch pad.
 * Now soft-checks player inventory (rocket, oxygen, fuel) when a server
 * player is available; stubs degrade gracefully when absent (unit tests).
 */
public final class LaunchPrepSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/moon/launch");

	private enum Step {
		OXYGEN_AND_SUIT,
		ROCKET_ASSEMBLY,
		FUEL_AND_PAD,
		LAUNCH,
		DONE
	}

	private Step step = Step.OXYGEN_AND_SUIT;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof LaunchPrepSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.OXYGEN_AND_SUIT;
		LOGGER.info("Moon launch prep: oxygen/suit → rocket → fuel/pad → launch ({})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MOON));
	}

	@Override
	protected Task onTick() {
		ServerPlayerEntity player = firstPlayer();
		switch (step) {
			case OXYGEN_AND_SUIT:
				if (player != null && !RocketHelper.hasOxygenGear(player)) {
					LOGGER.info("Moon launch prep: missing oxygen gear — waiting/gathering");
					break;
				}
				step = Step.ROCKET_ASSEMBLY;
				break;
			case ROCKET_ASSEMBLY:
				if (player != null && !RocketHelper.hasRocket(player, AdAstraRoutes.Destination.MOON)) {
					LOGGER.info("Moon launch prep: missing {} — gather/craft T2 rocket",
							RocketHelper.rocketIdFor(AdAstraRoutes.Destination.MOON));
					break;
				}
				step = Step.FUEL_AND_PAD;
				break;
			case FUEL_AND_PAD:
				if (player != null && !RocketHelper.hasFuel(player)) {
					LOGGER.info("Moon launch prep: missing fuel — waiting");
					break;
				}
				step = Step.LAUNCH;
				break;
			case LAUNCH:
				LOGGER.info("Moon launch committed (no actual Ad Astra entity launch in this stub)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	private ServerPlayerEntity firstPlayer() {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) {
					return list.get(0);
				}
			}
		} catch (Throwable ignored) {}
		return null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("LaunchPrep stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MoonLaunch/" + step;
	}
}
