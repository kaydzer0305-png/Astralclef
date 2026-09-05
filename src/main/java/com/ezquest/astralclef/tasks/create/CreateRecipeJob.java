package com.ezquest.astralclef.tasks.create;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-flight Create / Astral recipe job.
 * <p>
 * Tracks {@link CreateRecipeKinds.Kind}, opaque {@code recipeId}, and a shared
 * {@link Step} pipeline ({@code LOCATE_MACHINE → INSERT → PROCESS → EXTRACT → DONE}).
 * Per-kind semantics for each step are documented on the job and applied in
 * {@link #advancePlaceholder()} — real Create block interaction plugs in later
 * (depot/belt, mechanical crafter, spout/basin, furnace, smith, press, mixer).
 * <p>
 * Without Create on the classpath this class only logs and advances on a short
 * tick threshold so the executor framework stays compile-safe and extensible.
 */
public final class CreateRecipeJob {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-job");

	/** Default ticks to spend in each non-DONE step before advancing (placeholder). */
	public static final int PLACEHOLDER_STEP_TICKS = 20;

	/**
	 * Shared recipe pipeline. Kind-specific machine work maps onto these stages
	 * (see {@link #describeStep()}).
	 */
	public enum Step {
		LOCATE_MACHINE,
		INSERT,
		PROCESS,
		EXTRACT,
		DONE
	}

	private final String recipeId;
	private final CreateRecipeKinds.Kind kind;
	private Step step = Step.LOCATE_MACHINE;
	private int tickCount;
	private int ticksInStep;
	private boolean success;
	private boolean failed;
	private String failReason;

	public CreateRecipeJob(CreateRecipeKinds.Kind kind, String recipeId) {
		if (kind == null) {
			throw new IllegalArgumentException("kind");
		}
		if (recipeId == null || recipeId.isEmpty()) {
			throw new IllegalArgumentException("recipeId");
		}
		this.kind = kind;
		this.recipeId = recipeId;
	}

	public String getRecipeId() {
		return recipeId;
	}

	public CreateRecipeKinds.Kind getKind() {
		return kind;
	}

	public Step getStep() {
		return step;
	}

	public int getTickCount() {
		return tickCount;
	}

	public int getTicksInStep() {
		return ticksInStep;
	}

	public boolean isSuccess() {
		return success;
	}

	public boolean isFailed() {
		return failed;
	}

	public boolean isDone() {
		return step == Step.DONE;
	}

	public String getFailReason() {
		return failReason;
	}

	/**
	 * Advance one server tick. Placeholder path: log kind-specific intent and
	 * move to the next step after {@link #PLACEHOLDER_STEP_TICKS}.
	 *
	 * @return true if the step changed this tick
	 */
	public boolean tick() {
		if (isDone()) {
			return false;
		}
		tickCount++;
		ticksInStep++;
		return advancePlaceholder();
	}

	/**
	 * Compile-safe stand-in for Create machine I/O. Replace bodies with real
	 * block-entity calls when Create is on the classpath; keep the same step
	 * transitions so callers stay stable.
	 */
	private boolean advancePlaceholder() {
		if (ticksInStep < PLACEHOLDER_STEP_TICKS) {
			return false;
		}
		LOGGER.info("CreateRecipeJob {} [{}] {}: {}", recipeId, kind, step, describeStep());
		Step next = nextStep(step);
		step = next;
		ticksInStep = 0;
		if (next == Step.DONE) {
			success = true;
			failed = false;
			LOGGER.info("CreateRecipeJob {} [{}] completed (placeholder)", recipeId, kind);
		}
		return true;
	}

	private static Step nextStep(Step current) {
		switch (current) {
			case LOCATE_MACHINE:
				return Step.INSERT;
			case INSERT:
				return Step.PROCESS;
			case PROCESS:
				return Step.EXTRACT;
			case EXTRACT:
				return Step.DONE;
			case DONE:
			default:
				return Step.DONE;
		}
	}

	/**
	 * Kind-specific description of the current step (for logs / research alignment).
	 */
	public String describeStep() {
		switch (kind) {
			case SEQUENCED_ASSEMBLY:
				return sequencedAssemblyNote();
			case MECHANICAL_CRAFTING:
				return mechanicalCraftingNote();
			case FILLING:
				return fillingNote();
			case BASIN:
				return basinNote();
			case COMPOUND_SMELT:
				return compoundSmeltNote();
			case BRONZE_SMITH:
				return bronzeSmithNote();
			case PRESS_DUST:
				return pressDustNote();
			case MIXER_BASIN:
				return mixerBasinNote();
			case GROUT:
				return groutNote();
			default:
				return step.name();
		}
	}

	// --- Create vanilla-ish sequences ---

	private String sequencedAssemblyNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "locate depot / belt sequenced-assembly line";
			case INSERT:
				return "insert inputs onto depot/belt";
			case PROCESS:
				return "wait deployer/press/saw/spout process";
			case EXTRACT:
				return "extract assembly output";
			default:
				return "done";
		}
	}

	private String mechanicalCraftingNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "locate mechanical crafter grid";
			case INSERT:
				return "fill crafter pattern";
			case PROCESS:
				return "wait mechanical craft";
			case EXTRACT:
				return "extract crafted output";
			default:
				return "done";
		}
	}

	private String fillingNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "locate spout / basin fluid source";
			case INSERT:
				return "insert empty container";
			case PROCESS:
				return "process fill (spout)";
			case EXTRACT:
				return "extract filled container";
			default:
				return "done";
		}
	}

	private String basinNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "locate basin";
			case INSERT:
				return "insert basin inputs";
			case PROCESS:
				return "mix / press in basin";
			case EXTRACT:
				return "extract basin output";
			default:
				return "done";
		}
	}

	// --- Astral-specific (Research / Ch0.5–1) ---

	/** Astral: Andesite Compound → furnace smelt (quests26). */
	private String compoundSmeltNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "Astral: locate compound furnace / smelt station";
			case INSERT:
				return "Astral: insert Andesite Compound + fuel";
			case PROCESS:
				return "Astral: compound furnace smelt";
			case EXTRACT:
				return "Astral: extract smelted compound product";
			default:
				return "done";
		}
	}

	/** Astral: copper + tin → Bronze (not Brass). */
	private String bronzeSmithNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "Astral: locate smith / early alloy station";
			case INSERT:
				return "Astral: insert copper + tin (bronze, not brass)";
			case PROCESS:
				return "Astral: smith bronze alloy";
			case EXTRACT:
				return "Astral: extract bronze";
			default:
				return "done";
		}
	}

	/** Astral: press cobble×4 → andesite dust, then press dust. */
	private String pressDustNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "Astral: locate mechanical press for cobble→dust";
			case INSERT:
				return "Astral: insert cobble×4 (then dust on second pass)";
			case PROCESS:
				return "Astral: press cobble to dust / press dust";
			case EXTRACT:
				return "Astral: extract andesite dust / pressed product";
			default:
				return "done";
		}
	}

	/** Mixer + Basin kinetic mix. */
	private String mixerBasinNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "Astral: locate Mixer + Basin kinetic setup";
			case INSERT:
				return "Astral: insert mixer/basin inputs";
			case PROCESS:
				return "Astral: run mixer+basin mix";
			case EXTRACT:
				return "Astral: extract mixed output";
			default:
				return "done";
		}
	}

	/** Grout via Mixer (quests25) — Chapter 2 unlock gate. */
	private String groutNote() {
		switch (step) {
			case LOCATE_MACHINE:
				return "Astral: locate Mixer for grout (quests25)";
			case INSERT:
				return "Astral: insert grout ingredients into mixer/basin";
			case PROCESS:
				return "Astral: mix grout";
			case EXTRACT:
				return "Astral: extract grout (Ch2 unlock gate)";
			default:
				return "done";
		}
	}

	/** Mark failed and jump to DONE (e.g. machine missing when Create APIs land). */
	public void fail(String reason) {
		this.failed = true;
		this.success = false;
		this.failReason = reason != null ? reason : "unknown";
		this.step = Step.DONE;
		LOGGER.warn("CreateRecipeJob {} [{}] failed: {}", recipeId, kind, failReason);
	}

	@Override
	public String toString() {
		return "CreateRecipeJob{kind=" + kind + ", recipeId='" + recipeId + "', step=" + step
				+ ", ticks=" + tickCount + ", success=" + success + ", failed=" + failed + '}';
	}
}
