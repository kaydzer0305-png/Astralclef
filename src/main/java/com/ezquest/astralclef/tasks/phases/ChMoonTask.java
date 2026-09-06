package com.ezquest.astralclef.tasks.phases;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.phases.moon.LaunchPrepSubtask;
import com.ezquest.astralclef.tasks.phases.moon.LunarSurfaceSubtask;
import com.ezquest.astralclef.tasks.phases.moon.MoonDungeonSubtask;
import com.ezquest.astralclef.world.AdAstraRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moon phase — first Ad Astra destination after Ch01 grout gate.
 * <p>
 * Ship order (FTB Quests Ch2–3, unverified ids): launch infrastructure → oxygen
 * &amp; suit → rocket build/fuel → transit → surface ops → dungeon/return.
 * Each step is a {@link Task} so {@link com.ezquest.astralclef.task.TaskRunner}
 * can drive it tick-by-tick via {@code /astralclef moon}.
 */
public class ChMoonTask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/moon");

	public enum Phase {
		LAUNCH_PREP,
		LUNAR_SURFACE,
		MOON_DUNGEON,
		COMPLETE
	}

	private Phase phase = Phase.LAUNCH_PREP;
	private Task activeSubtask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof ChMoonTask;
	}

	@Override
	protected void onStart() {
		phase = Phase.LAUNCH_PREP;
		activeSubtask = null;
		AdAstraRoutes.ensureCatalogued();
		LOGGER.info("Moon phase begun — launch prep → surface → dungeon (route: {})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MOON));
	}

	@Override
	protected Task onTick() {
		switch (phase) {
			case LAUNCH_PREP:
				return drive(new LaunchPrepSubtask(), Phase.LUNAR_SURFACE);
			case LUNAR_SURFACE:
				return drive(new LunarSurfaceSubtask(), Phase.MOON_DUNGEON);
			case MOON_DUNGEON:
				return drive(new MoonDungeonSubtask(), Phase.COMPLETE);
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
		LOGGER.info("Moon phase {} -> {}", phase, next);
		phase = next;
		activeSubtask = null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.info("Moon stopped at phase {} (interrupt={})", phase, interrupt);
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
		return "ChMoon/" + phase;
	}
}
