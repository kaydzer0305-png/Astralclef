package com.ezquest.astralclef.tasks.phases;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.phases.mercury.MercuryPrepSubtask;
import com.ezquest.astralclef.tasks.phases.mercury.MercurySurfaceSubtask;
import com.ezquest.astralclef.tasks.phases.mercury.MercuryVaultSubtask;
import com.ezquest.astralclef.world.AdAstraRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mercury phase — third Ad Astra planet, gating Astral Singularity.
 * T4 rocket with extreme thermal load (FTB Ch5). Mirrors Moon/Mars pattern.
 */
public class ChMercuryTask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mercury");

	public enum Phase {
		MERCURY_PREP,
		MERCURY_SURFACE,
		MERCURY_VAULT,
		COMPLETE
	}

	private Phase phase = Phase.MERCURY_PREP;
	private Task activeSubtask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof ChMercuryTask;
	}

	@Override
	protected void onStart() {
		phase = Phase.MERCURY_PREP;
		activeSubtask = null;
		AdAstraRoutes.ensureCatalogued();
		LOGGER.info("Mercury phase begun — prep → surface → vault (route: {})",
				AdAstraRoutes.routeFor(AdAstraRoutes.Destination.MERCURY));
	}

	@Override
	protected Task onTick() {
		switch (phase) {
			case MERCURY_PREP:
				return drive(new MercuryPrepSubtask(), Phase.MERCURY_SURFACE);
			case MERCURY_SURFACE:
				return drive(new MercurySurfaceSubtask(), Phase.MERCURY_VAULT);
			case MERCURY_VAULT:
				return drive(new MercuryVaultSubtask(), Phase.COMPLETE);
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
		LOGGER.info("Mercury phase {} -> {}", phase, next);
		phase = next;
		activeSubtask = null;
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.info("Mercury stopped at phase {} (interrupt={})", phase, interrupt);
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
		return "ChMercury/" + phase;
	}
}
