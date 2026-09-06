package com.ezquest.astralclef.combat;

import com.ezquest.astralclef.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Great Beast combat phase (Ch6 gate before singularity craft).
 * Runs as a {@link Task} so it can be driven by TaskRunner or
 * embedded inside {@link com.ezquest.astralclef.tasks.phases.ChAstralSingularityTask}.
 */
public class GreatBeastPhase extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/combat/beast");

	private enum Step { LOCATE_BEAST, ENGAGE, LOOT, DONE }
	private Step step = Step.LOCATE_BEAST;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof GreatBeastPhase;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_BEAST;
		LOGGER.info("Great Beast phase begun — locate → engage → loot");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case LOCATE_BEAST:
				// TODO: locate Great Beast arena/entity — soft Baritone scan when present
				if (!locateBeast()) {
					LOGGER.info("Great Beast: no arena located — waiting/scouting");
					break;
				}
				step = Step.ENGAGE;
				break;
			case ENGAGE:
				// TODO: real combat AI; for now we gate on weapon check
				var player = firstPlayer();
				if (player != null && !hasWeapon(player)) {
					LOGGER.info("Great Beast: no weapon in inventory — gathering");
					break;
				}
				LOGGER.info("Great Beast engage (stub — no combat AI yet, would attack here)");
				step = Step.LOOT;
				break;
			case LOOT:
				// TODO: verify FTB Quests Ch6 trigger via FtbQuestsHelper
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	private boolean locateBeast() {
		// Soft: if Baritone present and we have a world context, we could path to an arena.
		// No hard dep — return true to keep the stub advancing in tests.
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid()) {
				return true;
			}
		} catch (Throwable ignored) {}
		return true;
	}

	private boolean hasWeapon(net.minecraft.server.network.ServerPlayerEntity player) {
		return com.ezquest.astralclef.inventory.InventoryHelper.hasAny(player,
				"minecraft:netherite_sword", "minecraft:diamond_sword", "minecraft:iron_sword",
				"ad_astra:desh_sword");
	}

	private net.minecraft.server.network.ServerPlayerEntity firstPlayer() {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) return list.get(0);
			}
		} catch (Throwable ignored) {}
		return null;
	}

	public void engage() {
		LOGGER.info("Great Beast engage() called (TaskRunner path preferred)");
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("GreatBeast stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "GreatBeast/" + step;
	}
}
