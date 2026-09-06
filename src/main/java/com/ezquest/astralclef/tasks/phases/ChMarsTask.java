package com.ezquest.astralclef.tasks.phases;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.phases.mars.MarsPrepSubtask;
import com.ezquest.astralclef.tasks.phases.mars.MarsSurfaceSubtask;
import com.ezquest.astralclef.tasks.phases.mars.MarsDungeonSubtask;
import com.ezquest.astralclef.world.AdAstraRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mars phase — second Ad Astra planet after Moon.
 * <p>
 * Gate: T3 rocket + thermal padding (FTB Ch3–4). Same TaskRunner
 * drive pattern as {@link ChMoonTask}.
 */
public class ChMarsTask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mars");

	public enum Phase {
		MARS_PREP,
		MARS_SURFACE,
		MARS_DUNGEON,
		COMPLETE
	}

	private Phase phase = Phase.MARS_PREP;
	private Task activeSubtask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof ChMarsTask;
	}

	@Override
	protected void onStart() {
		phase = Phase.MARS_PREP;
		activeSubtask = null;
		AdAstraRoutes.ensureCatalogued();
		LOGGER.info("Mars phase begun — prep → surface → dungeon (route: {})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MARS));
	}

	@Override
	protected Task onTick() {
		switch (phase) {
			case MARS_PREP:
				return drive(new MarsPrepSubtask(), Phase.MARS_SURFACE);
			case MARS_SURFACE:
				return drive(new MarsSurfaceSubtask(), Phase.MARS_DUNGEON);
			case MARS_DUNGEON:
				return drive(new MarsDungeonSubtask(), Phase.COMPLETE);
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
		LOGGER.info("Mars phase {} -> {}", phase, next);
		phase = next;
		activeSubtask = null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.info("Mars stopped at phase {} (interrupt={})", phase, interrupt);
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
		return "ChMars/" + phase;
	}
}
