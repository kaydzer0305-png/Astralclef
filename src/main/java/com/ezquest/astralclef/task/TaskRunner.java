package com.ezquest.astralclef.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the active user task and ticks it each game tick (when wired).
 * Supports one level of nested subtask returned from {@link Task#onTick()}.
 */
public final class TaskRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/taskrunner");
	private static final TaskRunner INSTANCE = new TaskRunner();

	private Task userTask;
	private Task subTask;
	private boolean ticking;

	private TaskRunner() {}

	public static TaskRunner getInstance() {
		return INSTANCE;
	}

	/** Replace the current user task (stops any previous). */
	public void runUserTask(Task task) {
		if (task == null) {
			cancel();
			return;
		}
		if (userTask != null && userTask.isEqual(task) && userTask.isActive()) {
			return;
		}
		stopChain(task);
		userTask = task;
		LOGGER.info("User task set: {}", task);
	}

	public void cancel() {
		stopChain(null);
		userTask = null;
		LOGGER.info("User task cancelled");
	}

	public Task getUserTask() {
		return userTask;
	}

	public boolean isActive() {
		return userTask != null && (userTask.isActive() || !userTask.isFinished());
	}

	/**
	 * Tick the active user task (and its subtask if any).
	 * Safe to call from a server tick hook when one is registered.
	 */
	public void tick() {
		if (userTask == null || ticking) {
			return;
		}
		ticking = true;
		try {
			if (userTask.isFinished()) {
				stopChain(null);
				userTask = null;
				return;
			}

			Task next = userTask.tick();

			if (next == null) {
				if (subTask != null) {
					subTask.stop(null);
					subTask = null;
				}
			} else if (subTask != null && subTask.isEqual(next)) {
				if (!subTask.isFinished()) {
					subTask.tick();
				}
			} else {
				if (subTask != null) {
					subTask.stop(next);
				}
				subTask = next;
				if (!subTask.isFinished()) {
					subTask.tick();
				}
			}

			if (userTask != null && userTask.isFinished()) {
				stopChain(null);
				userTask = null;
			}
		} finally {
			ticking = false;
		}
	}

	private void stopChain(Task interrupt) {
		if (subTask != null) {
			subTask.stop(interrupt);
			subTask = null;
		}
		if (userTask != null) {
			userTask.stop(interrupt);
		}
	}
}
