package com.ezquest.astralclef.tasks.create.world;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

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
 *   <li>{@value #GAP_KINETIC_PROCESS} — press/mixer {@code running} is best-effort reflective; no recipe match</li>
 * </ul>
 */
public final class CreateBlockEntityIO {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-be-io");

	public static final String GAP_SPOUT_FLUID = "spout fluid I/O soft — use Fluid Transfer";
	public static final String GAP_BASIN_FILTER_WRITE = "basin FilteringBehaviour write not implemented";
	public static final String GAP_CRAFTER_PATTERN = "mechanical crafter pattern encode TODO";
	public static final String GAP_KINETIC_PROCESS = "press/mixer process detection best-effort only";

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
	 * Best-effort Create processing detection for press / mixer (and basin operator above).
	 * @return empty if not a known processing BE; true/false when detectable
	 */
	public static Optional<Boolean> isProcessing(BlockEntity be) {
		if (be == null) {
			return Optional.empty();
		}
		if (isInstanceOfAny(be, MIXER_CLASSES) || isInstanceOfAny(be, PRESS_CLASSES)) {
			Object running = readField(be, "running");
			if (running instanceof Boolean b) {
				return Optional.of(b);
			}
			Object behaviour = invokeNoArg(be, "getPressingBehaviour");
			if (behaviour != null) {
				Object br = readField(behaviour, "running");
				if (br instanceof Boolean b) {
					return Optional.of(b);
				}
			}
			LOGGER.debug("isProcessing: {}", GAP_KINETIC_PROCESS);
			return Optional.of(false);
		}
		return Optional.empty();
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
