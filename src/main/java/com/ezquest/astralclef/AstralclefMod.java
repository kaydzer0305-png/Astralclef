package com.ezquest.astralclef;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AstralclefMod implements ModInitializer {
    public static final String MOD_ID = "astralclef";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  @Override
    public void onInitialize() {
          LOGGER.info("Astralclef loaded");
    }
}
