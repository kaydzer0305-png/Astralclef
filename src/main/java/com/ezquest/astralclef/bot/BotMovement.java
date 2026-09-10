package com.ezquest.astralclef.bot;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side movement — the bot's "feet".
 * <p>
 * The server cannot press keys, so movement is done with small direct
 * teleports ({@code ServerPlayerEntity.teleport} is always legal, unlike
 * client-sent moves, so vanilla anti-cheat does not kick). Every step lands
 * on a verified standable column (passable feet+head, solid ground, no
 * fluids), and fall distance is zeroed, so stepping is fast but never
 * clips into walls or plunges into voids/lava.
 */
public final class BotMovement {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/bot-move");

	private BotMovement() {}

	/**
	 * Take one step toward a block (aims at its center).
	 *
	 * @param stopDist stop and return true within this distance of the center
	 * @param maxStep  max blocks per step
	 * @return true when arrived; false when stepped (call again) or stuck
	 */
	public static boolean stepToward(ServerPlayerEntity player, BlockPos target, double stopDist, double maxStep) {
		if (player == null || target == null) {
			return true;
		}
		ServerWorld world = (ServerWorld) player.getWorld();
		Vec3d cur = player.getPos();
		Vec3d dst = Vec3d.ofCenter(target);
		double dx = dst.x - cur.x;
		double dy = dst.y - cur.y;
		double dz = dst.z - cur.z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist <= stopDist) {
			return true;
		}
		double step = Math.min(Math.max(0.5, maxStep), dist);
		double nx = cur.x + dx / dist * step;
		double nz = cur.z + dz / dist * step;
		double wantY = cur.y + Math.signum(dy) * Math.min(step, Math.abs(dy));

		Integer safeY = findStandableY(world, nx, wantY, nz);
		if (safeY == null) {
			LOGGER.debug("BotMovement stuck: no standable column near {} {} {}",
					(int) nx, (int) wantY, (int) nz);
			return false;
		}
		try {
			player.teleport(world, nx, safeY + 0.02, nz, player.getYaw(), player.getPitch());
			player.fallDistance = 0.0F;
		} catch (Throwable t) {
			LOGGER.debug("BotMovement teleport failed: {}", t.toString());
			return false;
		}
		return false;
	}

	/**
	 * Nearest standable feet-Y at the given column, searching a few blocks
	 * around {@code wantY}. Null when the column is unsafe (fluid, no ground,
	 * or headroom blocked everywhere scanned).
	 */
	private static Integer findStandableY(ServerWorld world, double x, double wantY, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int top = (int) Math.floor(wantY) + 2;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int y = top; y >= top - 7; y--) {
			m.set(bx, y, bz);
			if (isPassable(world, m) && isPassable(world, m.up()) && isGround(world, m.down())) {
				return y;
			}
		}
		return null;
	}

	private static boolean isPassable(ServerWorld world, BlockPos pos) {
		try {
			BlockState state = world.getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				return false;
			}
			return state.getCollisionShape(world, pos).isEmpty();
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean isGround(ServerWorld world, BlockPos pos) {
		try {
			BlockState state = world.getBlockState(pos);
			if (!state.getFluidState().isEmpty() || state.isAir()) {
				return false;
			}
			return state.isSideSolidFullSquare(world, pos, Direction.UP);
		} catch (Throwable t) {
			return false;
		}
	}
}
