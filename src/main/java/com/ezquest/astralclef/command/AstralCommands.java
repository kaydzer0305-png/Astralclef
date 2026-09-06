package com.ezquest.astralclef.command;

import com.ezquest.astralclef.AstralclefMod;
import com.ezquest.astralclef.recipes.Ch01RecipeBindings;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.task.TaskRunner;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.world.CreateWorldContext;
import com.ezquest.astralclef.combat.GreatBeastPhase;
import com.ezquest.astralclef.tasks.phases.Ch01GettingStartedTask;
import com.ezquest.astralclef.tasks.phases.ChAstralSingularityTask;
import com.ezquest.astralclef.tasks.phases.ChMarsTask;
import com.ezquest.astralclef.tasks.phases.ChMercuryTask;
import com.ezquest.astralclef.tasks.phases.ChMoonTask;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;

/**
 * Registers {@code /astralclef} commands that drive {@link TaskRunner} and Create world context.
 */
public final class AstralCommands {
	private AstralCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(AstralCommands::registerCommands);
		AstralclefMod.LOGGER.info("Astralclef commands registered");
	}

	private static void registerCommands(
			CommandDispatcher<ServerCommandSource> dispatcher,
			boolean dedicated) {
		dispatcher.register(
				CommandManager.literal("astralclef")
						.then(CommandManager.literal("ch01")
								.executes(AstralCommands::startCh01))
						.then(CommandManager.literal("moon")
								.executes(AstralCommands::startMoon))
						.then(CommandManager.literal("mars")
								.executes(AstralCommands::startMars))
						.then(CommandManager.literal("mercury")
								.executes(AstralCommands::startMercury))
						.then(CommandManager.literal("singularity")
								.executes(AstralCommands::startSingularity))
						.then(CommandManager.literal("beast")
								.executes(AstralCommands::startBeast))
						.then(CommandManager.literal("cancel")
								.executes(AstralCommands::cancel))
						.then(CommandManager.literal("status")
								.executes(AstralCommands::status))
						.then(CommandManager.literal("context")
								.executes(AstralCommands::setContext))
						.then(CommandManager.literal("tick")
								.executes(AstralCommands::manualTick))
						.then(CommandManager.literal("recipes")
								.executes(AstralCommands::dumpRecipes)));
	}

	private static int startCh01(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().runUserTask(new Ch01GettingStartedTask());
		ctx.getSource().sendFeedback(
				new LiteralText("Astralclef: started Ch0.5–1 Getting Started (Create loop)"),
				true);
		return 1;
	}

	private static int startMoon(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().runUserTask(new ChMoonTask());
		ctx.getSource().sendFeedback(new LiteralText("Astralclef: started Moon phase"), true);
		return 1;
	}

	private static int startMars(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().runUserTask(new ChMarsTask());
		ctx.getSource().sendFeedback(new LiteralText("Astralclef: started Mars phase"), true);
		return 1;
	}

	private static int startMercury(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().runUserTask(new ChMercuryTask());
		ctx.getSource().sendFeedback(new LiteralText("Astralclef: started Mercury phase"), true);
		return 1;
	}

	private static int startSingularity(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().runUserTask(new ChAstralSingularityTask());
		ctx.getSource().sendFeedback(new LiteralText("Astralclef: started Singularity (Ch6 win)"), true);
		return 1;
	}

	private static int startBeast(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().runUserTask(new GreatBeastPhase());
		ctx.getSource().sendFeedback(new LiteralText("Astralclef: started Great Beast"), true);
		return 1;
	}

	private static int cancel(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().cancel();
		ctx.getSource().sendFeedback(new LiteralText("Astralclef: task cancelled"), true);
		return 1;
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		Task task = TaskRunner.getInstance().getUserTask();
		String taskMsg = task == null ? "idle" : task.toString();
		String createMsg = CreateRecipeExecutor.getInstance().statusSummary();
		ctx.getSource().sendFeedback(new LiteralText("Astralclef task: " + taskMsg), false);
		ctx.getSource().sendFeedback(new LiteralText("Create: " + createMsg), false);
		// Soft helpers status (no hard deps)
		try {
			ctx.getSource().sendFeedback(new LiteralText(
					com.ezquest.astralclef.quests.FtbQuestsHelper.status(ctx.getSource().getServer())), false);
			ctx.getSource().sendFeedback(new LiteralText(
					com.ezquest.astralclef.movement.BaritoneHelper.status()), false);
		} catch (Throwable ignored) {}
		return 1;
	}

	/** Bind Create locate context to the executing player world + block pos. */
	private static int setContext(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		ServerPlayerEntity player;
		try {
			player = src.getPlayer();
		} catch (Exception e) {
			src.sendError(new LiteralText("Astralclef context requires a player"));
			return 0;
		}
		ServerWorld world = (ServerWorld) player.getWorld();
		CreateRecipeExecutor.getInstance().setWorldContext(
				world, player.getBlockPos(), CreateWorldContext.DEFAULT_SEARCH_RADIUS);
		src.sendFeedback(new LiteralText("Astralclef context: " + CreateRecipeExecutor.getInstance().getWorldContext()), true);
		return 1;
	}

	private static int manualTick(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().tick();
		CreateRecipeExecutor.getInstance().tick(ctx.getSource().getServer());
		Task task = TaskRunner.getInstance().getUserTask();
		String msg = task == null ? "idle (finished or none)" : task.toString();
		ctx.getSource().sendFeedback(new LiteralText("Astralclef tick → " + msg), false);
		ctx.getSource().sendFeedback(new LiteralText(CreateRecipeExecutor.getInstance().statusSummary()), false);
		return 1;
	}

	/** Dump Ch01 bind → resolved RecipeManager pack ids (REI-friendly confirm). */
	private static int dumpRecipes(CommandContext<ServerCommandSource> ctx) {
		java.util.List<String> lines = Ch01RecipeBindings.dumpCh01(ctx.getSource().getServer());
		for (String line : lines) {
			ctx.getSource().sendFeedback(new LiteralText(line), false);
		}
		return lines.size();
	}
}
