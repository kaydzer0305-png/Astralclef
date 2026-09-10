package com.ezquest.astralclef.tasks.phases.moon;

import com.ezquest.astralclef.quests.AstralQuests;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import com.ezquest.astralclef.world.AdAstraRoutes;
import com.ezquest.astralclef.world.RocketCraftHelper;
import com.ezquest.astralclef.world.RocketHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launch prep: oxygen, suit, rocket assembly, fueling, launch pad.
 * Soft-checks player inventory (rocket, oxygen, fuel) when a server
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
	private Task gatherTask;
	private int ticks;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof LaunchPrepSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.OXYGEN_AND_SUIT;
		gatherTask = null;
		ticks = 0;
		LOGGER.info("Moon launch prep: oxygen/suit → rocket → fuel/pad → launch ({})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MOON));
	}

	@Override
	protected Task onTick() {
		ticks++;
		ServerPlayerEntity player = firstPlayer();
		switch (step) {
			case OXYGEN_AND_SUIT:
				if (player != null && !RocketHelper.hasOxygenGear(player)) {
					if (gatherTask == null || gatherTask.isFinished()) {
						gatherTask = new GatherTask("ad_astra:oxygen_tank", 1);
						LOGGER.info("Moon launch prep: delegating to GatherTask for oxygen gear");
					}
					return gatherTask;
				}
				gatherTask = null;
				step = Step.ROCKET_ASSEMBLY;
				break;
			case ROCKET_ASSEMBLY:
				if (player != null && !RocketHelper.hasRocket(player, AdAstraRoutes.Destination.MOON)) {
					// Prefer Create craft pipeline; fall back to GatherTask if no job accepted
					if (RocketCraftHelper.tryCraftRocket(AdAstraRoutes.Destination.MOON)
							&& !RocketCraftHelper.isCrafted(AdAstraRoutes.Destination.MOON)) {
						LOGGER.info("Moon rocket: Create craft job in progress — {}", RocketCraftHelper.status(AdAstraRoutes.Destination.MOON));
						break;
					}
					if (RocketCraftHelper.isCrafted(AdAstraRoutes.Destination.MOON)) {
						gatherTask = null;
						step = Step.FUEL_AND_PAD;
						break;
					}
					if (gatherTask == null || gatherTask.isFinished()) {
						gatherTask = new GatherTask(
								RocketHelper.rocketIdFor(AdAstraRoutes.Destination.MOON), 1);
					}
					if (ticks % 100 == 1) {
						LOGGER.info("Moon rocket: assemble {} at a {} (workbench nearby: {})",
								RocketHelper.rocketIdFor(AdAstraRoutes.Destination.MOON),
								RocketHelper.NASA_WORKBENCH, RocketHelper.hasWorkbench(player));
					}
					return gatherTask;
				}
				gatherTask = null;
				step = Step.FUEL_AND_PAD;
				break;
			case FUEL_AND_PAD:
				if (player != null && !RocketHelper.hasFuel(player)) {
					if (gatherTask == null || gatherTask.isFinished()) {
						gatherTask = new GatherTask("ad_astra:oil_bucket", 1);
					}
					return gatherTask;
				}
				if (player != null && !RocketHelper.hasLaunchPad(player)) {
					if (ticks % 100 == 1) {
						LOGGER.info("Moon launch prep: place {} nearby (FTB pad quest {})",
								RocketHelper.LAUNCH_PAD, AstralQuests.CH4_LAUNCH_PAD);
					}
					break;
				}
				gatherTask = null;
				step = Step.LAUNCH;
				break;
			case LAUNCH:
				LOGGER.info("Moon launch committed (no actual Ad Astra entity launch in this stub)");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		if (gatherTask != null && gatherTask.isFinished()) {
			gatherTask = null;
		}
		return null;
	}

	private ServerPlayerEntity firstPlayer() {
		try {
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
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
		gatherTask = null;
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
