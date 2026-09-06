package com.ezquest.astralclef.tasks.create.world;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;

/**
 * Maps {@link CreateRecipeKinds.Kind} to target block ids (Create AllBlocks equivalents
 * via Registry ids {@code create:...}, plus vanilla furnace/smith for Astral kinds).
 */
public enum CreateMachineType {
	BASIN("create:basin"),
	MECHANICAL_PRESS("create:mechanical_press"),
	MECHANICAL_MIXER("create:mechanical_mixer"),
	DEPOT("create:depot"),
	BELT("create:belt"),
	MECHANICAL_CRAFTER("create:mechanical_crafter"),
	SPOUT("create:spout"),
	MILLSTONE("create:millstone"),
	ANDESITE_CASING("create:andesite_casing"),
	FURNACE("minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker"),
	SMITHING("minecraft:smithing_table");

	private final List<String> blockIds;

	CreateMachineType(String... blockIds) {
		this.blockIds = Collections.unmodifiableList(Arrays.asList(blockIds));
	}

	public List<String> blockIds() {
		return blockIds;
	}

	/** Resolve preferred machine type for a recipe kind. */
	public static CreateMachineType fromKind(CreateRecipeKinds.Kind kind) {
		if (kind == null) {
			return BASIN;
		}
		switch (kind) {
			case SEQUENCED_ASSEMBLY:
				return DEPOT;
			case MECHANICAL_CRAFTING:
				return MECHANICAL_CRAFTER;
			case FILLING:
				return SPOUT;
			case BASIN:
				return BASIN;
			case COMPOUND_SMELT:
				return FURNACE;
			case BRONZE_SMITH:
				return SMITHING;
			case PRESS_DUST:
				return MECHANICAL_PRESS;
			case MIXER_BASIN:
			case GROUT:
				// Basin holds items; mixer sits above for kinetics.
				return BASIN;
			default:
				return BASIN;
		}
	}

	/** Ordered candidates for locate (first match wins by nearest across all). */
	public static List<CreateMachineType> candidatesFor(CreateRecipeKinds.Kind kind) {
		if (kind == null) {
			return List.of(BASIN);
		}
		switch (kind) {
			case SEQUENCED_ASSEMBLY:
				return List.of(DEPOT, BELT);
			case FILLING:
				return List.of(SPOUT, BASIN, DEPOT);
			case MIXER_BASIN:
			case GROUT:
				return List.of(BASIN, MECHANICAL_MIXER);
			case COMPOUND_SMELT:
				return List.of(FURNACE);
			default:
				return List.of(fromKind(kind));
		}
	}
}
