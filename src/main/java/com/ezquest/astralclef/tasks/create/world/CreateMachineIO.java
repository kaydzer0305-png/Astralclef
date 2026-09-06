package com.ezquest.astralclef.tasks.create.world;

import java.util.Optional;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Insert / extract helpers for Create machines.
 * Prefers typed Create BE I/O via {@link CreateBlockEntityIO} (Basin / Depot / Crafter / …),
 * then Fabric Transfer {@link ItemStorage}, then {@link Inventory}.
 */
public final class CreateMachineIO {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-io");

	private CreateMachineIO() {}

	public static boolean hasBlockEntity(ServerWorld world, BlockPos pos) {
		return world != null && pos != null && world.getBlockEntity(pos) != null;
	}

	/**
	 * Attempt to insert {@code stack} into the machine at {@code pos}.
	 * @return remaining stack (empty if fully inserted)
	 */
	public static ItemStack insert(ServerWorld world, BlockPos pos, ItemStack stack) {
		if (world == null || pos == null || stack == null || stack.isEmpty()) {
			return stack == null ? ItemStack.EMPTY : stack;
		}
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null) {
			LOGGER.warn("insert: no block entity at {}", pos.toShortString());
			return stack;
		}
		LOGGER.info("insert: trying {} x{} into {} ({})",
				stack.getItem(), stack.getCount(), pos.toShortString(), be.getType());

		Optional<ItemStack> typed = CreateBlockEntityIO.tryInsert(world, pos, stack);
		if (typed.isPresent()) {
			return typed.get();
		}

