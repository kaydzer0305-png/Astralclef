package com.ezquest.astralclef.tasks.phases.singularity;

import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singularity craft: stockpile the planet-line metals (Moon desh, Mars ostrum,
 * Mercury calorite) that feed the Ch6 Create line, then fire the Astral
 * Singularity sequenced-assembly job and wait for the win item.
 * <p>
 * Completes when the player holds {@code createastral:astral_singularity} or
 * the assembly job finishes; otherwise waits with periodic guidance (the full
 * factory still needs player-scale automation).
 */
public final class CraftSingularitySubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/singularity/craft");

	private static final String ASTRAL_SINGULARITY = "createastral:astral_singularity";
	/** Planet mats feeding the Ch6 line: Moon → Mars → Mercury order. */
	private static final String[][] PLANET_MATS = {
			{"ad_astra:desh_ingot", "8"},
			{"ad_astra:ostrum_ingot", "8"},
			{"ad_astra:calorite_ingot", "8"},
	};

	private enum Step { GATHER_SINGULARITY_INPUTS, CREATE_SINGULARITY, DONE }
	private Step step = Step.GATHER_SINGULARITY_INPUTS;
	private Task gatherTask;
	private int ticks;
	private boolean jobFired;

	@Override public boolean isEqual(Task other) { return other instanceof CraftSingularitySubtask; }
	@Override protected void onStart() {
		step = Step.GATHER_SINGULARITY_INPUTS;
		gatherTask = null;
		ticks = 0;
		jobFired = false;
		LOGGER.info("Singularity craft: gather → create");
	}
	@Override protected Task onTick() {
		ticks++;
		ServerPlayerEntity player = firstPlayer();
		switch (step) {
			case GATHER_SINGULARITY_INPUTS:
				if (player != null && InventoryHelper.hasItem(player, ASTRAL_SINGULARITY, 1)) {
					LOGGER.info("Astral Singularity already held — skipping to DONE");
					gatherTask = null;
					step = Step.DONE;
					break;
				}
				if (player != null) {
					for (String[] mat : PLANET_MATS) {
						int want = Integer.parseInt(mat[1]);
						if (!InventoryHelper.hasItem(player, mat[0], want)) {
							if (gatherTask == null || gatherTask.isFinished()) {
								gatherTask = new GatherTask(mat[0], want,
										GatherTask.blockIdsFor(mat[0]), 32, 1200);
								LOGGER.info("Singularity craft: gather {} x{}", mat[0], want);
							}
							return gatherTask;
						}
					}
				}
				gatherTask = null;
				step = Step.CREATE_SINGULARITY;
				break;
			case CREATE_SINGULARITY:
				if (player != null && InventoryHelper.hasItem(player, ASTRAL_SINGULARITY, 1)) {
					LOGGER.info("Astral Singularity crafted — win item present");
					step = Step.DONE;
					break;
				}
				if (!jobFired) {
					jobFired = true;
					CreateRecipeKinds.tryExecute(
							CreateRecipeKinds.Kind.SEQUENCED_ASSEMBLY,
							"createastral:sequenced_assembly/astral_singularity");
					LOGGER.info("Singularity craft: sequenced-assembly job fired");
				}
				CreateRecipeExecutor exec = CreateRecipeExecutor.getInstance();
				if (exec.isDone(CreateRecipeKinds.Kind.SEQUENCED_ASSEMBLY,
						"createastral:sequenced_assembly/astral_singularity")) {
					if (exec.isSuccess(CreateRecipeKinds.Kind.SEQUENCED_ASSEMBLY,
							"createastral:sequenced_assembly/astral_singularity")) {
						LOGGER.info("Singularity assembly job succeeded");
					} else {
						LOGGER.warn("Singularity assembly job finished without success — "
								+ "complete the Create line manually, then re-run");
					}
					step = Step.DONE;
					break;
				}
				if (ticks % 100 == 1) {
					LOGGER.info("Singularity craft: waiting on Create assembly line "
							+ "(need {} in inventory or job completion)", ASTRAL_SINGULARITY);
				}
				break;
			case DONE: break;
		}
		if (gatherTask != null && gatherTask.isFinished()) gatherTask = null;
		return null;
	}

	private ServerPlayerEntity firstPlayer() {
		try {
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) return list.get(0);
			}
		} catch (Throwable ignored) {}
		return null;
	}

	@Override protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("CraftSingularity stopped at {} (interrupt={})", step, interrupt);
	}
	@Override public boolean isFinished() { return step == Step.DONE; }
	@Override protected String toDebugString() { return "SingularityCraft/" + step; }
}
