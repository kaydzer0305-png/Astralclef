package com.ezquest.astralclef.command;

import com.ezquest.astralclef.AstralclefMod;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.task.TaskRunner;
import com.ezquest.astralclef.tasks.phases.Ch01GettingStartedTask;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;

/**
 * Registers {@code /astralclef} commands that drive {@link TaskRunner}.
 * {@code /astralclef ch01} starts the Ch0.5–1 Create loop.
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
						.then(CommandManager.literal("cancel")
								.executes(AstralCommands::cancel))
						.then(CommandManager.literal("status")
								.executes(AstralCommands::status))
						.then(CommandManager.literal("tick")
								.executes(AstralCommands::manualTick)));
	}

	private static int startCh01(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().runUserTask(new Ch01GettingStartedTask());
		ctx.getSource().sendFeedback(
				new LiteralText("Astralclef: started Ch0.5–1 Getting Started (Create loop)"),
				true);
		return 1;
	}

	private static int cancel(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().cancel();
		ctx.getSource().sendFeedback(new LiteralText("Astralclef: task cancelled"), true);
		return 1;
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		Task task = TaskRunner.getInstance().getUserTask();
		String msg = task == null ? "idle" : task.toString();
		ctx.getSource().sendFeedback(new LiteralText("Astralclef: " + msg), false);
		return 1;
	}

	/** Manual tick for stub testing before a server-tick hook is always on. */
	private static int manualTick(CommandContext<ServerCommandSource> ctx) {
		TaskRunner.getInstance().tick();
		Task task = TaskRunner.getInstance().getUserTask();
		String msg = task == null ? "idle (finished or none)" : task.toString();
		ctx.getSource().sendFeedback(new LiteralText("Astralclef tick → " + msg), false);
		return 1;
	}
}
