package com.ezquest.astralclef.tasks.phases.mercury;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.movement.BaritoneHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import com.ezquest.astralclef.world.StructureLocator;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercuryVaultSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/mercury/vault");
	private enum Step { LOCATE_VAULT, TRAVEL_TO_VAULT, CLEAR_VAULT, RETURN, DONE }
	private Step step = Step.LOCATE_VAULT;
	private BlockPos vaultPos;
	private Task gatherTask;
	private int ticks;

	@Override public boolean isEqual(Task other) { return other instanceof MercuryVaultSubtask; }
	@Override protected void onStart() {
		step = Step.LOCATE_VAULT;
		gatherTask = null;
		ticks = 0;
		LOGGER.info("Mercury vault/return begun");
	}
	@Override protected Task onTick() {
		ticks++;
		var player = firstPlayer();
		switch (step) {
			case LOCATE_VAULT:
				if (player != null) {
					String[] cands = {"ad_astra:mercury_ruins", "minecraft:ancient_city", "minecraft:stronghold"};
					for (String sid : cands) { vaultPos = StructureLocator.locateNearest(player, sid); if (vaultPos != null) break; }
					if (vaultPos != null) { LOGGER.info("Mercury vault at {}", vaultPos.toShortString()); step = Step.TRAVEL_TO_VAULT; break; }
				}
				step = Step.TRAVEL_TO_VAULT; break;
			case TRAVEL_TO_VAULT:
				if (vaultPos != null && player != null) {
					if (player.getBlockPos().getSquaredDistance(vaultPos) < 9) { step = Step.CLEAR_VAULT; break; }
					if (BaritoneHelper.isPresent()) { BaritoneHelper.pathTo(player, vaultPos); break; }
					LOGGER.info("Mercury vault travel stub for {}", vaultPos.toShortString());
				}
				step = Step.CLEAR_VAULT; break;
			case CLEAR_VAULT:
				// Clear = hold Mercury loot (calorite); otherwise stockpile calorite
				// and wait (no automated combat in this stub).
				if (player == null
						|| InventoryHelper.hasAny(player, "ad_astra:calorite_ingot", "ad_astra:raw_calorite")) {
					gatherTask = null;
					step = Step.RETURN;
					break;
				}
				if (gatherTask == null || gatherTask.isFinished()) {
					gatherTask = new GatherTask("ad_astra:calorite_ingot", 4);
					LOGGER.info("Mercury vault: stockpiling calorite loot via GatherTask");
				}
				if (ticks % 100 == 1) {
					LOGGER.info("Mercury vault: clear the vault and loot calorite");
				}
				return gatherTask;
			case RETURN: LOGGER.info("Mercury return gate complete (soft)"); step = Step.DONE; break;
			case DONE: break;
		}
		if (gatherTask != null && gatherTask.isFinished()) gatherTask = null;
		return null;
	}

	private ServerPlayerEntity firstPlayer() {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) return list.get(0);
			}
		} catch (Throwable ignored) {}
		return null;
	}
	@Override protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("MercuryVault stopped at {} (interrupt={})", step, interrupt);
	}
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "MercuryVault/" + step; }
}
