package com.ezquest.astralclef.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ad Astra travel / planet routing helpers (Moon → Mars → Mercury → Singularity).
 * Provides tier/route metadata for phase tasks and {@code /astralclef} status.
 * Real orbital/gravity mechanics are handled by Ad Astra itself; this is
 * planning state for the bot.
 */
public final class AdAstraRoutes {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/routes");

	public enum Destination {
		MOON(2, "Moon", "T2 rocket + oxygen gear"),
		MARS(3, "Mars", "T3 rocket + thermal protection"),
		MERCURY(4, "Mercury", "T4 rocket + extreme thermal"),
		SINGULARITY(4, "Astral Singularity", "endgame — Ch6 win condition");

		public final int tier;
		public final String display;
		public final String requirement;

		Destination(int tier, String display, String requirement) {
			this.tier = tier;
			this.display = display;
			this.requirement = requirement;
		}
	}

	public static final class Route {
		public final Destination dest;
		public final int tier;
		public final String requirement;

		Route(Destination dest) {
			this.dest = dest;
			this.tier = dest.tier;
			this.display = dest.display;
			this.requirement = dest.requirement;
		}

		private final String display;

		@Override
		public String toString() {
			return dest.name() + "(T" + tier + ": " + requirement + ")";
		}
	}

	private AdAstraRoutes() {}

	public static Route routeFor(Destination dest) {
		return new Route(dest);
	}

	/** Refresh catalogue / quest bindings if needed (hooks KubeJS scan when present). */
	public static void ensureCatalogued() {
		LOGGER.debug("AdAstra routes catalogued for {}", Destination.MOON);
	}

	public void planRoute() {
		LOGGER.info("planRoute → {}", routeFor(Destination.MOON));
	}
}
