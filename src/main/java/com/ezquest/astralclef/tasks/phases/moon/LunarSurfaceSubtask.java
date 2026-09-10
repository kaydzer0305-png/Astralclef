package com.ezquest.astralclef.tasks.phases.moon;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Lunar surface ops: establish base, mine moon resources, tech uplift.
 */
public final class LunarSurfaceSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/moon/surface");

	/** Moon cheese + desh goal for the Mars gate (raw desh → desh quest line). */
	private static final String DESH_INGOT = "ad_astra:desh_ingot";
	private static final List<String> MOON_DESH_BLOCKS =
			List.of("ad_astra:moon_desh_ore", "ad_astra:desh_ore");

	private enum Step {
		ESTABLISH_BASE,
		MINE_MOON_ORES,
		DONE
	}

	private Step step = Step.ESTABLISH_BASE;
	private Task gatherTask;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof LunarSurfaceSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.ESTABLISH_BASE;
		gatherTask = null;
		LOGGER.info("Lunar surface ops begun");
	}

	@Override
	protected Task onTick() {
		ServerPlayerEntity player = firstPlayer();
		switch (step) {
			case ESTABLISH_BASE:
				// Gate on Moon dimension FTB quest (soft — passes when FTB absent/no context)
				if (!isMoonReached()) {
					LOGGER.debug("Moon surface: Moon dimension quest {} not yet complete — waiting", com.ezquest.astralclef.quests.AstralQuests.CH3_MOON_DIMENSION);
					break;
				}
				step = Step.MINE_MOON_ORES;
				break;
			case MINE_MOON_ORES:
				// Desh stockpile for the Mars gate (Ch4 desh quest line).
				if (player != null && !InventoryHelper.hasItem(player, DESH_INGOT, 4)) {
					if (gatherTask == null || gatherTask.isFinished()) {
						gatherTask = new GatherTask(DESH_INGOT, 8, MOON_DESH_BLOCKS, 32, 1200);
						LOGGER.info("Moon surface: mining desh via GatherTask");
					}
					return gatherTask;
				}
				gatherTask = null;
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		if (gatherTask != null && gatherTask.isFinished()) {
			gatherTask = null;
		}
		return null;
	}

	private boolean isMoonReached() {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx == null || !ctx.isValid() || ctx.getWorld() == null || ctx.getWorld().getServer() == null) return true;
			var server = ctx.getWorld().getServer();
			if (!com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestsPresent(server)) return true;
			var player = server.getPlayerManager().getPlayerList().isEmpty() ? null : server.getPlayerManager().getPlayerList().get(0);
			if (player == null) return true;
			return com.ezquest.astralclef.quests.FtbQuestsHelper.isQuestComplete(server, player, com.ezquest.astralclef.quests.AstralQuests.CH3_MOON_DIMENSION);
		} catch (Throwable t) { return true; }
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

	@Override
	protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("LunarSurface stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "MoonSurface/" + step;
	}
}
