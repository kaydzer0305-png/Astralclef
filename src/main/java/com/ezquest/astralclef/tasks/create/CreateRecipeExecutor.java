package com.ezquest.astralclef.tasks.create;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton executor for in-flight {@link CreateRecipeJob}s.
 * <p>
 * {@link #tick()} advances every active job one server tick (placeholder step
 * delays today). Wire from the same {@code ServerTickEvents.END_SERVER_TICK}
 * callback as {@code TaskRunner} — after {@code TaskRunner.tick()}.
 * <p>
 * <b>Create mod APIs:</b> machine locate / insert / process / extract are still
 * compile-safe placeholders inside {@link CreateRecipeJob}. When Create is on
 * the classpath, plug block-entity and inventory helpers into the job step
 * bodies; this executor's map / start / status / isDone contract should not need
 * to change.
 */
public final class CreateRecipeExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-executor");
	private static final CreateRecipeExecutor INSTANCE = new CreateRecipeExecutor();

	/** kind + '\0' + recipeId → job */
	private final Map<String, CreateRecipeJob> jobs = new LinkedHashMap<>();

	private CreateRecipeExecutor() {}

	public static CreateRecipeExecutor getInstance() {
		return INSTANCE;
	}

	private static String key(CreateRecipeKinds.Kind kind, String recipeId) {
		return kind.name() + '\0' + recipeId;
	}

	/**
	 * Start a job for {@code kind}/{@code recipeId}, or return the existing one
	 * if still in flight / already finished for this key.
	 */
	public CreateRecipeJob start(CreateRecipeKinds.Kind kind, String recipeId) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(recipeId, "recipeId");
		String k = key(kind, recipeId);
		CreateRecipeJob existing = jobs.get(k);
		if (existing != null) {
			return existing;
		}
		CreateRecipeJob job = new CreateRecipeJob(kind, recipeId);
		jobs.put(k, job);
		LOGGER.info("Started Create recipe job: {} [{}]", recipeId, kind);
		return job;
	}

	/** Advance all non-done jobs one tick; prune finished entries after a grace? keep until cleared. */
	public void tick() {
		if (jobs.isEmpty()) {
			return;
		}
		for (CreateRecipeJob job : jobs.values()) {
			if (!job.isDone()) {
				job.tick();
			}
		}
	}

	public CreateRecipeJob get(CreateRecipeKinds.Kind kind, String recipeId) {
		if (kind == null || recipeId == null) {
			return null;
		}
		return jobs.get(key(kind, recipeId));
	}

	public boolean isDone(CreateRecipeKinds.Kind kind, String recipeId) {
		CreateRecipeJob job = get(kind, recipeId);
		return job != null && job.isDone();
	}

	public boolean isSuccess(CreateRecipeKinds.Kind kind, String recipeId) {
		CreateRecipeJob job = get(kind, recipeId);
		return job != null && job.isDone() && job.isSuccess();
	}

	/**
	 * Human-readable status for commands / debugging.
	 */
	public String status(CreateRecipeKinds.Kind kind, String recipeId) {
		CreateRecipeJob job = get(kind, recipeId);
		if (job == null) {
			return "none";
		}
		if (job.isFailed()) {
			return "FAILED@" + job.getStep() + " ticks=" + job.getTickCount() + " reason=" + job.getFailReason();
		}
		if (job.isDone() && job.isSuccess()) {
			return "DONE ticks=" + job.getTickCount();
		}
		return job.getStep() + " ticks=" + job.getTickCount()
				+ " inStep=" + job.getTicksInStep()
				+ " — " + job.describeStep();
	}

	public String statusSummary() {
		if (jobs.isEmpty()) {
			return "no create recipe jobs";
		}
		StringBuilder sb = new StringBuilder();
		sb.append(jobs.size()).append(" job(s):");
		for (CreateRecipeJob job : jobs.values()) {
			sb.append("\n  ").append(job.getKind()).append('/').append(job.getRecipeId())
					.append(" → ").append(status(job.getKind(), job.getRecipeId()));
		}
		return sb.toString();
	}

	public Collection<CreateRecipeJob> jobs() {
		return Collections.unmodifiableCollection(jobs.values());
	}

	/** Remove finished jobs (success or fail). */
	public int clearFinished() {
		int removed = 0;
		Iterator<Map.Entry<String, CreateRecipeJob>> it = jobs.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue().isDone()) {
				it.remove();
				removed++;
			}
		}
		return removed;
	}

	/**
	 * Handler helper: ensure a job is started; return true when accepted,
	 * still progressing, or successfully done. False only when failed.
	 */
	public boolean acceptOrProgress(CreateRecipeKinds.Kind kind, String recipeId) {
		CreateRecipeJob job = start(kind, recipeId);
		if (job.isFailed()) {
			return false;
		}
		if (job.isDone() && job.isSuccess()) {
			return true;
		}
		// In-flight: tick pipeline runs on END_SERVER_TICK; reporting accept is enough.
		return true;
	}
}
