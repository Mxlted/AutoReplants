package com.autoreplants;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoReplantsMod implements ClientModInitializer {
    public static final String MOD_ID = "autoreplants";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        AutoReplantsHandler.register();
    }
}
