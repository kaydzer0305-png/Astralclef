package com.ezquest.astralclef.tasks.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ezquest.astralclef.tasks.create.world.CreateBlockEntityIO;
import com.ezquest.astralclef.tasks.create.world.CreateMachineIO;
import com.ezquest.astralclef.tasks.create.world.CreateMachineLocator;
import com.ezquest.astralclef.tasks.create.world.CreateMachineType;
import com.ezquest.astralclef.tasks.create.world.CreateWorldContext;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-flight Create / Astral recipe job.
 * Pipeline: LOCATE_MACHINE → INSERT → PROCESS → EXTRACT → DONE.
 * Uses {@link CreateMachineLocator} / {@link CreateMachineIO} when a world context is set.
 * Binding inputs (from {@code astralclef:bind/*}) may span multiple INSERT ticks.
 * <p>
 * PROCESS waits for kinetic start then completion (or clear {@code failReason} timeout);
 * does not soft-complete blindly. Fluid outputs (e.g. {@code kubejs:compound_mixture})
 * use basin Transfer extract.
 */
public final class CreateRecipeJob {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-job");

	/** @deprecated Prefer start/complete timeouts; kept for status display. */
	@Deprecated
	public static final int PROCESS_STEP_TICKS = 40;
	/** Fail PROCESS if kinetic never starts within this many ticks. */
	public static final int PROCESS_START_TIMEOUT_TICKS = 120;
	/** Fail PROCESS if started but never completes within this many ticks after start. */
	public static final int PROCESS_COMPLETE_TIMEOUT_TICKS = 200;
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
	private ItemStack expectedOutput = ItemStack.EMPTY;
	private ItemStack lastExtracted = ItemStack.EMPTY;
	private Identifier expectedFluidId;
	private long expectedFluidAmount = CreateBlockEntityIO.DROPLETS_PER_INGOT;
	private long lastExtractedFluid;
	private final List<ItemStack> bindingInputs = new ArrayList<>();
	private int bindingInputIndex;
	private boolean sawProcessingStart;
	private int ticksSinceProcessingStart;

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
	public ItemStack getPendingInsert() { return pendingInsert; }
	public ItemStack getExpectedOutput() { return expectedOutput; }

	public void setPendingInsert(ItemStack stack) {
		this.pendingInsert = stack == null ? ItemStack.EMPTY : stack;
	}

	public void setExpectedOutput(ItemStack stack) {
		this.expectedOutput = stack == null ? ItemStack.EMPTY : stack;
	}

	public Identifier getExpectedFluidId() { return expectedFluidId; }
	public long getExpectedFluidAmount() { return expectedFluidAmount; }
	public long getLastExtractedFluid() { return lastExtractedFluid; }
	public boolean expectsFluidOutput() { return expectedFluidId != null; }

	public void setExpectedFluid(Identifier fluidId, long amountDroplets) {
		this.expectedFluidId = fluidId;
		this.expectedFluidAmount = Math.max(1L, amountDroplets);
	}

	public void setExpectedFluid(String fluidId, long amountDroplets) {
		if (fluidId == null || fluidId.isEmpty()) {
			this.expectedFluidId = null;
			return;
		}
		setExpectedFluid(Identifier.tryParse(fluidId), amountDroplets);
	}

	public void setBindingInputs(List<ItemStack> inputs) {
		bindingInputs.clear();
		bindingInputIndex = 0;
		if (inputs == null) {
			return;
		}
		for (ItemStack s : inputs) {
			if (s != null && !s.isEmpty()) {
				bindingInputs.add(s.copy());
			}
		}
		if (!bindingInputs.isEmpty() && pendingInsert.isEmpty()) {
			pendingInsert = bindingInputs.get(0).copy();
			bindingInputIndex = 0;
		}
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
		if (pendingInsert.isEmpty() && bindingInputIndex < bindingInputs.size()) {
			pendingInsert = bindingInputs.get(bindingInputIndex).copy();
		}
		if (pendingInsert.isEmpty() && bindingInputs.isEmpty()) {
			LOGGER.info("CreateRecipeJob {} [{}] INSERT verify-only at {} — {}",
					recipeId, kind, machinePos.toShortString(), describeStep());
			return goTo(Step.PROCESS);
		}
		if (pendingInsert.isEmpty()) {
			return goTo(Step.PROCESS);
		}
		ItemStack remaining = CreateMachineIO.insert(ctx.getWorld(), machinePos, pendingInsert);
		if (remaining.isEmpty()) {
			LOGGER.info("CreateRecipeJob {} [{}] INSERT ok item {} at {}",
					recipeId, kind, pendingInsert.getItem(), machinePos.toShortString());
			pendingInsert = ItemStack.EMPTY;
			bindingInputIndex++;
			if (bindingInputIndex < bindingInputs.size()) {
				pendingInsert = bindingInputs.get(bindingInputIndex).copy();
				ticksInStep = 0;
				return false;
			}
			return goTo(Step.PROCESS);
		}
		pendingInsert = remaining;
		if (ticksInStep >= IO_TIMEOUT_TICKS) {
			fail("insert: could not insert items into " + machinePos.toShortString());
			return true;
		}
		return false;
	}

	/** True for Create kinetic / basin operators — harden wait; furnace/smith use timed dwell. */
	private boolean usesKineticProcess() {
		switch (kind) {
			case BASIN:
			case PRESS_DUST:
			case MIXER_BASIN:
			case GROUT:
			case FILLING:
			case SEQUENCED_ASSEMBLY:
				return true;
			default:
				return false;
		}
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

		// Non-kinetic (furnace / smith / crafting): timed dwell, then EXTRACT
		if (!usesKineticProcess()) {
			if (ticksInStep >= PROCESS_STEP_TICKS) {
				LOGGER.info("CreateRecipeJob {} [{}] PROCESS dwell done (non-kinetic) — {}",
						recipeId, kind, describeStep());
				return goTo(Step.EXTRACT);
			}
			return false;
		}

		CreateBlockEntityIO.KineticState kinetic = CreateMachineIO.readKinetic(ctx.getWorld(), machinePos);
		boolean running = CreateMachineIO.isLikelyProcessing(ctx.getWorld(), machinePos);
		boolean fluidReady = expectsFluidOutput()
				&& CreateMachineIO.hasBasinFluid(ctx.getWorld(), machinePos, expectedFluidId);

		if (running) {
			if (!sawProcessingStart) {
				sawProcessingStart = true;
				ticksSinceProcessingStart = 0;
				LOGGER.info("CreateRecipeJob {} [{}] PROCESS started {} — {}",
						recipeId, kind, kinetic, describeStep());
			}
			ticksSinceProcessingStart++;
		} else if (sawProcessingStart) {
			// running flipped false after applyBasinRecipe — done
			LOGGER.info("CreateRecipeJob {} [{}] PROCESS complete (running→false, kinetic={}) — {}",
					recipeId, kind, kinetic, describeStep());
			return goTo(Step.EXTRACT);
		}

		// Basin already holds fluid product (recipe applied / outputs landed)
		if (fluidReady) {
			LOGGER.info("CreateRecipeJob {} [{}] PROCESS complete (basin has {}) — {}",
					recipeId, kind, expectedFluidId, describeStep());
			return goTo(Step.EXTRACT);
		}

		if (!sawProcessingStart && ticksInStep >= PROCESS_START_TIMEOUT_TICKS) {
			fail("process: never started at " + machinePos.toShortString()
					+ " (speed=" + kinetic.speed + ", running=" + kinetic.running
					+ ", recipe=" + kinetic.hasCurrentRecipe + ", basin=" + kinetic.basinPresent
					+ ") — check RPM≥32 / ingredients / filter");
			return true;
		}
		if (sawProcessingStart && ticksSinceProcessingStart >= PROCESS_COMPLETE_TIMEOUT_TICKS) {
			fail("process: started but did not complete within " + PROCESS_COMPLETE_TIMEOUT_TICKS
					+ " ticks at " + machinePos.toShortString() + " kinetic=" + kinetic);
			return true;
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
		if (expectsFluidOutput()) {
			long got = CreateMachineIO.extractFluid(
					ctx.getWorld(), machinePos, expectedFluidId, expectedFluidAmount);
			if (got > 0) {
				lastExtractedFluid = got;
				LOGGER.info("CreateRecipeJob {} [{}] EXTRACT fluid {} x{} droplets",
						recipeId, kind, expectedFluidId, got);
				success = true;
				failed = false;
				return goTo(Step.DONE);
			}
			if (ticksInStep >= IO_TIMEOUT_TICKS) {
				fail("extract: no fluid " + expectedFluidId + " in basin at "
						+ machinePos.toShortString() + " after " + IO_TIMEOUT_TICKS + " ticks");
				return true;
			}
			return false;
		}
		ItemStack filter = expectedOutput.isEmpty() ? ItemStack.EMPTY : expectedOutput;
		int want = expectedOutput.isEmpty() ? 64 : Math.max(1, expectedOutput.getCount());
		ItemStack out = CreateMachineIO.extract(ctx.getWorld(), machinePos, filter, want);
		if (!out.isEmpty()) {
			lastExtracted = out;
			LOGGER.info("CreateRecipeJob {} [{}] EXTRACT got {} x{}", recipeId, kind, out.getItem(), out.getCount());
			success = true;
			failed = false;
			return goTo(Step.DONE);
		}
		if (ticksInStep >= IO_TIMEOUT_TICKS) {
			// Item extract soft-complete only when no concrete output was required
			if (expectedOutput.isEmpty()) {
				LOGGER.info("CreateRecipeJob {} [{}] EXTRACT timeout — soft-ok (no expected item)",
						recipeId, kind);
				success = true;
				failed = false;
				return goTo(Step.DONE);
			}
			fail("extract: expected " + expectedOutput.getItem() + " x" + expectedOutput.getCount()
					+ " not found at " + machinePos.toShortString());
			return true;
		}
		return false;
	}

	private boolean goTo(Step next) {
		step = next;
		ticksInStep = 0;
		if (next == Step.PROCESS) {
			sawProcessingStart = false;
			ticksSinceProcessingStart = 0;
		}
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
