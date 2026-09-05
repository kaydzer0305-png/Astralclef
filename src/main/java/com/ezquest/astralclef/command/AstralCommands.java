package com.ezquest.astralclef.command;

import com.ezquest.astralclef.task.TaskRunner;
import com.ezquest.astralclef.tasks.phases.Ch01GettingStartedTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Astralclef command registration hooks.
 * Full Brigadier {@code /astralclef} wiring can attach to these entry points later;
 * {@link #startCh01()} is the compile-safe placeholder that starts Getting Started.
 */
public final class AstralCommands {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/commands");
	private static boolean registered;

	private AstralCommands() {}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		// TODO: CommandRegistrationCallback — /astralclef start|stop|status
		LOGGER.info("Astralclef command hooks ready (use startCh01 / stop / status)");
	}

	/** Placeholder command body: start Ch0.5–1 Getting Started on the TaskRunner. */
	public static void startCh01() {
		TaskRunner.getInstance().runUserTask(new Ch01GettingStartedTask());
		LOGGER.info("Started Ch01GettingStartedTask");
	}

	public static void stop() {
		TaskRunner.getInstance().cancel();
	}

	public static String status() {
		var runner = TaskRunner.getInstance();
		var task = runner.getUserTask();
		if (task == null) {
			return "idle";
		}
		return (runner.isActive() ? "active: " : "pending: ") + task;
	}
}
