package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.bot.BotCrafting;
import com.ezquest.astralclef.bot.BotSmelting;
import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.quests.AstralQuests;
import com.ezquest.astralclef.quests.FtbQuestsHelper;
import com.ezquest.astralclef.recipes.Ch01RecipeBindings;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alloy / Casing: Materials → Welcome to Create → Bronze → Compound craft →
 * Compound smelt → Alloy → Casing.
 * <ul>
 *   <li>Bronze = smith Copper+Tin ({@link Ch01RecipeIds#BRONZE_SMITH})</li>
 *   <li>Andesite Compound shaped BBB/AAA/CCC ({@link Ch01RecipeIds#ANDESITE_COMPOUND_SHAPED})</li>
 *   <li>Compound → furnace/blast alloy (quests26) — stock Create alloy recipes removed in Astral</li>
 *   <li>Andesite Casing: strip a log + right-click andesite alloy
 *       (gated on inventory or FTB {@code CH2_ANDESITE_CASING})</li>
 * </ul>
 */
public final class AlloyCasingSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/alloy");

	private static final String ANDESITE_CASING = "create:andesite_casing";
	private static final String IRON_NUGGET = "minecraft:iron_nugget";

	private enum Step {
		GATHER_MATERIALS,
		WELCOME_CREATE,
		BRONZE_SMITH,
		COMPOUND_SHAPED,
		COMPOUND_SMELT,
		ALLOY_STOCKPILE,
		ANDESITE_CASING,
		DONE
	}

	private Step step = Step.GATHER_MATERIALS;
	private Task gatherTask;
	private int ticks;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof AlloyCasingSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.GATHER_MATERIALS;
		gatherTask = null;
		ticks = 0;
		LOGGER.info("Alloy/Casing: Materials → Bronze → Compound shaped → Smelt/Blast → Alloy → Casing");
	}

	@Override
	protected Task onTick() {
		ticks++;
		switch (step) {
			case GATHER_MATERIALS: {
				ServerPlayerEntity player = firstPlayer();
				boolean alloyDone = player != null
						&& InventoryHelper.hasItem(player, Ch01RecipeBindings.ANDESITE_ALLOY, 4);
				if (player != null && !alloyDone
						&& !InventoryHelper.hasItem(player, Ch01RecipeBindings.ANDESITE, 12)) {
					return driveGather(Ch01RecipeBindings.ANDESITE, 12);
				}
				// Pack recipes accept zinc or iron nuggets (#create:alloy_nuggets).
				boolean enoughNuggets = player != null
						&& (InventoryHelper.hasItem(player, Ch01RecipeBindings.ZINC_NUGGET, 6)
								|| InventoryHelper.hasItem(player, IRON_NUGGET, 6));
				if (player != null && !alloyDone && !enoughNuggets) {
					return driveGather(Ch01RecipeBindings.ZINC_NUGGET, 8);
				}
				gatherTask = null;
				step = Step.WELCOME_CREATE;
				break;
			}
			case WELCOME_CREATE:
				step = Step.BRONZE_SMITH;
				break;
			case BRONZE_SMITH:
				if (!awaitKind(CreateRecipeKinds.Kind.BRONZE_SMITH, Ch01RecipeIds.BRONZE_SMITH,
						() -> CreateRecipeKinds.bronzeSmith(Ch01RecipeIds.BRONZE_SMITH))) {
					break;
				}
				step = Step.COMPOUND_SHAPED;
				break;
			case COMPOUND_SHAPED: {
				ServerPlayerEntity shapedPlayer = firstPlayer();
				if (shapedPlayer != null && InventoryHelper.hasItem(shapedPlayer, Ch01RecipeBindings.ANDESITE_COMPOUND, 1)) {
					gatherTask = null;
					step = Step.COMPOUND_SMELT;
					break;
				}
				if (shapedPlayer != null) {
					// Real craft first: BBB/AAA/CCC rows in any order, matched live.
					String a = Ch01RecipeBindings.ANDESITE;
					String z = Ch01RecipeBindings.ZINC_NUGGET;
					String c = Ch01RecipeBindings.CLAY_BALL;
					ItemStack made = BotCrafting.craftFirstMatch(shapedPlayer,
							BotCrafting.grid3(a, z, c), BotCrafting.grid3(a, c, z),
							BotCrafting.grid3(z, a, c), BotCrafting.grid3(z, c, a),
							BotCrafting.grid3(c, a, z), BotCrafting.grid3(c, z, a));
					if (!made.isEmpty()) {
						LOGGER.info("Compound shaped: crafted {} for real", made.getItem());
						gatherTask = null;
						step = Step.COMPOUND_SMELT;
						break;
					}
				}
				if (!awaitKind(CreateRecipeKinds.Kind.MECHANICAL_CRAFTING, Ch01RecipeIds.ANDESITE_COMPOUND_SHAPED,
						() -> CreateRecipeKinds.compoundShaped(Ch01RecipeIds.ANDESITE_COMPOUND_SHAPED))) {
					break;
				}
				step = Step.COMPOUND_SMELT;
				break;
			}
			case COMPOUND_SMELT: {
				// Real furnace smelt when compound is on hand; machine jobs otherwise.
				boolean realSmeltActive = false;
				ServerPlayerEntity smeltPlayer = firstPlayer();
				if (smeltPlayer != null && InventoryHelper.hasItem(smeltPlayer, Ch01RecipeBindings.ANDESITE_COMPOUND, 1)) {
					BotSmelting.SmeltState st = BotSmelting.tickSmelt(smeltPlayer,
							Ch01RecipeBindings.ANDESITE_COMPOUND, Ch01RecipeBindings.ANDESITE_ALLOY, 4);
					if (st == BotSmelting.SmeltState.DONE) {
						gatherTask = null;
						step = Step.ALLOY_STOCKPILE;
						break;
					}
					if (st == BotSmelting.SmeltState.NEED_FUEL) {
						return driveGather("minecraft:coal", 4);
					}
					if (st == BotSmelting.SmeltState.WORKING) {
						realSmeltActive = true;
					}
				}
				if (!realSmeltActive) {
					CreateRecipeKinds.compoundBlast(Ch01RecipeIds.ANDESITE_COMPOUND_BLAST);
					if (!awaitKind(CreateRecipeKinds.Kind.COMPOUND_SMELT, Ch01RecipeIds.ANDESITE_COMPOUND_SMELT,
							() -> CreateRecipeKinds.compoundSmelt(Ch01RecipeIds.ANDESITE_COMPOUND_SMELT))) {
						break;
					}
					step = Step.ALLOY_STOCKPILE;
				}
				break;
			}
			case ALLOY_STOCKPILE:
				// Stockpile is inventory goal; smelt/blast already produce alloy
				step = Step.ANDESITE_CASING;
				break;
			case ANDESITE_CASING: {
				ServerPlayerEntity player = firstPlayer();
				boolean have = player != null && InventoryHelper.hasItem(player, ANDESITE_CASING, 1);
				boolean questDone = isQuestDone(AstralQuests.CH2_ANDESITE_CASING);
				if (have || questDone) {
					gatherTask = null;
					step = Step.DONE;
					break;
				}
				// Make sure alloy exists, then wait for the strip + right-click craft.
				if (player != null && !InventoryHelper.hasItem(player, Ch01RecipeBindings.ANDESITE_ALLOY, 1)) {
					return driveGather(Ch01RecipeBindings.ANDESITE_ALLOY, 4);
				}
				gatherTask = null;
				if (ticks % 100 == 1) {
					LOGGER.info("Andesite casing gate: strip a log and right-click andesite alloy "
							+ "(FTB quest {})", AstralQuests.CH2_ANDESITE_CASING);
				}
				break;
			}
			case DONE:
				break;
		}
		if (gatherTask != null && gatherTask.isFinished()) {
			gatherTask = null;
		}
		return null;
	}

	/** Reuse the in-flight gather child until it finishes, then replace it. */
	private Task driveGather(String itemId, int count) {
		if (gatherTask == null || gatherTask.isFinished()) {
			gatherTask = new GatherTask(itemId, count);
			LOGGER.info("Alloy/Casing prep: gather {} x{}", itemId, count);
		}
		return gatherTask;
	}

	/**
	 * Fire recipe kind and wait until the job is done (success or fail).
	 * @return true when finished and caller may advance
	 */
	private boolean awaitKind(CreateRecipeKinds.Kind kind, String bindId, Runnable fire) {
		fire.run();
		CreateRecipeExecutor exec = CreateRecipeExecutor.getInstance();
		if (!exec.isDone(kind, bindId)) {
			return false;
		}
		if (!exec.isSuccess(kind, bindId)) {
			LOGGER.warn("Alloy step {} finished without success — continuing", bindId);
		}
		return true;
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

	private boolean isQuestDone(String questId) {
		try {
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx == null || !ctx.isValid() || ctx.getWorld() == null || ctx.getWorld().getServer() == null) {
				return false;
			}
			var server = ctx.getWorld().getServer();
			if (!FtbQuestsHelper.isQuestsPresent(server)) {
				return false;
			}
			var player = server.getPlayerManager().getPlayerList().isEmpty()
					? null : server.getPlayerManager().getPlayerList().get(0);
			if (player == null) {
				return false;
			}
			return FtbQuestsHelper.isQuestComplete(server, player, questId);
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("Alloy/Casing stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "AlloyCasing/" + step;
	}
}
