package com.ezquest.astralclef.tasks.create;

import java.util.Optional;

import com.ezquest.astralclef.tasks.create.world.CreateMachineIO;
import com.ezquest.astralclef.tasks.create.world.CreateMachineLocator;
import com.ezquest.astralclef.tasks.create.world.CreateMachineType;
import com.ezquest.astralclef.tasks.create.world.CreateWorldContext;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-flight Create / Astral recipe job.
 * Pipeline: LOCATE_MACHINE → INSERT → PROCESS → EXTRACT → DONE.
 * Uses {@link CreateMachineLocator} / {@link CreateMachineIO} when a world context is set.
 */
public final class CreateRecipeJob {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-job");

	/** Ticks to wait in PROCESS (kinetic work placeholder). */
	public static final int PROCESS_STEP_TICKS = 40;
	/** Fail LOCATE if machine not found after this many ticks. */
	public static final int LOCATE_TIMEOUT_TICKS = 100;
	/** Fail INSERT/EXTRACT after this many unsuccessful ticks. */
	public static final int IO_TIMEOUT_TICKS = 60;

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
	private BlockPos machinePos;
	private ItemStack pendingInsert = ItemStack.EMPTY;
	private ItemStack lastExtracted = ItemStack.EMPTY;

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

	public String getRecipeId() { return recipeId; }
	public CreateRecipeKinds.Kind getKind() { return kind; }
	public Step getStep() { return step; }
	public int getTickCount() { return tickCount; }
	public int getTicksInStep() { return ticksInStep; }
	public boolean isSuccess() { return success; }
	public boolean isFailed() { return failed; }
	public boolean isDone() { return step == Step.DONE; }
	public String getFailReason() { return failReason; }
	public BlockPos getMachinePos() { return machinePos; }
	public ItemStack getLastExtracted() { return lastExtracted; }

	public void setPendingInsert(ItemStack stack) {
		this.pendingInsert = stack == null ? ItemStack.EMPTY : stack;
	}

	public boolean tick() {
		return tick(CreateRecipeExecutor.getInstance().getWorldContext());
	}

	public boolean tick(CreateWorldContext ctx) {
		if (isDone()) {
			return false;
		}
		tickCount++;
		ticksInStep++;
		return advance(ctx);
	}

	private boolean advance(CreateWorldContext ctx) {
		switch (step) {
			case LOCATE_MACHINE:
				return advanceLocate(ctx);
			case INSERT:
				return advanceInsert(ctx);
			case PROCESS:
				return advanceProcess(ctx);
			case EXTRACT:
				return advanceExtract(ctx);
			case DONE:
			default:
				return false;
		}
	}

	private boolean advanceLocate(CreateWorldContext ctx) {
		if (ctx == null || !ctx.isValid()) {
			if (ticksInStep >= LOCATE_TIMEOUT_TICKS) {
				fail("no world context for locate (set /astralclef context or wait for player bind)");
				return true;
			}
			return false;
		}
		CreateMachineType type = CreateMachineType.fromKind(kind);
		Optional<BlockPos> found = CreateMachineLocator.locateForKind(ctx, kind);
		if (found.isPresent()) {
			machinePos = found.get();
			LOGGER.info("CreateRecipeJob {} [{}] located (prefer {}) at {}", recipeId, kind, type, machinePos.toShortString());
			return goTo(Step.INSERT);
		}
		if (ticksInStep >= LOCATE_TIMEOUT_TICKS) {
			fail("machine not found for " + type + " / candidates within radius " + ctx.getSearchRadius()
					+ " of " + ctx.getOrigin().toShortString());
			return true;
		}
		return false;
	}

	private boolean advanceInsert(CreateWorldContext ctx) {
		if (machinePos == null || ctx == null || !ctx.isValid()) {
			fail("insert: missing machine pos or world context");
			return true;
		}
		if (!CreateMachineIO.hasBlockEntity(ctx.getWorld(), machinePos)) {
			fail("insert: block entity missing at " + machinePos.toShortString());
			return true;
		}
		// Empty pending = soft verify-only insert (inputs supplied by later inventory wiring).
		if (pendingInsert.isEmpty()) {
			LOGGER.info("CreateRecipeJob {} [{}] INSERT verify-only at {} — {}",
					recipeId, kind, machinePos.toShortString(), describeStep());
			return goTo(Step.PROCESS);
		}
		ItemStack remaining = CreateMachineIO.insert(ctx.getWorld(), machinePos, pendingInsert);
		if (remaining.isEmpty()) {
			pendingInsert = ItemStack.EMPTY;
			LOGGER.info("CreateRecipeJob {} [{}] INSERT ok at {}", recipeId, kind, machinePos.toShortString());
			return goTo(Step.PROCESS);
		}
		pendingInsert = remaining;
		if (ticksInStep >= IO_TIMEOUT_TICKS) {
			fail("insert: could not insert items into " + machinePos.toShortString());
			return true;
		}
		return false;
	}

