package com.ezquest.astralclef.tasks.create;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalogue of Create / Astral recipe kinds used by Ch0.5–1 planning.
 * Handlers are stubs/hooks — full recipe execution is TODO.
 * <p>
 * Astral flags: Compound smelt; Bronze early (not Brass); Andesite Dust =
 * Press Cobble×4→dust then press dust; mixer alloy cheaper later.
 */
public final class CreateRecipeKinds {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-recipes");

	public enum Kind {
		/** Deployer / press / saw / spout sequenced assembly lines. */
		SEQUENCED_ASSEMBLY,
		/** Mechanical crafter grid recipes. */
		MECHANICAL_CRAFTING,
		/** Spout / filling (buckets, bottles, basins). */
		FILLING,
		/** Generic basin mix / compact / press. */
		BASIN,
		/** Astral: Andesite Compound → furnace smelt (quests26). */
		COMPOUND_SMELT,
		/** Astral: smith Copper+Tin → Bronze (not Brass). */
		BRONZE_SMITH,
		/** Astral: Press cobble×4 → andesite dust, then press dust. */
		PRESS_DUST,
		/** Mixer + Basin kinetic setup / mix recipes. */
		MIXER_BASIN,
		/** Grout via Mixer (quests25) — Chapter 2 unlock gate. */
		GROUT
	}

	@FunctionalInterface
	public interface RecipeHandler {
		/**
		 * Attempt to satisfy a recipe of this kind.
		 *
		 * @param recipeId opaque id / KubeJS key (stub)
		 * @return true when the handler claims the request was accepted
		 */
		boolean execute(String recipeId);
	}

	private static final Map<Kind, RecipeHandler> HANDLERS = new EnumMap<>(Kind.class);
	private static boolean initialized;

	private CreateRecipeKinds() {}

	public static void init() {
		if (initialized) {
			return;
		}
		register(Kind.SEQUENCED_ASSEMBLY, id -> stub("SEQUENCED_ASSEMBLY", id));
		register(Kind.MECHANICAL_CRAFTING, id -> stub("MECHANICAL_CRAFTING", id));
		register(Kind.FILLING, id -> stub("FILLING", id));
		register(Kind.BASIN, id -> stub("BASIN", id));
		register(Kind.COMPOUND_SMELT, id -> stub("COMPOUND_SMELT", id));
		register(Kind.BRONZE_SMITH, id -> stub("BRONZE_SMITH", id));
		register(Kind.PRESS_DUST, id -> stub("PRESS_DUST", id));
		register(Kind.MIXER_BASIN, id -> stub("MIXER_BASIN", id));
		register(Kind.GROUT, id -> stub("GROUT", id));
		initialized = true;
		LOGGER.info("Create recipe kind handlers registered (stubs): {}", HANDLERS.keySet());
	}

	private static boolean stub(String kind, String recipeId) {
		LOGGER.debug("{} stub: {}", kind, recipeId);
		return false;
	}

	public static void register(Kind kind, RecipeHandler handler) {
		HANDLERS.put(kind, handler);
	}

	public static RecipeHandler getHandler(Kind kind) {
		return HANDLERS.get(kind);
	}

	public static Map<Kind, RecipeHandler> handlers() {
		return Collections.unmodifiableMap(HANDLERS);
	}

	public static boolean tryExecute(Kind kind, String recipeId) {
		RecipeHandler handler = HANDLERS.get(kind);
		if (handler == null) {
			return false;
		}
		return handler.execute(recipeId);
	}

	// --- Named Astral Ch0.5–1 hooks (thin wrappers over Kind handlers) ---

	/** Andesite Compound → furnace smelt. FTB: quests26 (SNBT edges unverified). */
	public static boolean compoundSmelt(String recipeId) {
		return tryExecute(Kind.COMPOUND_SMELT, recipeId);
	}

	/** Smith Copper + Tin → Bronze (not Brass). */
	public static boolean bronzeSmith(String recipeId) {
		return tryExecute(Kind.BRONZE_SMITH, recipeId);
	}

	/** Press cobble×4 → dust, then press dust (Andesite Dust path). */
	public static boolean pressDust(String recipeId) {
		return tryExecute(Kind.PRESS_DUST, recipeId);
	}

	/** Mixer + Basin mix / setup. */
	public static boolean mixerBasin(String recipeId) {
		return tryExecute(Kind.MIXER_BASIN, recipeId);
	}

	/** Grout via Mixer. FTB: quests25 — Chapter 2 unlock (SNBT edges unverified). */
	public static boolean grout(String recipeId) {
		return tryExecute(Kind.GROUT, recipeId);
	}

	public static void forEachKind(Consumer<Kind> consumer) {
		for (Kind kind : Kind.values()) {
			consumer.accept(kind);
		}
	}
}
