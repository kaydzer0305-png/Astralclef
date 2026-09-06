package com.ezquest.astralclef.world;

import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Craft pipeline for Ad Astra rockets via Create/Mechanical Crafting.
 * Prefers Create jobs (mechanical crafter / sequenced assembly) when a
 * recipe id is known; falls back to gather when executor has no success.
 * All calls are soft — they degrade to GatherTask if Create path missing.
 */
public final class RocketCraftHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/rocket-craft");

	private RocketCraftHelper() {}

	/** Known Ad Astra rocket recipe ids (pack may use KubeJS overrides — try each). */
	public static String[] rocketRecipeCandidates(AdAstraRoutes.Destination dest) {
		String rocket = RocketHelper.rocketIdFor(dest);
		// Try common pack ids: ad_astra crafting + kubejs wrappers
		return new String[]{
				"ad_astra:" + dest.name().toLowerCase() + "_rocket",
				"ad_astra:nasa_workbench/" + rocket.substring(rocket.indexOf(':') + 1),
				"kubejs:shaped/" + rocket.substring(rocket.indexOf(':') + 1),
				rocket
		};
	}

	/** Try to craft the rocket via Create mechanical crafting / sequenced assembly. */
	public static boolean tryCraftRocket(AdAstraRoutes.Destination dest) {
		CreateRecipeKinds.init();
		String[] cands = rocketRecipeCandidates(dest);
		for (String id : cands) {
			if (CreateRecipeKinds.tryExecute(CreateRecipeKinds.Kind.MECHANICAL_CRAFTING, id)) {
				CreateRecipeExecutor exec = CreateRecipeExecutor.getInstance();
				if (exec.isSuccess(CreateRecipeKinds.Kind.MECHANICAL_CRAFTING, id)) {
					LOGGER.info("Rocket craft success for {} via {}", dest, id);
					return true;
				}
				// Accepted/progressing — caller should wait
				LOGGER.info("Rocket craft progressing for {} via {}", dest, id);
				return true;
			}
			if (CreateRecipeKinds.tryExecute(CreateRecipeKinds.Kind.SEQUENCED_ASSEMBLY, id)) {
				LOGGER.info("Rocket craft SEQUENCED for {} via {}", dest, id);
				return true;
			}
		}
		LOGGER.debug("Rocket craft: no Create recipe accepted for {}", dest);
		return false;
	}

	/** Whether a rocket craft job has succeeded. */
	public static boolean isCrafted(AdAstraRoutes.Destination dest) {
		for (String id : rocketRecipeCandidates(dest)) {
			if (CreateRecipeExecutor.getInstance().isSuccess(CreateRecipeKinds.Kind.MECHANICAL_CRAFTING, id)
					|| CreateRecipeExecutor.getInstance().isSuccess(CreateRecipeKinds.Kind.SEQUENCED_ASSEMBLY, id)) {
				return true;
			}
		}
		return false;
	}

	/** Human-readable status for /astralclef status. */
	public static String status(AdAstraRoutes.Destination dest) {
		CreateRecipeKinds.init();
		for (String id : rocketRecipeCandidates(dest)) {
			String s = CreateRecipeExecutor.getInstance().status(CreateRecipeKinds.Kind.MECHANICAL_CRAFTING, id);
			if (!"none".equals(s)) return "Rocket " + dest + " mechanical_crafting/" + id + ": " + s;
			s = CreateRecipeExecutor.getInstance().status(CreateRecipeKinds.Kind.SEQUENCED_ASSEMBLY, id);
			if (!"none".equals(s)) return "Rocket " + dest + " sequenced/" + id + ": " + s;
		}
		return "Rocket " + dest + ": no Create job yet (will use GatherTask fallback)";
	}
}
