package com.ezquest.astralclef.recipes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.ezquest.astralclef.tasks.phases.ch01.Ch01RecipeIds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Light catalogue surface for recipes that may be generated or overridden by KubeJS.
 * Implementations can index datapack / KubeJS ids without pulling Create classes at compile time.
 * <p>
 * Ch0.5–1 Astral bindings live in {@link Ch01RecipeBindings} ({@code astralclef:bind/*});
 * prefer type+I/O {@link net.minecraft.recipe.RecipeManager} resolution over guessed auto-ids.
 * Short names from {@link Ch01RecipeIds} are also registered for lookup.
 */
public interface KubeJsAwareCatalogue {
	/** Refresh the index (e.g. after datapack reload). */
	void refresh();

	/** Lookup a recipe by opaque id / KubeJS key / astralclef bind. */
	Optional<String> find(String recipeId);

	/** Register alias → canonical recipe id (optional for noop / bind catalogues). */
	default void register(String alias, String recipeId) {}

	default void register(String recipeId) {
		register(recipeId, recipeId);
	}

	/** Whether the catalogue knows about this id (present or overridden). */
	default boolean knows(String recipeId) {
		return find(recipeId).isPresent();
	}

	/** Ch01 Astral bindings catalogue (type+I/O specs + bind placeholders). */
	static KubeJsAwareCatalogue ch01() {
		return Ch01RecipeBindings.asCatalogue();
	}

	/** Shared process-wide catalogue: Ch01RecipeIds short names + bind ids. */
	static KubeJsAwareCatalogue shared() {
		return DefaultCatalogue.SHARED;
	}

	/** No-op catalogue for compile-safe defaults until KubeJS scan is wired. */
	static KubeJsAwareCatalogue noop() {
		return new KubeJsAwareCatalogue() {
			@Override
			public void refresh() {}

			@Override
			public Optional<String> find(String recipeId) {
				return Optional.empty();
			}
		};
	}

	final class DefaultCatalogue implements KubeJsAwareCatalogue {
		private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/catalogue");
		static final DefaultCatalogue SHARED = new DefaultCatalogue();

		private final Map<String, String> index = new LinkedHashMap<>();
		private final KubeJsAwareCatalogue binds = Ch01RecipeBindings.asCatalogue();

		DefaultCatalogue() {
			refresh();
		}

		@Override
		public synchronized void refresh() {
			index.clear();
			binds.refresh();
			for (Map.Entry<String, String> e : Ch01RecipeIds.all().entrySet()) {
				index.put(e.getKey(), e.getValue());
				index.put(e.getValue(), e.getValue());
			}
			for (String bindId : Ch01RecipeBindings.all().keySet()) {
				index.putIfAbsent(bindId, bindId);
			}
			LOGGER.info("KubeJS-aware catalogue refreshed: {} Ch01 keys, {} total entries",
					Ch01RecipeIds.all().size(), index.size());
		}

		@Override
		public synchronized void register(String alias, String recipeId) {
			if (alias == null || recipeId == null) {
				return;
			}
			index.put(alias, recipeId);
			index.put(recipeId, recipeId);
		}

		@Override
		public synchronized Optional<String> find(String recipeId) {
			if (recipeId == null || recipeId.isEmpty()) {
				return Optional.empty();
			}
			if (index.containsKey(recipeId)) {
				return Optional.of(index.get(recipeId));
			}
			return binds.find(recipeId);
		}

		public synchronized Map<String, String> snapshot() {
			return Collections.unmodifiableMap(new LinkedHashMap<>(index));
		}
	}
}
