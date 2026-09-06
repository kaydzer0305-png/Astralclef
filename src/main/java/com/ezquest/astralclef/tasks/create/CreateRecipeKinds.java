package com.ezquest.astralclef.tasks.create;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import com.ezquest.astralclef.recipes.Ch01RecipeBindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalogue of Create / Astral recipe kinds used by Ch0.5–1 planning.
 * Handlers start / track jobs via {@link CreateRecipeExecutor}.
 * Named hooks default to {@link Ch01RecipeBindings} {@code astralclef:bind/*} ids.
 * <p>
 * Astral flags: Compound smelt/blast; Bronze early (not Brass); Press cobble→dust
 * then compact 4×dust→andesite; early mixer → {@code kubejs:compound_mixture}
 * (NOT direct mixer→alloy for Ch01).
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
		/** Astral: Andesite Compound → furnace/blast smelt (quests26). */
		COMPOUND_SMELT,
		/** Astral: smith Copper+Tin → Bronze (not Brass). */
		BRONZE_SMITH,
		/** Astral: Press cobble → andesite dust; compacting 4×dust → andesite. */
		PRESS_DUST,
		/** Mixer + Basin kinetic setup / mix recipes (early: compound mixture). */
		MIXER_BASIN,
		/** Grout via Mixer (quests25) — Chapter 2 unlock gate. */
		GROUT
	}

	@FunctionalInterface
	public interface RecipeHandler {
		/**
		 * Attempt to satisfy a recipe of this kind.
		 *
		 * @param recipeId opaque id / KubeJS key / astralclef:bind/*
		 * @return true when the request was accepted, is progressing, or succeeded
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
		register(Kind.SEQUENCED_ASSEMBLY, id -> executorHandler(Kind.SEQUENCED_ASSEMBLY, id));
		register(Kind.MECHANICAL_CRAFTING, id -> executorHandler(Kind.MECHANICAL_CRAFTING, id));
		register(Kind.FILLING, id -> executorHandler(Kind.FILLING, id));
		register(Kind.BASIN, id -> executorHandler(Kind.BASIN, id));
		register(Kind.COMPOUND_SMELT, id -> executorHandler(Kind.COMPOUND_SMELT, id));
		register(Kind.BRONZE_SMITH, id -> executorHandler(Kind.BRONZE_SMITH, id));
		register(Kind.PRESS_DUST, id -> executorHandler(Kind.PRESS_DUST, id));
		register(Kind.MIXER_BASIN, id -> executorHandler(Kind.MIXER_BASIN, id));
		register(Kind.GROUT, id -> executorHandler(Kind.GROUT, id));
		initialized = true;
		LOGGER.info("Create recipe kind handlers registered (executor-backed): {}", HANDLERS.keySet());
	}

	private static boolean executorHandler(Kind kind, String recipeId) {
		return CreateRecipeExecutor.getInstance().acceptOrProgress(kind, recipeId);
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

	private static String orBind(String recipeId, String bindDefault) {
		return (recipeId == null || recipeId.isEmpty()) ? bindDefault : recipeId;
	}

	// --- Named Astral Ch0.5–1 hooks (defaults → Ch01RecipeBindings) ---

	/** Andesite Compound → furnace smelt. FTB: quests26. */
	public static boolean compoundSmelt(String recipeId) {
		return tryExecute(Kind.COMPOUND_SMELT, orBind(recipeId, Ch01RecipeBindings.BIND_COMPOUND_SMELT));
	}

	/** Andesite Compound → blasting (parallel path). */
	public static boolean compoundBlast(String recipeId) {
		return tryExecute(Kind.COMPOUND_SMELT, orBind(recipeId, Ch01RecipeBindings.BIND_COMPOUND_BLAST));
	}

	/** Shaped BBB/AAA/CCC → andesite_compound. */
	public static boolean compoundShaped(String recipeId) {
		return tryExecute(Kind.MECHANICAL_CRAFTING, orBind(recipeId, Ch01RecipeBindings.BIND_COMPOUND_SHAPED));
	}

	/** Smith Copper + Tin → Bronze (not Brass). */
	public static boolean bronzeSmith(String recipeId) {
		return tryExecute(Kind.BRONZE_SMITH, orBind(recipeId, Ch01RecipeBindings.BIND_BRONZE_SMITH));
	}

	/** Press cobble → andesite dust. */
	public static boolean pressDust(String recipeId) {
		return tryExecute(Kind.PRESS_DUST, orBind(recipeId, Ch01RecipeBindings.BIND_PRESS_DUST));
	}

	/** Compact 4× andesite dust → andesite. */
	public static boolean compactAndesite(String recipeId) {
		return tryExecute(Kind.BASIN, orBind(recipeId, Ch01RecipeBindings.BIND_COMPACT_ANDESITE));
	}

	/** Mixer + Basin — early: compound mixture (NOT alloy). */
	public static boolean mixerBasin(String recipeId) {
		return tryExecute(Kind.MIXER_BASIN, orBind(recipeId, Ch01RecipeBindings.BIND_MIXER_COMPOUND));
	}

	/** Grout via Mixer. FTB: quests25 — Chapter 2 unlock. */
	public static boolean grout(String recipeId) {
		return tryExecute(Kind.GROUT, orBind(recipeId, Ch01RecipeBindings.BIND_GROUT));
	}

	public static void forEachKind(Consumer<Kind> consumer) {
		for (Kind kind : Kind.values()) {
			consumer.accept(kind);
		}
	}
}
