package com.ezquest.astralclef;

import com.ezquest.astralclef.command.AstralCommands;
import com.ezquest.astralclef.recipes.KubeJsAwareCatalogue;
import com.ezquest.astralclef.task.TaskRunner;
import com.ezquest.astralclef.tasks.create.CreateRecipeKinds;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AstralclefMod implements ModInitializer {
	public static final String MOD_ID = "astralclef";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Astralclef loaded");

		TaskRunner.getInstance();
		CreateRecipeKinds.init();
		KubeJsAwareCatalogue.getInstance().refresh();
		AstralCommands.register();

		LOGGER.info("TaskRunner + CreateRecipeKinds + command hooks initialized (Ch0.5–1 ready)");
	}
}
