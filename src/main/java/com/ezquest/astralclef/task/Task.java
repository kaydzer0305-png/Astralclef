package com.ezquest.astralclef.task;

/**
 * Minimal Altoclef-style task: start / tick (optional subtask) / stop.
 * Compile-safe; no Minecraft world coupling in the base type.
 */
public abstract class Task {
	private boolean active;

	public final boolean isActive() {
		return active;
	}

	/** Equality for runner subtask reuse — same goal means keep running. */
	public abstract boolean isEqual(Task other);

	protected abstract void onStart();

	/**
	 * Called each tick while active. Return a child task to run as a subtask,
	 * or {@code null} to continue this task (or finish via {@link #isFinished()}).
	 */
	protected abstract Task onTick();

	protected abstract void onStop(Task interrupt);

	/** Debug / status label for logs and commands. */
	protected abstract String toDebugString();

	/**
	 * Override when the task has a clear completion condition.
	 * Default: never finished (phase roots usually manage their own phases).
	 */
	public boolean isFinished() {
		return false;
	}

	final Task tick() {
		if (!active) {
			active = true;
			onStart();
		}
		return onTick();
	}

	final void stop(Task interrupt) {
		if (!active) {
			return;
		}
		active = false;
		onStop(interrupt);
	}

	@Override
	public final String toString() {
		return toDebugString();
	}
}
