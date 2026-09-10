package com.ezquest.astralclef.combat;

import com.ezquest.astralclef.bot.BotMovement;
import com.ezquest.astralclef.inventory.InventoryHelper;
import com.ezquest.astralclef.movement.BaritoneHelper;
import com.ezquest.astralclef.task.Task;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.gather.GatherTask;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

/**
 * Great Beast combat phase (Ch6 gate before singularity craft).
 * Runs as a {@link Task} so it can be driven by TaskRunner or
 * embedded inside {@link com.ezquest.astralclef.tasks.phases.ChAstralSingularityTask}.
 * <p>
 * Combat is server-side assist, not a full AI: locate paths toward the nearest
 * hostile (Baritone when present), engage swings the player's held weapon via
 * {@code ServerPlayerEntity.attack}, and loot gates on the area going clear.
 * Soft — advances with a warning if no hostiles show up (never hard-stalls
 * the win path).
 */
public class GreatBeastPhase extends Task {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/combat/beast");

	/** How far to look for the beast / an arena fight. */
	private static final double LOCATE_RADIUS = 48.0;
	/** Melee reach used before swinging (vanilla reach ~3 + slack). */
	private static final double ATTACK_REACH = 4.0;
	/** Area considered "the fight" for clear checks. */
	private static final double FIGHT_RADIUS = 16.0;
	/** Ticks to wait for any hostile before soft-advancing. */
	private static final int ENGAGE_SOFT_TIMEOUT = 600;

	private enum Step { LOCATE_BEAST, GATHER_WEAPON, ENGAGE, LOOT, DONE }
	private Step step = Step.LOCATE_BEAST;
	private Task gatherWeaponTask;
	private int ticks;
	private int ticksInEngage;
	private boolean engaged;

	@Override
	public boolean isEqual(Task other) {
		return other instanceof GreatBeastPhase;
	}

	@Override
	protected void onStart() {
		step = Step.LOCATE_BEAST;
		gatherWeaponTask = null;
		ticks = 0;
		ticksInEngage = 0;
		engaged = false;
		LOGGER.info("Great Beast phase begun — locate → engage → loot");
	}

