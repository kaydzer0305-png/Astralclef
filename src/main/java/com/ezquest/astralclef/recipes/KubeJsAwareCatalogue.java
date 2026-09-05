package com.ezquest.astralclef.recipes;

import java.util.Optional;

/**
 * Light catalogue surface for recipes that may be generated or overridden by KubeJS.
 * Implementations can index datapack / KubeJS ids without pulling Create classes at compile time.
 */
public interface KubeJsAwareCatalogue {
	/** Refresh the index (e.g. after datapack reload). */
	void refresh();

	/** Lookup a recipe by opaque id / KubeJS key. */
	Optional<String> find(String recipeId);

	/** Whether the catalogue knows about this id (present or overridden). */
	default boolean knows(String recipeId) {
		return find(recipeId).isPresent();
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
}
