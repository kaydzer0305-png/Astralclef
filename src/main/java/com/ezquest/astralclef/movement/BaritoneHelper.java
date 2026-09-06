package com.ezquest.astralclef.movement;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Soft Baritone integration. If Baritone API is present, we attempt to issue
 * goal commands via reflection; otherwise we log and return false so callers
 * degrade gracefully. No compile-time dep on Baritone.
 */
public final class BaritoneHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/baritone");

	private BaritoneHelper() {}

	public static boolean isPresent() {
		try {
			Class.forName("baritone.api.BaritoneAPI");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/** Try to mine a set of blocks (MineProcess). Returns true when queued. */
	public static boolean mineBlocks(ServerPlayerEntity player, java.util.List<net.minecraft.block.Block> blocks, int count) {
		if (player == null || blocks == null || blocks.isEmpty()) {
			return false;
		}
		if (!isPresent()) {
			LOGGER.debug("Baritone absent — mineBlocks {} stub", blocks);
			return false;
		}
		try {
			Class<?> api = Class.forName("baritone.api.BaritoneAPI");
			Object provider = api.getMethod("getProvider").invoke(null);
			Object baritone = null;
			for (java.lang.reflect.Method m : provider.getClass().getMethods()) {
				if (m.getName().startsWith("getBaritone") && m.getParameterCount() == 1) {
					try { baritone = m.invoke(provider, player); if (baritone != null) break; } catch (Throwable ignored) {}
				}
			}
			if (baritone == null) return false;
			Object mineProcess = baritone.getClass().getMethod("getMineProcess").invoke(baritone);
			// mineProcess.mine(int amount, Block... toMine) or mine(Block)
			for (java.lang.reflect.Method m : mineProcess.getClass().getMethods()) {
				if (!"mine".equals(m.getName())) continue;
				Class<?>[] p = m.getParameterTypes();
				try {
					if (p.length == 2 && p[0] == int.class) {
						Object[] arr = blocks.toArray(new net.minecraft.block.Block[0]);
						// varargs needs array
						m.setAccessible(true);
						m.invoke(mineProcess, count, (Object) arr);
						LOGGER.info("Baritone mine {} x{}", blocks, count);
						return true;
					} else if (p.length == 1 && p[0].isArray()) {
						m.invoke(mineProcess, (Object) blocks.toArray(new net.minecraft.block.Block[0]));
						return true;
					}
				} catch (Throwable inner) {
					LOGGER.debug("Baritone mine invoke variant failed: {}", inner.toString());
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("Baritone mineBlocks failed: {}", t.toString());
		}
		return false;
	}

	/** Try to path to a block pos. Returns true when a goal was set. */
	public static boolean pathTo(ServerPlayerEntity player, BlockPos pos) {
		if (player == null || pos == null) {
			return false;
		}
		if (!isPresent()) {
			LOGGER.debug("Baritone absent — pathTo {} stub", pos.toShortString());
			return false;
		}
		try {
			Class<?> api = Class.forName("baritone.api.BaritoneAPI");
			Method getProvider = api.getMethod("getProvider");
			Object provider = getProvider.invoke(null);
			Method getBaritone = provider.getClass().getMethod("getBaritone", player.getClass().getSuperclass());
			// Fallback: getBaritoneForPlayer
			Object baritone = null;
			for (Method m : provider.getClass().getMethods()) {
				if (m.getName().startsWith("getBaritone") && m.getParameterCount() == 1) {
					try {
						baritone = m.invoke(provider, player);
						if (baritone != null) {
							break;
						}
					} catch (Throwable ignored) {}
				}
			}
			if (baritone == null) {
				return false;
			}
			// baritone.getCustomGoalProcess().setGoalAndPath(GoalBlock)
			Method getCustomGoal = baritone.getClass().getMethod("getCustomGoalProcess");
			Object proc = getCustomGoal.invoke(baritone);
			Class<?> goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock");
			Object goal = goalBlock.getConstructor(int.class, int.class, int.class)
					.newInstance(pos.getX(), pos.getY(), pos.getZ());
			Method setGoal = proc.getClass().getMethod("setGoalAndPath", goalBlock);
			setGoal.invoke(proc, goal);
			LOGGER.info("Baritone pathTo {}", pos.toShortString());
			return true;
		} catch (Throwable t) {
			LOGGER.debug("Baritone pathTo failed: {}", t.toString());
			return false;
		}
	}

	public static String status() {
		return isPresent() ? "Baritone: present" : "Baritone: absent (stub movement)";
	}
}
