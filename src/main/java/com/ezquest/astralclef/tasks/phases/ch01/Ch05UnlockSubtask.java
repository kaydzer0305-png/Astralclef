package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.bot.BotSmelting;
import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.recipes.KubeJsAwareCatalogue;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import com.ezquest.astralclef.world.BlockPlacementHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Ch0.5 unlock critical path (assorted_goals) — skip food/alcohol/Chipped.
 * <ol>
 *   <li>Crafting Table → Hephaestus (Patterns → Part Builder → Tinker Station) or copper tools</li>
 *   <li>Furnace</li>
 *   <li>Mine Iron + Tin + Copper (Pickadze wood-tier only)</li>
 *   <li>Smelt to ingots through the placed furnace (real smelting, coal gathered as fuel)</li>
 *   <li>Essential Materials — Tin, Copper, Andesite, Clay</li>
 * </ol>
 * FTB: quests31 (Ch1 unlock). SNBT edges unverified.
 * <p>
 * Gathers missing items via {@link GatherTask} and places the crafting table /
 * furnace in the world once (idempotent — skips when the block already exists
 * nearby). Refreshes {@link KubeJsAwareCatalogue#shared()} so later subtasks can
 * resolve {@link Ch01RecipeIds} / bind placeholders. Does not start Create jobs
 * (those run in Alloy/Mixer/Grout).
 */
public final class Ch05UnlockSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/ch05");

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String FURNACE = "minecraft:furnace";
	private static final List<String> LOG_BLOCKS =
			List.of("minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log");

	private enum Step {
		CRAFTING_AND_TOOLS,
		FURNACE,
		MINE_METALS,
		SMELT_METALS,
		ESSENTIAL_MATERIALS,
		DONE
	}

	private Step step = Step.CRAFTING_AND_TOOLS;
	private Task gatherTask;
	private boolean craftingTablePlaced;
	private boolean furnacePlaced;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof Ch05UnlockSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.CRAFTING_AND_TOOLS;
		gatherTask = null;
		craftingTablePlaced = false;
		furnacePlaced = false;
		CreateRecipeKinds.init();
		KubeJsAwareCatalogue.shared().refresh();
		LOGGER.info("Ch0.5 unlock: tools → furnace → Fe/Sn/Cu → essential materials (quests31)");
	}

	@Override
	protected Task onTick() {
		KubeJsAwareCatalogue cat = KubeJsAwareCatalogue.shared();
		ServerPlayerEntity player = firstPlayer();
		switch (step) {
			case CRAFTING_AND_TOOLS:
				if (player == null) {
					step = Step.FURNACE;
					break;
				}
				if (!InventoryHelper.hasItem(player, CRAFTING_TABLE, 1)) {
					return driveGather(CRAFTING_TABLE, 1, LOG_BLOCKS, 24, 200);
				}
				if (!craftingTablePlaced) {
					if (BlockPlacementHelper.findBlockNearby(player, CRAFTING_TABLE, 6) != null) {
						craftingTablePlaced = true;
					} else if (BlockPlacementHelper.place(player, CRAFTING_TABLE,
							BlockPlacementHelper.findPlacePos(player, 3))) {
						craftingTablePlaced = true;
						LOGGER.info("Ch0.5 unlock: placed crafting table");
					} else {
						break; // no free spot yet — retry next tick
					}
				}
				gatherTask = null;
				step = Step.FURNACE;
				break;
			case FURNACE:
				LOGGER.debug("furnace prep; compound_smelt known={}",
						cat.knows(Ch01RecipeIds.ANDESITE_COMPOUND_SMELT));
				if (player == null) {
					step = Step.MINE_METALS;
					break;
				}
				if (!furnacePlaced && BlockPlacementHelper.findBlockNearby(player, FURNACE, 8) != null) {
					furnacePlaced = true;
				}
				if (!furnacePlaced && !InventoryHelper.hasItem(player, FURNACE, 1)) {
					return driveGather("minecraft:cobblestone", 8);
				}
				if (!furnacePlaced) {
					if (BlockPlacementHelper.place(player, FURNACE,
							BlockPlacementHelper.findPlacePos(player, 3))) {
						furnacePlaced = true;
						LOGGER.info("Ch0.5 unlock: placed furnace");
					} else {
						break; // no free spot yet — retry next tick
					}
				}
				gatherTask = null;
				step = Step.MINE_METALS;
				break;
			case MINE_METALS:
				if (player != null) {
					String[] ores = {"minecraft:raw_iron", "techreborn:raw_tin", "minecraft:raw_copper"};
					for (String ore : ores) {
						if (!InventoryHelper.hasItem(player, ore, 2)) {
							LOGGER.info("Ch0.5 unlock: gather {}", ore);
							return driveGather(ore, 4);
						}
					}
				}
				gatherTask = null;
				step = Step.SMELT_METALS;
				break;
			case SMELT_METALS: {
				if (player == null) {
					step = Step.ESSENTIAL_MATERIALS;
					break;
				}
				// Bronze needs copper + tin ingots; tools want iron. Smelt one
				// metal at a time through the placed furnace (real items).
				String[][] smelts = {
						{"minecraft:raw_copper", "minecraft:copper_ingot"},
						{"techreborn:raw_tin", "techreborn:tin_ingot"},
						{"minecraft:raw_iron", "minecraft:iron_ingot"},
				};
				boolean busy = false;
				for (String[] pair : smelts) {
					if (InventoryHelper.hasItem(player, pair[1], 2)) {
						continue;
					}
					if (!InventoryHelper.hasItem(player, pair[0], 1)) {
						return driveGather(pair[0], 4);
					}
					switch (BotSmelting.tickSmelt(player, pair[0], pair[1], 2)) {
						case DONE:
							continue;
						case NEED_FUEL:
							LOGGER.info("Ch0.5 unlock: furnace needs fuel for {}", pair[1]);
							return driveGather("minecraft:coal", 4);
						case NO_FURNACE:
							if (BlockPlacementHelper.findBlockNearby(player, FURNACE, 8) == null) {
								BlockPos spot = BlockPlacementHelper.findPlacePos(player, 3);
								if (!BlockPlacementHelper.place(player, FURNACE, spot)
										&& !InventoryHelper.hasItem(player, FURNACE, 1)) {
									return driveGather("minecraft:cobblestone", 8);
								}
							}
							busy = true;
							break;
						case NEED_INPUT:
						case WORKING:
						default:
							busy = true;
							break;
					}
					break; // one furnace job at a time
				}
				if (!busy) {
					gatherTask = null;
					step = Step.ESSENTIAL_MATERIALS;
				}
				break;
			}
			case ESSENTIAL_MATERIALS:
				LOGGER.debug("essential materials; bronze_smith known={} grout known={}",
						cat.knows(Ch01RecipeIds.BRONZE_SMITH),
						cat.knows(Ch01RecipeIds.GROUT));
				if (player != null) {
					String[][] needs = {
							{"minecraft:andesite", "8"},
							{"minecraft:clay_ball", "8"},
					};
					for (String[] need : needs) {
						int want = Integer.parseInt(need[1]);
						if (!InventoryHelper.hasItem(player, need[0], want)) {
							LOGGER.info("Ch0.5 unlock: essential material {} x{}", need[0], want);
							return driveGather(need[0], want);
						}
					}
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

	/** Reuse the in-flight gather child until it finishes, then replace it. */
	private Task driveGather(String itemId, int count) {
		if (gatherTask == null || gatherTask.isFinished()) {
			gatherTask = new GatherTask(itemId, count);
			LOGGER.info("Ch0.5 unlock: gather {} x{} via GatherTask", itemId, count);
		}
		return gatherTask;
	}

	private Task driveGather(String itemId, int count, List<String> blockIds, int radius, int timeout) {
		if (gatherTask == null || gatherTask.isFinished()) {
			gatherTask = new GatherTask(itemId, count, blockIds, radius, timeout);
			LOGGER.info("Ch0.5 unlock: gather {} x{} via GatherTask", itemId, count);
		}
		return gatherTask;
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

	@Override
	protected void onStop(Task interrupt) {
		gatherTask = null;
		LOGGER.debug("Ch0.5 unlock stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "Ch05Unlock/" + step;
	}
}
