package com.ezquest.astralclef.tasks.phases.ch01;

import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import com.ezquest.astralclef.world.BlockPlacementHelper;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ch0.5–1 phase: craft/place early Create kinetic chain
 * (shaft, cogwheel, hand crank / water wheel, mechanical press).
 */
public final class EarlyCreateMachinesSubtask extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01/machines");

	private enum Step {
		CRAFT_KINETICS,
		PLACE_POWER,
		PLACE_PRESS,
		DONE
	}

	private Step step = Step.CRAFT_KINETICS;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof EarlyCreateMachinesSubtask;
	}

	@Override
	protected void onStart() {
		step = Step.CRAFT_KINETICS;
		LOGGER.info("Early Create machines: kinetics + press");
	}

	@Override
	protected Task onTick() {
		switch (step) {
			case CRAFT_KINETICS:
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/shaft");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/cogwheel");
				step = Step.PLACE_POWER;
				break;
			case PLACE_POWER:
				if (!tryPlace("create:hand_crank") && !tryPlace("create:water_wheel")) {
					LOGGER.info("Place power: no crank/water wheel item yet — deferring (inventory check)");
				}
				step = Step.PLACE_PRESS;
				break;
			case PLACE_PRESS:
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.MECHANICAL_CRAFTING,
						"create:crafting/kinetics/mechanical_press");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.SEQUENCED_ASSEMBLY,
						"create:sequenced_assembly/precision_mechanism");
				CreateRecipeKinds.tryExecute(
						CreateRecipeKinds.Kind.FILLING,
						"create:filling/sweet_roll");
				tryPlace("create:mechanical_press");
				step = Step.DONE;
				break;
			case DONE:
				break;
		}
		return null;
	}

	private boolean tryPlace(String blockId) {
		try {
			var ctx = com.ezquest.astralclef.tasks.create.CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx == null || !ctx.isValid() || ctx.getWorld() == null || ctx.getWorld().getServer() == null) return false;
			var player = ctx.getWorld().getServer().getPlayerManager().getPlayerList().isEmpty() ? null : ctx.getWorld().getServer().getPlayerManager().getPlayerList().get(0);
			if (player == null) return false;
			BlockPos pos = BlockPlacementHelper.findPlacePos(player, 3);
			return BlockPlacementHelper.place(player, blockId, pos);
		} catch (Throwable t) {
			LOGGER.debug("tryPlace {} failed: {}", blockId, t.toString());
			return false;
		}
	}

	@Override
	protected void onStop(Task interrupt) {
		LOGGER.debug("Early Create machines stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "EarlyCreateMachines/" + step;
	}
}
