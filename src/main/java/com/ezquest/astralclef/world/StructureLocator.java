package com.ezquest.astralclef.world;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Locate world structures (dungeons/vaults) via 1.18.2 ServerWorld APIs.
 * Uses reflection to tolerate Yarn vs Mojmap and Ad Astra / vanilla structure ids.
 * Returns empty when not found or API absent; caller should degrade to stub.
 */
public final class StructureLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/structure");

	private StructureLocator() {}

	public static BlockPos locateNearest(ServerPlayerEntity player, String structureId) {
		if (player == null || structureId == null || structureId.isEmpty()) {
			return null;
		}
		ServerWorld world = (ServerWorld) player.getWorld();
		BlockPos origin = player.getBlockPos();
		Identifier id = Identifier.tryParse(structureId);
		if (id == null) {
			return null;
		}
		// 1.18.2 vanilla: world.locateStructure(Structure, center, radius, skipExisting)
		// Try several signatures via reflection so we don't hard-depend on Yarn mappings.
		try {
			// Try locateStructure with Identifier + TagKey path
			for (Method m : ServerWorld.class.getMethods()) {
				if (!"locateStructure".equals(m.getName())) {
					continue;
				}
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 4 && p[0].getSimpleName().contains("Structure")) {
					// Need to resolve Structure registry entry from id
					Object structure = resolveStructure(world, id);
					if (structure == null) continue;
					m.setAccessible(true);
					Object res = m.invoke(world, structure, origin, 100, false);
					if (res instanceof BlockPos pos) {
						LOGGER.info("StructureLocator {} -> {}", structureId, pos.toShortString());
						return pos;
					}
					if (res != null) {
						// Some builds return Pair<BlockPos, Structure>
						try {
							Method getFirst = res.getClass().getMethod("getFirst");
							Object first = getFirst.invoke(res);
							if (first instanceof BlockPos bp) return bp;
						} catch (Throwable ignored) {}
					}
				}
				if (p.length == 3 && p[0].getSimpleName().equals("TagKey")) {
					Object tag = tagForStructure(id);
					if (tag == null) continue;
					m.setAccessible(true);
					Object res = m.invoke(world, tag, origin, 100);
					if (res instanceof BlockPos bp) return bp;
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("StructureLocator locateStructure failed for {}: {}", structureId, t.toString());
		}
		// Fallback: scan via /locate command helper (reflection on command)
		LOGGER.debug("StructureLocator: no locate path succeeded for {}", structureId);
		return null;
	}

	private static Object resolveStructure(ServerWorld world, Identifier id) {
		// Reflection-only resolve to avoid compile-time Registry.STRUCTURE (1.19+) issues on 1.18.2.
		try {
			Class<?> regClass = Registry.class;
			for (java.lang.reflect.Field f : regClass.getFields()) {
				if (f.getName().equals("STRUCTURE") || f.getName().equals("STRUCTURE_KEY")) {
					Object reg = f.get(null);
					if (reg == null) continue;
					try {
						Method get = reg.getClass().getMethod("get", Identifier.class);
						Object val = get.invoke(reg, id);
						if (val != null) return val;
					} catch (Throwable ignored) {}
				}
			}
			// Try world registry manager reflectively
			for (Method m : world.getRegistryManager().getClass().getMethods()) {
				if (m.getName().equals("get") && m.getParameterCount() == 1) {
					try {
						Object reg = m.invoke(world.getRegistryManager(), findStructureKey());
						if (reg != null) {
							Method get = reg.getClass().getMethod("get", Identifier.class);
							Object val = get.invoke(reg, id);
							if (val != null) return val;
						}
					} catch (Throwable ignored) {}
				}
			}
		} catch (Throwable ignored) {}
		return null;
	}

	private static Object findStructureKey() {
		try {
			Class<?> keys = Class.forName("net.minecraft.util.registry.RegistryKey");
			Class<?> reg = Class.forName("net.minecraft.util.registry.Registry");
			for (java.lang.reflect.Field f : reg.getFields()) {
				if (f.getType() == keys && f.getName().toLowerCase().contains("structure")) {
					return f.get(null);
				}
			}
		} catch (Throwable ignored) {}
		return null;
	}

	private static Object tagForStructure(Identifier id) {
		try {
			Class<?> tagKey = Class.forName("net.minecraft.tag.TagKey");
			Class<?> registryKey = Class.forName("net.minecraft.util.registry.RegistryKey");
			Method of = tagKey.getMethod("of", registryKey, Identifier.class);
			// RegistryKeys.STRUCTURE not trivial; skip if unavailable
			return null;
		} catch (Throwable ignored) {
			return null;
		}
	}
}
