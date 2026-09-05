package com.ezquest.astralclef;

import com.ezquest.astralclef.command.AstralCommands;
import com.ezquest.astralclef.task.TaskRunner;
import com.ezquest.astralclef.tasks.create.CreateRecipeExecutor;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AstralclefMod implements ModInitializer {
	public static final String MOD_ID = "astralclef";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Astralclef loaded");
		CreateRecipeKinds.init();
		AstralCommands.register();
		// Advance TaskRunner then Create recipe jobs each server tick.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TaskRunner.getInstance().tick();
			CreateRecipeExecutor.getInstance().tick();
		});
	}
}