	@Override
	protected Task onTick() {
		ticks++;
		ServerPlayerEntity player = firstPlayer();
		switch (step) {
			case LOCATE_BEAST: {
				if (player == null) {
					step = Step.GATHER_WEAPON;
					break;
				}
				HostileEntity beast = nearestHostile(player, LOCATE_RADIUS);
				if (beast == null) {
					LOGGER.debug("Great Beast: no hostiles within {} — advancing to weapon check", (int) LOCATE_RADIUS);
					step = Step.GATHER_WEAPON;
					break;
				}
				BlockPos target = beast.getBlockPos();
				double distSq = player.getBlockPos().getSquaredDistance(target);
				if (distSq <= 144.0) { // within 12 — close enough to arm up
					step = Step.GATHER_WEAPON;
					break;
				}
				if (BaritoneHelper.isPresent()) {
					BaritoneHelper.pathTo(player, target);
					if (ticks % 100 == 1) {
						LOGGER.info("Great Beast: pathing to hostile at {}", target.toShortString());
					}
					break;
				}
				LOGGER.info("Great Beast: hostile at {} but Baritone absent — advancing (fight it manually)",
						target.toShortString());
				step = Step.GATHER_WEAPON;
				break;
			}
			case GATHER_WEAPON:
				if (player != null && hasWeapon(player)) {
					gatherWeaponTask = null;
					step = Step.ENGAGE;
					break;
				}
				if (player == null) {
					step = Step.ENGAGE;
					break;
				}
				if (gatherWeaponTask == null || gatherWeaponTask.isFinished()) {
					gatherWeaponTask = new GatherTask("minecraft:diamond_sword", 1);
					LOGGER.info("Great Beast: delegating to GatherTask for weapon");
				}
				return gatherWeaponTask;
			case ENGAGE: {
				ticksInEngage++;
				if (player == null) {
					step = Step.LOOT;
					break;
				}
				HostileEntity foe = nearestHostile(player, FIGHT_RADIUS);
				if (foe == null) {
					if (engaged) {
						LOGGER.info("Great Beast: area clear after fight — looting");
					} else if (ticksInEngage >= ENGAGE_SOFT_TIMEOUT) {
						LOGGER.warn("Great Beast: no hostiles for {} ticks — soft-advancing (fight manually if needed)",
								ticksInEngage);
					} else {
						if (ticks % 100 == 1) {
							LOGGER.info("Great Beast: waiting for hostiles near the player (tick {}/{})",
									ticksInEngage, ENGAGE_SOFT_TIMEOUT);
						}
						break;
					}
					step = Step.LOOT;
					break;
				}
				double dist = Math.sqrt(player.squaredDistanceTo(foe));
				if (ticks % 4 == 1) {
					if (dist < 3.0) {
						// Too close — back off away from the foe (kiting).
						Vec3d away = player.getPos().subtract(foe.getPos());
						if (away.lengthSquared() > 0.001) {
							away = away.normalize().multiply(3.0);
							BotMovement.stepToward(player,
									new BlockPos(player.getPos().add(away)), 0.5, 2.5);
						}
					} else if (dist > ATTACK_REACH + 1.0) {
						// Strafe in on alternating flanks instead of walking straight at it.
						Vec3d to = foe.getPos().subtract(player.getPos()).normalize();
						Vec3d perp = new Vec3d(-to.z, 0.0, to.x);
						double flank = (ticks % 8 == 1) ? 1.0 : -1.0;
						Vec3d dest = player.getPos().add(to.multiply(1.5)).add(perp.multiply(3.0 * flank));
						BotMovement.stepToward(player, new BlockPos(dest), 1.0, 2.5);
					}
				}
				if (dist <= ATTACK_REACH + 0.5 && ticks % 8 == 1) {
					try {
						player.attack(foe);
						engaged = true;
						LOGGER.debug("Great Beast: attacked {}", foe.getType().getName().getString());
					} catch (Throwable t) {
						LOGGER.debug("Great Beast: attack failed: {}", t.toString());
					}
				}
				break;
			}
			case LOOT:
				if (player == null || nearestHostile(player, 12.0) == null) {
					LOGGER.info("Great Beast down — loot the drops, advancing");
					step = Step.DONE;
					break;
				}
				LOGGER.debug("Great Beast: hostiles still nearby — re-engaging");
				step = Step.ENGAGE;
				break;
			case DONE:
				break;
		}
		if (gatherWeaponTask != null && gatherWeaponTask.isFinished()) {
			gatherWeaponTask = null;
		}
		return null;
	}

	/** Nearest living hostile to the player within {@code radius}, or null. */
	private HostileEntity nearestHostile(ServerPlayerEntity player, double radius) {
		try {
			ServerWorld world = (ServerWorld) player.getWorld();
			Box box = new Box(player.getBlockPos()).expand(radius);
			List<HostileEntity> found = world.getEntitiesByClass(HostileEntity.class, box, e -> e != null && e.isAlive());
			return found.stream()
					.min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
					.orElse(null);
		} catch (Throwable t) {
			LOGGER.debug("Great Beast: hostile scan failed: {}", t.toString());
			return null;
		}
	}

	private boolean hasWeapon(ServerPlayerEntity player) {
		return InventoryHelper.hasAny(player,
				"minecraft:netherite_sword", "minecraft:diamond_sword", "minecraft:iron_sword",
				"ad_astra:desh_sword");
	}

	private ServerPlayerEntity firstPlayer() {
		try {
			var ctx = CreateRecipeExecutor.getInstance().getWorldContext();
			if (ctx != null && ctx.isValid() && ctx.getWorld() != null && ctx.getWorld().getServer() != null) {
				var list = ctx.getWorld().getServer().getPlayerManager().getPlayerList();
				if (!list.isEmpty()) return list.get(0);
			}
		} catch (Throwable ignored) {}
		return null;
	}

	public void engage() {
		LOGGER.info("Great Beast engage() called (TaskRunner path preferred)");
	}

	@Override
	protected void onStop(Task interrupt) {
		// gatherWeaponTask is a TaskRunner-managed subtask; TaskRunner handles its stop
		// via the returned Task reference. Just clear our handle.
		gatherWeaponTask = null;
		LOGGER.debug("GreatBeast stopped at {} (interrupt={})", step, interrupt);
	}

	@Override
	public boolean isFinished() {
		return step == Step.DONE;
	}

	@Override
	protected String toDebugString() {
		return "GreatBeast/" + step;
	}
}
