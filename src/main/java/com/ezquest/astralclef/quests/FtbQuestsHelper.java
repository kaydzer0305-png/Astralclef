package com.ezquest.astralclef.quests;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Soft FTB Quests integration via reflection. If FTB Quests is not present,
 * all checks return empty/false and log at debug. No compile-time dep.
 * <p>
 * When FTB Quests is available, we attempt to resolve quest completion via
 * its server-side API (method names vary by port — try several).
 */
public final class FtbQuestsHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ftb");

	private FtbQuestsHelper() {}

	public static boolean isQuestsPresent(MinecraftServer server) {
		if (server == null) {
			return false;
		}
		try {
			Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/** Whether a specific quest id is complete for the player (or team). Best-effort. */
	public static boolean isQuestComplete(MinecraftServer server, ServerPlayerEntity player, String questId) {
		if (server == null || questId == null || questId.isEmpty()) {
			return false;
		}
		try {
			Class<?> fileClass = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
			Method instance = fileClass.getMethod("getInstance");
			Object file = instance.invoke(null);
			if (file == null) {
				return false;
			}
			// Try file.getQuest(Identifier) or file.get(Identifier)
			// Quest ids are usually ResourceLocation; we try both.
			Class<?> idClass = Class.forName("net.minecraft.resources.ResourceLocation");
			Object resLoc = idClass.getConstructor(String.class).newInstance(questId);
			Object quest = null;
			for (String m : new String[]{"get", "getQuest"}) {
				try {
					Method getter = fileClass.getMethod(m, idClass);
					quest = getter.invoke(file, resLoc);
					if (quest != null) {
						break;
					}
				} catch (NoSuchMethodException ignored) {}
			}
			if (quest == null) {
				LOGGER.debug("FTB quest not found: {}", questId);
				return false;
			}
			// quest.isComplete(team) or quest.isCompleted(player)
			for (Method m : quest.getClass().getMethods()) {
				if ((m.getName().equals("isComplete") || m.getName().equals("isCompleted"))
						&& m.getParameterCount() == 1) {
					Object arg = player;
					// Some builds take TeamData
					if (!m.getParameterTypes()[0].isInstance(player)) {
						continue;
					}
					Object res = m.invoke(quest, arg);
					if (res instanceof Boolean b) {
						return b;
					}
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("FTB Quests check failed for {}: {}", questId, t.toString());
		}
		return false;
	}

	/** Human-readable FTB Quests presence for /astralclef status. */
	public static String status(MinecraftServer server) {
		if (server == null) {
			return "FTB Quests: unknown (no server)";
		}
		return isQuestsPresent(server) ? "FTB Quests: present" : "FTB Quests: absent (stub checks)";
	}
}
