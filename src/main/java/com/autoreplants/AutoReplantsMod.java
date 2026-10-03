package com.autoreplants;

import net.fabricmc.api.ClientModInitializer;

public class AutoReplantsMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SettingsStore.load();
        AutoReplantsHandler.register();
    }
}
