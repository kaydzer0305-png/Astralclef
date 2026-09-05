package com.ezquest.astralclef.tasks.create;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalogue of Create mod recipe kinds used by Astralclef planning.
 * Handlers are stubs/hooks — full recipe execution is TODO.
 */
public final class CreateRecipeKinds {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-recipes");

	public enum Kind {
		/** Deployer / press / saw / spout sequenced assembly lines. */
		SEQUENCED_ASSEMBLY,
		/** Mechanical crafter grid recipes (andesite machines, etc.). */
		MECHANICAL_CRAFTING,
		/** Spout / filling (buckets, bottles, basins). */
		FILLING,
		/** Mixing / compacting / pressing in a basin. */
		BASIN
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
		register(Kind.SEQUENCED_ASSEMBLY, recipeId -> {
			// TODO: drive Create sequenced assembly (deployers, belts, depots)
			LOGGER.debug("SEQUENCED_ASSEMBLY stub: {}", recipeId);
			return false;
		});
		register(Kind.MECHANICAL_CRAFTING, recipeId -> {
			// TODO: place/fill mechanical crafter grid and power it
			LOGGER.debug("MECHANICAL_CRAFTING stub: {}", recipeId);
			return false;
		});
		register(Kind.FILLING, recipeId -> {
			// TODO: spout/filling execution against fluid tanks
			LOGGER.debug("FILLING stub: {}", recipeId);
			return false;
		});
		register(Kind.BASIN, recipeId -> {
			// TODO: basin mix/compact/press with blender or press
			LOGGER.debug("BASIN stub: {}", recipeId);
			return false;
		});
		initialized = true;
		LOGGER.info("Create recipe kind handlers registered (stubs): {}", HANDLERS.keySet());
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

	/** Convenience: invoke the stub handler for a kind. */
	public static boolean tryExecute(Kind kind, String recipeId) {
		RecipeHandler handler = HANDLERS.get(kind);
		if (handler == null) {
			return false;
		}
		return handler.execute(recipeId);
	}

	/** For tests / diagnostics. */
	public static void forEachKind(Consumer<Kind> consumer) {
		for (Kind kind : Kind.values()) {
			consumer.accept(kind);
		}
	}
}