		ItemStack viaTransfer = insertTransfer(world, pos, stack);
		if (viaTransfer.getCount() != stack.getCount() || viaTransfer.isEmpty()) {
			return viaTransfer;
		}
		return insertInventory(be, stack);
	}

	/**
	 * Extract up to {@code maxCount} items from the machine (any item if filter empty).
	 */
	public static ItemStack extract(ServerWorld world, BlockPos pos, ItemStack filter, int maxCount) {
		if (world == null || pos == null || maxCount <= 0) {
			return ItemStack.EMPTY;
		}
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null) {
			LOGGER.warn("extract: no block entity at {}", pos.toShortString());
			return ItemStack.EMPTY;
		}
		LOGGER.info("extract: from {} ({}) max={}", pos.toShortString(), be.getType(), maxCount);

		Optional<ItemStack> typed = CreateBlockEntityIO.tryExtract(world, pos, filter, maxCount);
		if (typed.isPresent() && !typed.get().isEmpty()) {
			return typed.get();
		}

		ItemStack viaTransfer = extractTransfer(world, pos, filter, maxCount);
		if (!viaTransfer.isEmpty()) {
			return viaTransfer;
		}
		ItemStack viaInv = extractInventory(be, filter, maxCount);
		if (!viaInv.isEmpty()) {
			return viaInv;
		}
		return typed.orElse(ItemStack.EMPTY);
	}

	/** Verify machine still present; used by PROCESS step. */
	public static boolean verifyPresent(ServerWorld world, BlockPos pos) {
		boolean ok = hasBlockEntity(world, pos);
		if (!ok) {
			LOGGER.warn("verifyPresent failed at {}", pos != null ? pos.toShortString() : "null");
		}
		return ok;
	}

	/**
	 * PROCESS helper: true when Create press/mixer reports {@code running}, or when the
	 * basin's operator above is running. False when undetectable or idle.
	 */
	public static boolean isLikelyProcessing(ServerWorld world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		BlockEntity be = world.getBlockEntity(pos);
		Optional<Boolean> typed = CreateBlockEntityIO.isProcessing(be);
		if (typed.isPresent()) {
			return typed.get();
		}
		BlockEntity above = world.getBlockEntity(pos.up(2));
		Optional<Boolean> op = CreateBlockEntityIO.isProcessing(above);
		return op.orElse(false);
	}

	private static ItemStack insertTransfer(ServerWorld world, BlockPos pos, ItemStack stack) {
		try {
			Storage<ItemVariant> storage = ItemStorage.SIDED.find(world, pos, Direction.UP);
			if (storage == null) {
				storage = ItemStorage.SIDED.find(world, pos, null);
			}
			if (storage == null || !storage.supportsInsertion()) {
				return stack;
			}
			ItemVariant variant = ItemVariant.of(stack);
			long inserted;
			try (Transaction tx = Transaction.openOuter()) {
				inserted = storage.insert(variant, stack.getCount(), tx);
				tx.commit();
			}
			if (inserted <= 0) {
				return stack;
			}
			ItemStack remaining = stack.copy();
			remaining.decrement((int) inserted);
			LOGGER.debug("insertTransfer: inserted {}", inserted);
			return remaining;
		} catch (Throwable t) {
			LOGGER.debug("insertTransfer unavailable: {}", t.toString());
			return stack;
		}
	}

	private static ItemStack extractTransfer(ServerWorld world, BlockPos pos, ItemStack filter, int maxCount) {
		try {
			Storage<ItemVariant> storage = ItemStorage.SIDED.find(world, pos, Direction.DOWN);
			if (storage == null) {
				storage = ItemStorage.SIDED.find(world, pos, null);
			}
			if (storage == null || !storage.supportsExtraction()) {
				return ItemStack.EMPTY;
			}
			try (Transaction tx = Transaction.openOuter()) {
				for (StorageView<ItemVariant> view : storage) {
					if (view.isResourceBlank()) {
						continue;
					}
					ItemVariant resource = view.getResource();
					ItemStack asStack = resource.toStack(1);
					if (filter != null && !filter.isEmpty() && !ItemStack.areItemsEqual(filter, asStack)) {
						continue;
					}
					long extracted = storage.extract(resource, maxCount, tx);
					if (extracted > 0) {
						tx.commit();
						LOGGER.debug("extractTransfer: extracted {}", extracted);
						return resource.toStack((int) extracted);
					}
				}
			}
			return ItemStack.EMPTY;
		} catch (Throwable t) {
			LOGGER.debug("extractTransfer unavailable: {}", t.toString());
			return ItemStack.EMPTY;
		}
	}

	private static ItemStack insertInventory(BlockEntity be, ItemStack stack) {
		if (!(be instanceof Inventory inv)) {
			LOGGER.info("insertInventory: BE is not Inventory — soft-ok (typed Create path already tried)");
			return ItemStack.EMPTY;
		}
		ItemStack remaining = stack.copy();
		for (int i = 0; i < inv.size() && !remaining.isEmpty(); i++) {
			ItemStack slot = inv.getStack(i);
			if (slot.isEmpty()) {
				int put = Math.min(remaining.getCount(), remaining.getMaxCount());
				inv.setStack(i, remaining.split(put));
			} else if (ItemStack.canCombine(slot, remaining)) {
				int space = slot.getMaxCount() - slot.getCount();
				if (space > 0) {
					int move = Math.min(space, remaining.getCount());
					slot.increment(move);
					remaining.decrement(move);
					inv.setStack(i, slot);
				}
			}
		}
		inv.markDirty();
		return remaining;
	}

	private static ItemStack extractInventory(BlockEntity be, ItemStack filter, int maxCount) {
		if (!(be instanceof Inventory inv)) {
			LOGGER.info("extractInventory: BE is not Inventory — empty after typed Create attempt");
			return ItemStack.EMPTY;
		}
		for (int i = 0; i < inv.size(); i++) {
			ItemStack slot = inv.getStack(i);
			if (slot.isEmpty()) {
				continue;
			}
			if (filter != null && !filter.isEmpty() && !ItemStack.areItemsEqual(filter, slot)) {
				continue;
			}
			int take = Math.min(maxCount, slot.getCount());
			ItemStack out = slot.split(take);
			inv.setStack(i, slot);
			inv.markDirty();
			return out;
		}
		return ItemStack.EMPTY;
	}
}
