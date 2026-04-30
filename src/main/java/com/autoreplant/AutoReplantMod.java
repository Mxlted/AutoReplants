package com.autoreplant;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoReplantMod implements ClientModInitializer {
    public static final String MOD_ID = "autoreplant";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        AutoReplantHandler.register();
    }
}