	private boolean advanceProcess(CreateWorldContext ctx) {
		if (machinePos == null || ctx == null || !ctx.isValid()) {
			fail("process: missing machine pos or world context");
			return true;
		}
		if (!CreateMachineIO.verifyPresent(ctx.getWorld(), machinePos)) {
			fail("process: machine disappeared at " + machinePos.toShortString());
			return true;
		}
		if (ticksInStep >= PROCESS_STEP_TICKS) {
			LOGGER.info("CreateRecipeJob {} [{}] PROCESS done — {}", recipeId, kind, describeStep());
			return goTo(Step.EXTRACT);
		}
		return false;
	}

	private boolean advanceExtract(CreateWorldContext ctx) {
		if (machinePos == null || ctx == null || !ctx.isValid()) {
			fail("extract: missing machine pos or world context");
			return true;
		}
		if (!CreateMachineIO.hasBlockEntity(ctx.getWorld(), machinePos)) {
			fail("extract: block entity missing at " + machinePos.toShortString());
			return true;
		}
		ItemStack out = CreateMachineIO.extract(ctx.getWorld(), machinePos, ItemStack.EMPTY, 64);
		if (!out.isEmpty()) {
			lastExtracted = out;
			LOGGER.info("CreateRecipeJob {} [{}] EXTRACT got {} x{}", recipeId, kind, out.getItem(), out.getCount());
			success = true;
			failed = false;
			return goTo(Step.DONE);
		}
		// Soft success when inventory empty but machine present (typed BE extract TODO).
		if (ticksInStep >= IO_TIMEOUT_TICKS) {
			LOGGER.info("CreateRecipeJob {} [{}] EXTRACT timeout — completing soft (typed BE TODO)", recipeId, kind);
			success = true;
			failed = false;
			return goTo(Step.DONE);
		}
		return false;
	}

	private boolean goTo(Step next) {
		step = next;
		ticksInStep = 0;
		if (next == Step.DONE && !failed) {
			success = true;
			LOGGER.info("CreateRecipeJob {} [{}] completed", recipeId, kind);
		}
		return true;
	}

	public String describeStep() {
		switch (kind) {
			case SEQUENCED_ASSEMBLY: return note("depot/belt", "insert depot", "deployer/press/saw/spout", "extract assembly");
			case MECHANICAL_CRAFTING: return note("mechanical crafter", "fill pattern", "wait craft", "extract craft");
			case FILLING: return note("spout/basin", "insert container", "spout fill", "extract filled");
			case BASIN: return note("basin", "insert basin inputs", "mix/press basin", "extract basin");
			case COMPOUND_SMELT: return note("furnace", "insert compound+fuel", "smelt", "extract smelted");
			case BRONZE_SMITH: return note("smithing table", "insert Cu+Sn", "smith bronze", "extract bronze");
			case PRESS_DUST: return note("mechanical press", "insert cobble/dust", "press", "extract dust/product");
			case MIXER_BASIN: return note("mixer+basin", "insert mix inputs", "run mixer", "extract mixed");
			case GROUT: return note("mixer (grout)", "insert grout inputs", "mix grout", "extract grout");
			default: return step.name();
		}
	}

	private String note(String locate, String insert, String process, String extract) {
		switch (step) {
			case LOCATE_MACHINE: return "locate " + locate + (machinePos != null ? " @ " + machinePos.toShortString() : "");
			case INSERT: return insert;
			case PROCESS: return process;
			case EXTRACT: return extract;
			default: return "done";
		}
	}

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
				+ ", machine=" + (machinePos != null ? machinePos.toShortString() : "none")
				+ ", ticks=" + tickCount + ", success=" + success + ", failed=" + failed + '}';
	}
}
