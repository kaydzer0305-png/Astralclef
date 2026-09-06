package com.ezquest.astralclef.tasks.phases.ch01;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ezquest.astralclef.recipes.Ch01RecipeBindings;

/**
 * Opaque Ch0.5–1 recipe id constants for subtask call sites.
 * <p>
 * Values are {@code astralclef:bind/*} placeholders from {@link Ch01RecipeBindings}
 * that resolve at runtime via RecipeManager (type + I/O). Swap a constant here
 * when Research supplies an exact datapack/KubeJS id — call sites stay unchanged.
 */
public final class Ch01RecipeIds {
	private Ch01RecipeIds() {}

	/** @see Ch01RecipeBindings#BIND_BRONZE_SMITH */
	public static final String BRONZE_SMITH = Ch01RecipeBindings.BIND_BRONZE_SMITH;

	/** @see Ch01RecipeBindings#BIND_COMPOUND_SHAPED */
	public static final String ANDESITE_COMPOUND_SHAPED = Ch01RecipeBindings.BIND_COMPOUND_SHAPED;

	/** quests26 — @see Ch01RecipeBindings#BIND_COMPOUND_SMELT */
	public static final String ANDESITE_COMPOUND_SMELT = Ch01RecipeBindings.BIND_COMPOUND_SMELT;

	/** @see Ch01RecipeBindings#BIND_COMPOUND_BLAST */
	public static final String ANDESITE_COMPOUND_BLAST = Ch01RecipeBindings.BIND_COMPOUND_BLAST;

	/**
	 * Alloy stockpile craft path. Uncertain: Astral removes stock Create alloy recipes;
	 * prefer compound smelt / blast.
	 */
	public static final String ANDESITE_ALLOY = "create:crafting/materials/andesite_alloy";

	/** @see Ch01RecipeBindings#BIND_PRESS_DUST */
	public static final String PRESS_DUST = Ch01RecipeBindings.BIND_PRESS_DUST;

	/** @see Ch01RecipeBindings#BIND_COMPACT_ANDESITE */
	public static final String COMPACT_ANDESITE = Ch01RecipeBindings.BIND_COMPACT_ANDESITE;

	/** Early mixer — compound mixture, not direct alloy. @see Ch01RecipeBindings#BIND_MIXER_COMPOUND */
	public static final String MIXER_BASIN_MIX = Ch01RecipeBindings.BIND_MIXER_COMPOUND;

	/** quests25 — @see Ch01RecipeBindings#BIND_GROUT */
	public static final String GROUT = Ch01RecipeBindings.BIND_GROUT;

	/** All Ch01 catalogue entries: short name → opaque id. */
	public static Map<String, String> all() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("bronze_smith", BRONZE_SMITH);
		m.put("andesite_compound_shaped", ANDESITE_COMPOUND_SHAPED);
		m.put("andesite_compound_smelt", ANDESITE_COMPOUND_SMELT);
		m.put("andesite_compound_blast", ANDESITE_COMPOUND_BLAST);
		m.put("andesite_alloy", ANDESITE_ALLOY);
		m.put("press_dust", PRESS_DUST);
		m.put("compact_andesite", COMPACT_ANDESITE);
		m.put("mixer_basin_mix", MIXER_BASIN_MIX);
		m.put("grout", GROUT);
		return Collections.unmodifiableMap(m);
	}
}
