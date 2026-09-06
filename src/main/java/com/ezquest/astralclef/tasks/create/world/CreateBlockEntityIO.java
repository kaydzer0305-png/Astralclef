package com.ezquest.astralclef.tasks.create.world;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Typed Create block-entity I/O for Fabric 1.18.2 ({@code create-fabric-1.18.2:0.5.1-f-build.1415+mc1.18.2}).
 * <p>
 * Prefers Create-specific inventories / behaviours when present:
 * <ul>
 *   <li><b>Basin / Mixing</b> — {@code BasinBlockEntity}/{@code BasinTileEntity}
 *       input/output {@code SmartInventory} slots; {@code FilteringBehaviour} when readable</li>
 *   <li><b>Depot</b> — {@code DepotBehaviour#getHeldItemStack} / set held via behaviour</li>
 *   <li><b>Mechanical crafter</b> — grid {@code getInventory()}</li>
 *   <li><b>Spout</b> — item path soft; fluid via Create fluid APIs when detectable
 *       ({@link #GAP_SPOUT_FLUID})</li>
 *   <li><b>Press / Mixer</b> — processing state ({@code running} / pressing behaviour) via
 *       {@link #isProcessing(BlockEntity)}</li>
 * </ul>
 * Uses soft class checks + reflection so Yarn/Mojmap remapping and package moves
 * ({@code content.processing.*} vs legacy {@code content.contraptions.processing.*})
 * do not break compile. Callers should fall back to Fabric Transfer / {@code Inventory}
 * when these methods return empty / unchanged.
 * <p>
 * <b>Documented gaps</b>
 * <ul>
 *   <li>{@value #GAP_SPOUT_FLUID} — spout fluid fill/drain not fully wired; Transfer fluid path preferred</li>
 *   <li>{@value #GAP_BASIN_FILTER_WRITE} — basin recipe filter readable when present; write not implemented</li>
 *   <li>{@value #GAP_CRAFTER_PATTERN} — crafter grid slot insert/extract only; pattern encoding TODO</li>
 * </ul>
 * <p>
 * <b>Hardened (Ch01)</b>: basin {@code inputTank}/{@code outputTank} + Fabric
 * {@code Storage&lt;FluidVariant&gt;} for {@code kubejs:compound_mixture}; mixer/press
 * kinetic via {@code running}/{@code runningTicks}/{@code processingTicks}/{@code currentRecipe}/
 * {@code getSpeed()}/{@code getBasin()}.
 */
public final class CreateBlockEntityIO {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-be-io");

	public static final String GAP_SPOUT_FLUID = "spout fluid I/O soft — use Fluid Transfer";
	public static final String GAP_BASIN_FILTER_WRITE = "basin FilteringBehaviour write not implemented";
	public static final String GAP_CRAFTER_PATTERN = "mechanical crafter pattern encode TODO";
	/** @deprecated kinetic PROCESS hardened via {@link #readKineticState} / {@link #isLikelyProcessing} */
	@Deprecated
	public static final String GAP_KINETIC_PROCESS = "press/mixer process detection best-effort only";

	public static final String COMPOUND_MIXTURE_FLUID = "kubejs:compound_mixture";
	/** Create Fabric fluid unit: 1 bucket = 81000 droplets (Fabric Transfer). */
	public static final long DROPLETS_PER_BUCKET = 81000L;
	public static final long DROPLETS_PER_INGOT = 9000L; // Create Astral INGOT ≈ 90 mB → 9000 droplets

	private static final String[] BASIN_CLASSES = {
			"com.simibubi.create.content.processing.basin.BasinBlockEntity",
			"com.simibubi.create.content.contraptions.processing.BasinTileEntity",
			"com.simibubi.create.content.contraptions.processing.BasinBlockEntity"
	};
	private static final String[] DEPOT_BE_CLASSES = {
			"com.simibubi.create.content.logistics.depot.DepotBlockEntity",
			"com.simibubi.create.content.logistics.block.depot.DepotTileEntity",
			"com.simibubi.create.content.contraptions.components.depot.DepotTileEntity"
	};
	private static final String[] DEPOT_BEHAVIOUR_CLASSES = {
			"com.simibubi.create.content.logistics.depot.DepotBehaviour",
			"com.simibubi.create.content.logistics.block.depot.DepotBehaviour",
			"com.simibubi.create.content.contraptions.relays.belt.transport.DepotBehaviour"
	};
	private static final String[] CRAFTER_CLASSES = {
			"com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity",
			"com.simibubi.create.content.contraptions.components.crafter.MechanicalCrafterTileEntity"
	};
	private static final String[] SPOUT_CLASSES = {
			"com.simibubi.create.content.fluids.spout.SpoutBlockEntity",
			"com.simibubi.create.content.contraptions.fluids.actors.SpoutTileEntity"
	};
	private static final String[] PRESS_CLASSES = {
			"com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity",
			"com.simibubi.create.content.contraptions.components.press.MechanicalPressTileEntity"
	};
	private static final String[] MIXER_CLASSES = {
			"com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity",
			"com.simibubi.create.content.contraptions.components.mixer.MechanicalMixerTileEntity"
	};
	private static final String[] BEHAVIOUR_HELPER_CLASSES = {
			"com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour",
			"com.simibubi.create.foundation.tileEntity.behaviour.BlockEntityBehaviour",
			"com.simibubi.create.foundation.tileEntity.TileEntityBehaviour"
	};

	private CreateBlockEntityIO() {}

	/**
	 * Try Create-typed insert. Returns empty when fully inserted, a remaining stack when
	 * partially handled, or {@link Optional#empty()} when no typed path applied (caller falls back).
	 */
	public static Optional<ItemStack> tryInsert(ServerWorld world, BlockPos pos, ItemStack stack) {
		if (world == null || pos == null || stack == null || stack.isEmpty()) {
			return Optional.empty();
		}
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null) {
			return Optional.empty();
		}
		Optional<ItemStack> basin = insertBasin(be, stack);
		if (basin.isPresent()) {
			return basin;
		}
		Optional<ItemStack> depot = insertDepot(be, stack);
		if (depot.isPresent()) {
			return depot;
		}
		Optional<ItemStack> crafter = insertCrafter(be, stack);
		if (crafter.isPresent()) {
			return crafter;
		}
		// Spout: item insert rarely applicable; fluid gap documented
		if (isInstanceOfAny(be, SPOUT_CLASSES)) {
			LOGGER.debug("tryInsert spout at {}: {}", pos.toShortString(), GAP_SPOUT_FLUID);
			return Optional.empty();
		}
		return Optional.empty();
	}

	/**
	 * Try Create-typed extract. Returns empty Optional when typed path unavailable;
	 * returns present (possibly EMPTY stack) when typed path ran.
	 */
	public static Optional<ItemStack> tryExtract(ServerWorld world, BlockPos pos, ItemStack filter, int maxCount) {
		if (world == null || pos == null || maxCount <= 0) {
			return Optional.empty();
		}
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null) {
			return Optional.empty();
		}
		Optional<ItemStack> basin = extractBasin(be, filter, maxCount);
		if (basin.isPresent()) {
			return basin;
		}
		Optional<ItemStack> depot = extractDepot(be, filter, maxCount);
		if (depot.isPresent()) {
			return depot;
		}
		Optional<ItemStack> crafter = extractCrafter(be, filter, maxCount);
		if (crafter.isPresent()) {
			return crafter;
		}
		if (isInstanceOfAny(be, SPOUT_CLASSES)) {
			LOGGER.debug("tryExtract spout at {}: {}", pos.toShortString(), GAP_SPOUT_FLUID);
			return Optional.empty();
		}
		return Optional.empty();
	}

	/**
	 * Kinetic snapshot for BasinOperatingBlockEntity subclasses (mixer / press).
	 */
	public static final class KineticState {
		public final boolean known;
		public final boolean running;
		public final int runningTicks;
		public final int processingTicks;
		public final float speed;
		public final boolean hasCurrentRecipe;
		public final boolean basinPresent;

		public KineticState(boolean known, boolean running, int runningTicks, int processingTicks,
				float speed, boolean hasCurrentRecipe, boolean basinPresent) {
			this.known = known;
			this.running = running;
			this.runningTicks = runningTicks;
			this.processingTicks = processingTicks;
			this.speed = speed;
			this.hasCurrentRecipe = hasCurrentRecipe;
			this.basinPresent = basinPresent;
		}

		/** True when the operator is actively applying a basin recipe. */
		public boolean isLikelyProcessing() {
			if (!known) {
				return false;
			}
			if (running) {
				return true;
			}
			if (Math.abs(speed) > 0.001f && (processingTicks > 0 || hasCurrentRecipe || runningTicks > 0)) {
				return true;
			}
			return false;
		}

		@Override
		public String toString() {
			return "KineticState{known=" + known + ", running=" + running
					+ ", runningTicks=" + runningTicks + ", processingTicks=" + processingTicks
					+ ", speed=" + speed + ", recipe=" + hasCurrentRecipe
					+ ", basin=" + basinPresent + "}";
		}
	}

	/**
	 * Read mixer/press BasinOperating fields:
	 * {@code running}, {@code runningTicks}, {@code processingTicks}, {@code currentRecipe},
	 * {@code getSpeed()}, {@code getBasin()}.
	 */
	public static KineticState readKineticState(BlockEntity be) {
		if (be == null) {
			return new KineticState(false, false, 0, 0, 0f, false, false);
		}
		boolean mixerOrPress = isInstanceOfAny(be, MIXER_CLASSES) || isInstanceOfAny(be, PRESS_CLASSES)
				|| isBasinOperating(be);
		if (!mixerOrPress) {
			return new KineticState(false, false, 0, 0, 0f, false, false);
		}
		boolean running = false;
		Object runningObj = readField(be, "running");
		if (runningObj instanceof Boolean b) {
			running = b;
		} else {
			Object behaviour = invokeNoArg(be, "getPressingBehaviour");
			if (behaviour != null) {
				Object br = readField(behaviour, "running");
				if (br instanceof Boolean bb) {
					running = bb;
				}
			}
		}
		int runningTicks = asInt(readField(be, "runningTicks"), 0);
		int processingTicks = asInt(readField(be, "processingTicks"), 0);
		float speed = 0f;
		Object speedObj = invokeNoArg(be, "getSpeed");
		if (speedObj instanceof Number n) {
			speed = n.floatValue();
		} else {
			Object sf = readField(be, "speed");
			if (sf instanceof Number n) {
				speed = n.floatValue();
			}
		}
		Object currentRecipe = readField(be, "currentRecipe");
		if (currentRecipe == null) {
			currentRecipe = invokeNoArg(be, "getCurrentRecipe");
		}
		boolean hasRecipe = currentRecipe != null;
		Object basin = invokeNoArg(be, "getBasin");
		if (basin instanceof Optional<?> opt) {
			basin = opt.orElse(null);
		}
		boolean basinPresent = basin != null;
		KineticState state = new KineticState(true, running, runningTicks, processingTicks, speed, hasRecipe, basinPresent);
		LOGGER.debug("readKineticState {}: {}", be.getClass().getSimpleName(), state);
		return state;
	}

	/**
	 * Best-effort Create processing detection for press / mixer (BasinOperating).
	 * @return empty if not a known processing BE; true/false when detectable
	 */
	public static Optional<Boolean> isProcessing(BlockEntity be) {
		KineticState state = readKineticState(be);
		if (!state.known) {
			return Optional.empty();
		}
		return Optional.of(state.isLikelyProcessing());
	}

	/** Convenience: mixer/press likely mid-recipe (running / ticks / recipe + speed). */
	public static boolean isLikelyProcessing(BlockEntity be) {
		return readKineticState(be).isLikelyProcessing();
	}

	private static boolean isBasinOperating(BlockEntity be) {
		if (be == null) {
			return false;
		}
		String[] names = {
				"com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity",
				"com.simibubi.create.content.contraptions.processing.BasinOperatingTileEntity",
				"com.simibubi.create.content.contraptions.components.actors.BasinOperatingTileEntity"
		};
		return isInstanceOfAny(be, names);
	}

	// --- Basin fluids (compound_mixture) ---

	/**
	 * Insert fluid into basin via Fabric Transfer {@code fluidCapability} / sided FluidStorage,
	 * falling back to reflective {@code inputTank} SmartFluidTankBehaviour.
	 * @return empty if no fluid path; otherwise remaining amount (0 = fully inserted)
	 */
	public static Optional<Long> tryInsertFluid(ServerWorld world, BlockPos pos, Identifier fluidId, long amount) {
		if (world == null || pos == null || fluidId == null || amount <= 0) {
			return Optional.empty();
		}
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null || !isInstanceOfAny(be, BASIN_CLASSES)) {
			return Optional.empty();
		}
		long viaTransfer = insertFluidTransfer(world, pos, fluidId, amount);
		if (viaTransfer >= 0) {
			long remaining = amount - viaTransfer;
			LOGGER.info("tryInsertFluid Transfer {} x{} at {} inserted={} remaining={}",
					fluidId, amount, pos.toShortString(), viaTransfer, remaining);
			return Optional.of(Math.max(0L, remaining));
		}
		long viaTank = insertFluidTankBehaviour(be, fluidId, amount);
		if (viaTank >= 0) {
			long remaining = amount - viaTank;
			invokeNoArg(be, "notifyChangeOfContents");
			LOGGER.info("tryInsertFluid tank {} x{} at {} inserted={} remaining={}",
					fluidId, amount, pos.toShortString(), viaTank, remaining);
			return Optional.of(Math.max(0L, remaining));
		}
		return Optional.empty();
	}

	/**
	 * Extract fluid from basin output (prefer {@code outputTank}) then Transfer API.
	 * @return empty if no fluid path; otherwise amount extracted (may be 0)
	 */
	public static Optional<Long> tryExtractFluid(ServerWorld world, BlockPos pos, Identifier fluidId, long maxAmount) {
		if (world == null || pos == null || fluidId == null || maxAmount <= 0) {
			return Optional.empty();
		}
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null || !isInstanceOfAny(be, BASIN_CLASSES)) {
			return Optional.empty();
		}
		long viaTank = extractFluidTankBehaviour(be, fluidId, maxAmount);
		if (viaTank > 0) {
			invokeNoArg(be, "notifyChangeOfContents");
			LOGGER.info("tryExtractFluid tank {} extracted {} at {}", fluidId, viaTank, pos.toShortString());
			return Optional.of(viaTank);
		}
		long viaTransfer = extractFluidTransfer(world, pos, fluidId, maxAmount);
		if (viaTransfer >= 0) {
			LOGGER.info("tryExtractFluid Transfer {} extracted {} at {}", fluidId, viaTransfer, pos.toShortString());
			return Optional.of(viaTransfer);
		}
		if (viaTank == 0) {
			return Optional.of(0L);
		}
		return Optional.empty();
	}

	/** Amount of {@code fluidId} currently in basin tanks (input+output), or empty if undetectable. */
	public static Optional<Long> getBasinFluidAmount(ServerWorld world, BlockPos pos, Identifier fluidId) {
		if (world == null || pos == null || fluidId == null) {
			return Optional.empty();
		}
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null || !isInstanceOfAny(be, BASIN_CLASSES)) {
			return Optional.empty();
		}
		long total = 0;
		boolean any = false;
		long t = amountInTankBehaviour(readField(be, "outputTank"), fluidId);
		if (t >= 0) {
			total += t;
			any = true;
		}
		t = amountInTankBehaviour(readField(be, "inputTank"), fluidId);
		if (t >= 0) {
			total += t;
			any = true;
		}
		if (!any) {
			long viaCap = amountInFluidCapability(be, fluidId);
			if (viaCap >= 0) {
				return Optional.of(viaCap);
			}
			long viaWorld = amountInFluidStorage(world, pos, fluidId);
			if (viaWorld >= 0) {
				return Optional.of(viaWorld);
			}
			return Optional.empty();
		}
		return Optional.of(total);
	}

	public static boolean basinHasFluid(ServerWorld world, BlockPos pos, Identifier fluidId) {
		return getBasinFluidAmount(world, pos, fluidId).orElse(0L) > 0L;
	}

	private static long insertFluidTransfer(ServerWorld world, BlockPos pos, Identifier fluidId, long amount) {
		try {
			FluidVariant variant = fluidVariantOf(fluidId);
			if (variant == null || variant.isBlank()) {
				return -1;
			}
			Storage<FluidVariant> storage = FluidStorage.SIDED.find(world, pos, Direction.UP);
			if (storage == null) {
				storage = FluidStorage.SIDED.find(world, pos, null);
			}
			if (storage == null) {
				BlockEntity be = world.getBlockEntity(pos);
				storage = fluidCapabilityOf(be);
			}
			if (storage == null || !storage.supportsInsertion()) {
				return -1;
			}
			try (Transaction tx = Transaction.openOuter()) {
				long inserted = storage.insert(variant, amount, tx);
				tx.commit();
				return inserted;
			}
		} catch (Throwable t) {
			LOGGER.debug("insertFluidTransfer: {}", t.toString());
			return -1;
		}
	}

	private static long extractFluidTransfer(ServerWorld world, BlockPos pos, Identifier fluidId, long maxAmount) {
		try {
			FluidVariant want = fluidVariantOf(fluidId);
			if (want == null || want.isBlank()) {
				return -1;
			}
			Storage<FluidVariant> storage = FluidStorage.SIDED.find(world, pos, Direction.DOWN);
			if (storage == null) {
				storage = FluidStorage.SIDED.find(world, pos, null);
			}
			if (storage == null) {
				storage = fluidCapabilityOf(world.getBlockEntity(pos));
			}
			if (storage == null || !storage.supportsExtraction()) {
				return -1;
			}
			try (Transaction tx = Transaction.openOuter()) {
				long extracted = 0;
				for (StorageView<FluidVariant> view : storage) {
					if (view.isResourceBlank()) {
						continue;
					}
					FluidVariant resource = view.getResource();
					if (!resource.equals(want) && !sameFluidId(resource, fluidId)) {
						continue;
					}
					extracted = storage.extract(resource, maxAmount, tx);
					if (extracted > 0) {
						tx.commit();
						return extracted;
					}
				}
			}
			return 0;
		} catch (Throwable t) {
			LOGGER.debug("extractFluidTransfer: {}", t.toString());
			return -1;
		}
	}

	@SuppressWarnings("unchecked")
	private static Storage<FluidVariant> fluidCapabilityOf(BlockEntity be) {
		if (be == null) {
			return null;
		}
		Object cap = readField(be, "fluidCapability");
		if (cap instanceof Storage) {
			return (Storage<FluidVariant>) cap;
		}
		Object got = invokeNoArg(be, "getFluidCapability");
		if (got instanceof Storage) {
			return (Storage<FluidVariant>) got;
		}
		return null;
	}

	private static long amountInFluidCapability(BlockEntity be, Identifier fluidId) {
		Storage<FluidVariant> storage = fluidCapabilityOf(be);
		if (storage == null) {
			return -1;
		}
		return sumStorage(storage, fluidId);
	}

	private static long amountInFluidStorage(ServerWorld world, BlockPos pos, Identifier fluidId) {
		try {
			Storage<FluidVariant> storage = FluidStorage.SIDED.find(world, pos, null);
			if (storage == null) {
				return -1;
			}
			return sumStorage(storage, fluidId);
		} catch (Throwable t) {
			return -1;
		}
	}

	private static long sumStorage(Storage<FluidVariant> storage, Identifier fluidId) {
		long total = 0;
		try {
			for (StorageView<FluidVariant> view : storage) {
				if (view.isResourceBlank()) {
					continue;
				}
				if (sameFluidId(view.getResource(), fluidId)) {
					total += view.getAmount();
				}
			}
			return total;
		} catch (Throwable t) {
			return -1;
		}
	}

	private static long insertFluidTankBehaviour(BlockEntity be, Identifier fluidId, long amount) {
		Object tank = readField(be, "inputTank");
		if (tank == null) {
			tank = invokeNoArg(be, "getInputTank");
		}
		long n = fillSmartFluidTank(tank, fluidId, amount);
		return n;
	}

	private static long extractFluidTankBehaviour(BlockEntity be, Identifier fluidId, long maxAmount) {
		Object out = readField(be, "outputTank");
		if (out == null) {
			out = invokeNoArg(be, "getOutputTank");
		}
		long n = drainSmartFluidTank(out, fluidId, maxAmount);
		if (n > 0) {
			return n;
		}
		Object in = readField(be, "inputTank");
		if (in == null) {
			in = invokeNoArg(be, "getInputTank");
		}
		return drainSmartFluidTank(in, fluidId, maxAmount);
	}

	private static long amountInTankBehaviour(Object tankBehaviour, Identifier fluidId) {
		if (tankBehaviour == null) {
			return -1;
		}
		Object primary = invokeNoArg(tankBehaviour, "getPrimaryHandler");
		if (primary == null) {
			primary = readField(tankBehaviour, "tank");
		}
		if (primary == null) {
			return -1;
		}
		Object fluidStack = invokeNoArg(primary, "getFluid");
		if (fluidStack == null) {
			fluidStack = readField(primary, "fluid");
		}
		if (fluidStack == null) {
			return 0;
		}
		if (!fluidStackMatches(fluidStack, fluidId)) {
			return 0;
		}
		Object amt = invokeNoArg(fluidStack, "getAmount");
		if (amt instanceof Number n) {
			return n.longValue();
		}
		amt = readField(fluidStack, "amount");
		return amt instanceof Number n ? n.longValue() : 0;
	}

	private static long fillSmartFluidTank(Object tankBehaviour, Identifier fluidId, long amount) {
		if (tankBehaviour == null) {
			return -1;
		}
		try {
			Object primary = invokeNoArg(tankBehaviour, "getPrimaryHandler");
			if (primary == null) {
				return -1;
			}
			Object fluidStack = newFluidStack(fluidId, amount);
			if (fluidStack == null) {
				return -1;
			}
			// FluidTank#fill(FluidStack, boolean) — Create/Forge-style; Fabric port may use long fill
			for (Method m : primary.getClass().getMethods()) {
				if (!"fill".equals(m.getName()) || m.getParameterCount() < 1) {
					continue;
				}
				m.setAccessible(true);
				Class<?>[] p = m.getParameterTypes();
				Object result;
				if (p.length == 2 && (p[1] == boolean.class || p[1] == Boolean.class)) {
					// boolean simulate: false = execute
					result = m.invoke(primary, fluidStack, false);
				} else if (p.length == 2 && p[1].isEnum()) {
					// FluidAction.EXECUTE
					Object execute = null;
					for (Object c : p[1].getEnumConstants()) {
						if ("EXECUTE".equals(String.valueOf(c)) || "EXECUTE".equals(((Enum<?>) c).name())) {
							execute = c;
							break;
						}
					}
					if (execute == null) {
						continue;
					}
					result = m.invoke(primary, fluidStack, execute);
				} else if (p.length == 1) {
					result = m.invoke(primary, fluidStack);
				} else {
					continue;
				}
				if (result instanceof Number n) {
					return n.longValue();
				}
			}
			// Fabric Transfer path on behaviour
			Object capability = readField(tankBehaviour, "capability");
			if (capability instanceof Storage) {
				@SuppressWarnings("unchecked")
				Storage<FluidVariant> storage = (Storage<FluidVariant>) capability;
				FluidVariant variant = fluidVariantOf(fluidId);
				if (variant == null) {
					return -1;
				}
				try (Transaction tx = Transaction.openOuter()) {
					long inserted = storage.insert(variant, amount, tx);
					tx.commit();
					return inserted;
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("fillSmartFluidTank: {}", t.toString());
		}
		return -1;
	}

	private static long drainSmartFluidTank(Object tankBehaviour, Identifier fluidId, long maxAmount) {
		if (tankBehaviour == null) {
			return -1;
		}
		try {
			Object primary = invokeNoArg(tankBehaviour, "getPrimaryHandler");
			if (primary == null) {
				return -1;
			}
			if (amountInTankBehaviour(tankBehaviour, fluidId) <= 0) {
				return 0;
			}
			for (Method m : primary.getClass().getMethods()) {
				if (!"drain".equals(m.getName())) {
					continue;
				}
				m.setAccessible(true);
				Class<?>[] p = m.getParameterTypes();
				Object result = null;
				if (p.length == 2 && p[0] == int.class && (p[1] == boolean.class || p[1] == Boolean.class)) {
					result = m.invoke(primary, (int) Math.min(maxAmount, Integer.MAX_VALUE), false);
				} else if (p.length == 2 && p[0] == long.class && (p[1] == boolean.class || p[1] == Boolean.class)) {
					result = m.invoke(primary, maxAmount, false);
				} else if (p.length == 2 && p[1].isEnum()) {
					Object execute = null;
					for (Object c : p[1].getEnumConstants()) {
						if ("EXECUTE".equals(((Enum<?>) c).name())) {
							execute = c;
							break;
						}
					}
					if (execute == null) {
						continue;
					}
					Object amtArg = p[0] == long.class ? Long.valueOf(maxAmount)
							: Integer.valueOf((int) Math.min(maxAmount, Integer.MAX_VALUE));
					result = m.invoke(primary, amtArg, execute);
				} else if (p.length == 1 && (p[0] == int.class || p[0] == long.class)) {
					Object amtArg = p[0] == long.class ? Long.valueOf(maxAmount)
							: Integer.valueOf((int) Math.min(maxAmount, Integer.MAX_VALUE));
					result = m.invoke(primary, amtArg);
				}
				if (result != null) {
					Object amt = invokeNoArg(result, "getAmount");
					if (amt instanceof Number n) {
						return n.longValue();
					}
					if (result instanceof Number n) {
						return n.longValue();
					}
				}
			}
			Object capability = readField(tankBehaviour, "capability");
			if (capability instanceof Storage) {
				@SuppressWarnings("unchecked")
				Storage<FluidVariant> storage = (Storage<FluidVariant>) capability;
				FluidVariant variant = fluidVariantOf(fluidId);
				if (variant == null) {
					return -1;
				}
				try (Transaction tx = Transaction.openOuter()) {
					long extracted = storage.extract(variant, maxAmount, tx);
					tx.commit();
					return extracted;
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("drainSmartFluidTank: {}", t.toString());
		}
		return -1;
	}

	private static Object newFluidStack(Identifier fluidId, long amount) {
		Fluid fluid = Registry.FLUID.get(fluidId);
		if (fluid == null || Registry.FLUID.getId(fluid).equals(Registry.FLUID.getDefaultId())
				&& !fluidId.equals(Registry.FLUID.getDefaultId())) {
			// missing fluid registry entry
			Identifier got = Registry.FLUID.getId(fluid);
			if (!fluidId.equals(got)) {
				LOGGER.debug("newFluidStack: fluid not registered {}", fluidId);
				return null;
			}
		}
		String[] stackClasses = {
				"net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant", // not a stack
				"io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidStack",
				"io.github.fabricators_of_create.porting_lib.fluids.FluidStack",
				"net.minecraftforge.fluids.FluidStack"
		};
		for (String name : stackClasses) {
			try {
				Class<?> c = Class.forName(name);
				try {
					return c.getConstructor(Fluid.class, int.class).newInstance(fluid, (int) Math.min(amount, Integer.MAX_VALUE));
				} catch (NoSuchMethodException e) {
					return c.getConstructor(Fluid.class, long.class).newInstance(fluid, amount);
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static FluidVariant fluidVariantOf(Identifier fluidId) {
		try {
			Fluid fluid = Registry.FLUID.get(fluidId);
			Identifier got = Registry.FLUID.getId(fluid);
			if (!fluidId.equals(got)) {
				return null;
			}
			return FluidVariant.of(fluid);
		} catch (Throwable t) {
			return null;
		}
	}

	private static boolean sameFluidId(FluidVariant variant, Identifier fluidId) {
		if (variant == null || variant.isBlank() || fluidId == null) {
			return false;
		}
		return fluidId.equals(Registry.FLUID.getId(variant.getFluid()));
	}

	private static boolean fluidStackMatches(Object fluidStack, Identifier fluidId) {
		if (fluidStack == null || fluidId == null) {
			return false;
		}
		Object fluid = invokeNoArg(fluidStack, "getFluid");
		if (fluid instanceof Fluid f) {
			return fluidId.equals(Registry.FLUID.getId(f));
		}
		Object variant = invokeNoArg(fluidStack, "getType");
		if (variant instanceof FluidVariant fv) {
			return sameFluidId(fv, fluidId);
		}
		return false;
	}

	private static int asInt(Object o, int def) {
		if (o instanceof Number n) {
			return n.intValue();
		}
		return def;
	}

	/** Readable basin filter stack when FilteringBehaviour is present; empty otherwise. */
	public static Optional<ItemStack> getBasinFilter(BlockEntity be) {
		if (!isInstanceOfAny(be, BASIN_CLASSES)) {
			return Optional.empty();
		}
		Object filtering = invokeNoArg(be, "getFilter");
		if (filtering == null) {
			filtering = readField(be, "filtering");
		}
		if (filtering == null) {
			return Optional.empty();
		}
		Object filter = invokeNoArg(filtering, "getFilter");
		if (filter instanceof ItemStack stack) {
			return Optional.of(stack);
		}
		LOGGER.debug("getBasinFilter: {}", GAP_BASIN_FILTER_WRITE);
		return Optional.empty();
	}

	// --- Basin ---

	private static Optional<ItemStack> insertBasin(BlockEntity be, ItemStack stack) {
		if (!isInstanceOfAny(be, BASIN_CLASSES)) {
			return Optional.empty();
		}
		Object inputInv = invokeNoArg(be, "getInputInventory");
		if (inputInv == null) {
			inputInv = readField(be, "inputInventory");
		}
		if (inputInv == null) {
			return Optional.empty();
		}
		ItemStack remaining = insertSmartInventory(inputInv, stack);
		invokeNoArg(be, "notifyChangeOfContents");
		LOGGER.info("insertBasin: remaining={}", remaining.getCount());
		return Optional.of(remaining);
	}

	private static Optional<ItemStack> extractBasin(BlockEntity be, ItemStack filter, int maxCount) {
		if (!isInstanceOfAny(be, BASIN_CLASSES)) {
			return Optional.empty();
		}
		Object outputInv = invokeNoArg(be, "getOutputInventory");
		if (outputInv == null) {
			outputInv = readField(be, "outputInventory");
		}
		ItemStack fromOut = outputInv != null ? extractSmartInventory(outputInv, filter, maxCount) : ItemStack.EMPTY;
		if (!fromOut.isEmpty()) {
			invokeNoArg(be, "notifyChangeOfContents");
			return Optional.of(fromOut);
		}
		Object inputInv = invokeNoArg(be, "getInputInventory");
		if (inputInv == null) {
			inputInv = readField(be, "inputInventory");
		}
		ItemStack fromIn = inputInv != null ? extractSmartInventory(inputInv, filter, maxCount) : ItemStack.EMPTY;
		if (!fromIn.isEmpty()) {
			invokeNoArg(be, "notifyChangeOfContents");
		}
		return Optional.of(fromIn);
	}

	// --- Depot ---

	private static Optional<ItemStack> insertDepot(BlockEntity be, ItemStack stack) {
		if (!isInstanceOfAny(be, DEPOT_BE_CLASSES) && getDepotBehaviour(be) == null) {
			return Optional.empty();
		}
		Object behaviour = getDepotBehaviour(be);
		if (behaviour == null) {
			return Optional.empty();
		}
		ItemStack held = asItemStack(invokeNoArg(behaviour, "getHeldItemStack"));
		if (held != null && !held.isEmpty()) {
			if (!ItemStack.canCombine(held, stack)) {
				return Optional.of(stack);
			}
			int space = held.getMaxCount() - held.getCount();
			if (space <= 0) {
				return Optional.of(stack);
			}
			int move = Math.min(space, stack.getCount());
			held.increment(move);
			ItemStack remaining = stack.copy();
			remaining.decrement(move);
			setDepotHeld(behaviour, held);
			return Optional.of(remaining);
		}
		ItemStack placed = stack.copy();
		setDepotHeld(behaviour, placed);
		LOGGER.info("insertDepot: placed {} x{}", placed.getItem(), placed.getCount());
		return Optional.of(ItemStack.EMPTY);
	}

	private static Optional<ItemStack> extractDepot(BlockEntity be, ItemStack filter, int maxCount) {
		Object behaviour = getDepotBehaviour(be);
		if (behaviour == null) {
			if (!isInstanceOfAny(be, DEPOT_BE_CLASSES)) {
				return Optional.empty();
			}
			return Optional.of(ItemStack.EMPTY);
		}
		ItemStack held = asItemStack(invokeNoArg(behaviour, "getHeldItemStack"));
		if (held == null || held.isEmpty()) {
			return Optional.of(ItemStack.EMPTY);
		}
		if (filter != null && !filter.isEmpty() && !ItemStack.areItemsEqual(filter, held)) {
			return Optional.of(ItemStack.EMPTY);
		}
		int take = Math.min(maxCount, held.getCount());
		ItemStack out = held.split(take);
		setDepotHeld(behaviour, held.isEmpty() ? ItemStack.EMPTY : held);
		LOGGER.info("extractDepot: got {} x{}", out.getItem(), out.getCount());
		return Optional.of(out);
	}

	private static Object getDepotBehaviour(BlockEntity be) {
		if (be == null) {
			return null;
		}
		// SmartBlockEntity#getBehaviour(TYPE)
		for (String behaviourClass : DEPOT_BEHAVIOUR_CLASSES) {
			try {
				Class<?> bhClass = Class.forName(behaviourClass);
				Field typeField = bhClass.getField("TYPE");
				Object type = typeField.get(null);
				Method getBehaviour = be.getClass().getMethod("getBehaviour", type.getClass());
				Object behaviour = getBehaviour.invoke(be, type);
				if (behaviour != null) {
					return behaviour;
				}
			} catch (ReflectiveOperationException ignored) {
				// try helper
			}
			for (String helper : BEHAVIOUR_HELPER_CLASSES) {
				try {
					Class<?> helperClass = Class.forName(helper);
					Class<?> bhClass = Class.forName(behaviourClass);
					Field typeField = bhClass.getField("TYPE");
					Object type = typeField.get(null);
					Method get = helperClass.getMethod("get", BlockEntity.class, type.getClass());
					Object behaviour = get.invoke(null, be, type);
					if (behaviour != null) {
						return behaviour;
					}
				} catch (ReflectiveOperationException ignored) {
					// next
				}
			}
		}
		return readField(be, "depotBehaviour");
	}

	private static void setDepotHeld(Object behaviour, ItemStack stack) {
		// Prefer setHeldItem(TransportedItemStack) when available; else mutate heldItem.stack / field
		try {
			for (Method m : behaviour.getClass().getMethods()) {
				if (!"setHeldItem".equals(m.getName()) && !"setCenteredHeldItem".equals(m.getName())) {
					continue;
				}
				Class<?>[] params = m.getParameterTypes();
				if (params.length != 1) {
					continue;
				}
				if (ItemStack.class.isAssignableFrom(params[0])) {
					m.invoke(behaviour, stack);
					return;
				}
				// TransportedItemStack wrapper
				Object transported = newTransported(params[0], stack);
				if (transported != null) {
					m.invoke(behaviour, transported);
					return;
				}
			}
		} catch (ReflectiveOperationException e) {
			LOGGER.debug("setDepotHeld setHeldItem failed: {}", e.toString());
		}
		Object held = readField(behaviour, "heldItem");
		if (held != null && stack != null) {
			try {
				Field stackField = held.getClass().getField("stack");
				stackField.set(held, stack);
				return;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		if (stack == null || stack.isEmpty()) {
			writeField(behaviour, "heldItem", null);
		}
	}

	private static Object newTransported(Class<?> transportedClass, ItemStack stack) {
		try {
			return transportedClass.getConstructor(ItemStack.class).newInstance(stack);
		} catch (ReflectiveOperationException e) {
			try {
				Object inst = transportedClass.getDeclaredConstructor().newInstance();
				Field stackField = transportedClass.getField("stack");
				stackField.set(inst, stack);
				return inst;
			} catch (ReflectiveOperationException e2) {
				return null;
			}
		}
	}

	// --- Mechanical crafter ---

	private static Optional<ItemStack> insertCrafter(BlockEntity be, ItemStack stack) {
		if (!isInstanceOfAny(be, CRAFTER_CLASSES)) {
			return Optional.empty();
		}
		Object inv = invokeNoArg(be, "getInventory");
		if (inv == null) {
			return Optional.empty();
		}
		ItemStack remaining = insertSmartInventory(inv, stack);
		LOGGER.debug("insertCrafter: {} — {}", remaining.getCount(), GAP_CRAFTER_PATTERN);
		return Optional.of(remaining);
	}

	private static Optional<ItemStack> extractCrafter(BlockEntity be, ItemStack filter, int maxCount) {
		if (!isInstanceOfAny(be, CRAFTER_CLASSES)) {
			return Optional.empty();
		}
		Object inv = invokeNoArg(be, "getInventory");
		if (inv == null) {
			return Optional.empty();
		}
		return Optional.of(extractSmartInventory(inv, filter, maxCount));
	}

	// --- SmartInventory / ItemStackHandler helpers ---

	private static ItemStack insertSmartInventory(Object inv, ItemStack stack) {
		ItemStack remaining = stack.copy();
		Integer slots = slotCount(inv);
		if (slots == null) {
			return remaining;
		}
		for (int i = 0; i < slots && !remaining.isEmpty(); i++) {
			ItemStack slot = getSlot(inv, i);
			if (slot == null) {
				continue;
			}
			if (slot.isEmpty()) {
				int put = Math.min(remaining.getCount(), remaining.getMaxCount());
				setSlot(inv, i, remaining.split(put));
			} else if (ItemStack.canCombine(slot, remaining)) {
				int space = slot.getMaxCount() - slot.getCount();
				if (space > 0) {
					int move = Math.min(space, remaining.getCount());
					slot.increment(move);
					remaining.decrement(move);
					setSlot(inv, i, slot);
				}
			}
		}
		invokeNoArg(inv, "markDirty");
		return remaining;
	}

	private static ItemStack extractSmartInventory(Object inv, ItemStack filter, int maxCount) {
		Integer slots = slotCount(inv);
		if (slots == null) {
			return ItemStack.EMPTY;
		}
		for (int i = 0; i < slots; i++) {
			ItemStack slot = getSlot(inv, i);
			if (slot == null || slot.isEmpty()) {
				continue;
			}
			if (filter != null && !filter.isEmpty() && !ItemStack.areItemsEqual(filter, slot)) {
				continue;
			}
			int take = Math.min(maxCount, slot.getCount());
			ItemStack out = slot.split(take);
			setSlot(inv, i, slot);
			invokeNoArg(inv, "markDirty");
			return out;
		}
		return ItemStack.EMPTY;
	}

	private static Integer slotCount(Object inv) {
		Object n = invokeNoArg(inv, "getSlots");
		if (n instanceof Integer i) {
			return i;
		}
		n = invokeNoArg(inv, "size");
		if (n instanceof Integer i) {
			return i;
		}
		return null;
	}

	private static ItemStack getSlot(Object inv, int index) {
		Object stack = invokeInt(inv, "getStackInSlot", index);
		if (stack instanceof ItemStack s) {
			return s;
		}
		stack = invokeInt(inv, "getStack", index);
		return stack instanceof ItemStack s ? s : null;
	}

	private static void setSlot(Object inv, int index, ItemStack stack) {
		if (invokeIntObj(inv, "setStackInSlot", index, stack)) {
			return;
		}
		invokeIntObj(inv, "setStack", index, stack);
	}

	// --- reflection utils ---

	private static boolean isInstanceOfAny(Object obj, String[] classNames) {
		if (obj == null) {
			return false;
		}
		Class<?> c = obj.getClass();
		for (String name : classNames) {
			try {
				if (Class.forName(name).isAssignableFrom(c)) {
					return true;
				}
			} catch (ClassNotFoundException ignored) {
			}
		}
		// Soft name match when remapped packages differ but simple name matches
		String simple = c.getSimpleName();
		for (String name : classNames) {
			int dot = name.lastIndexOf('.');
			String expect = dot >= 0 ? name.substring(dot + 1) : name;
			if (expect.equals(simple)) {
				return true;
			}
		}
		return false;
	}

	private static Object invokeNoArg(Object target, String name) {
		if (target == null) {
			return null;
		}
		try {
			Method m = findMethod(target.getClass(), name);
			if (m == null) {
				return null;
			}
			m.setAccessible(true);
			return m.invoke(target);
		} catch (ReflectiveOperationException e) {
			LOGGER.debug("invoke {}.{}: {}", target.getClass().getSimpleName(), name, e.toString());
			return null;
		}
	}

	private static Object invokeInt(Object target, String name, int arg) {
		if (target == null) {
			return null;
		}
		try {
			Method m = findMethod(target.getClass(), name, int.class);
			if (m == null) {
				return null;
			}
			m.setAccessible(true);
			return m.invoke(target, arg);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static boolean invokeIntObj(Object target, String name, int arg, Object obj) {
		if (target == null) {
			return false;
		}
		try {
			for (Method m : target.getClass().getMethods()) {
				if (!m.getName().equals(name) || m.getParameterCount() != 2) {
					continue;
				}
				Class<?>[] p = m.getParameterTypes();
				if (p[0] == int.class || p[0] == Integer.class) {
					m.setAccessible(true);
					m.invoke(target, arg, obj);
					return true;
				}
			}
			return false;
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	private static Method findMethod(Class<?> type, String name, Class<?>... params) {
		Class<?> c = type;
		while (c != null) {
			try {
				return c.getDeclaredMethod(name, params);
			} catch (NoSuchMethodException ignored) {
			}
			try {
				return c.getMethod(name, params);
			} catch (NoSuchMethodException ignored) {
			}
			c = c.getSuperclass();
		}
		return null;
	}

	private static Object readField(Object target, String name) {
		if (target == null) {
			return null;
		}
		Class<?> c = target.getClass();
		while (c != null) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (ReflectiveOperationException ignored) {
				c = c.getSuperclass();
			}
		}
		return null;
	}

	private static void writeField(Object target, String name, Object value) {
		if (target == null) {
			return;
		}
		Class<?> c = target.getClass();
		while (c != null) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				f.set(target, value);
				return;
			} catch (ReflectiveOperationException ignored) {
				c = c.getSuperclass();
			}
		}
	}

	private static ItemStack asItemStack(Object o) {
		return o instanceof ItemStack s ? s : null;
	}
}
