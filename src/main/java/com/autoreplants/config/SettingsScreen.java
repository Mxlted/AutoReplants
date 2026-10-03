package com.autoreplants.config;

import com.autoreplants.Settings;
import com.autoreplants.SettingsStore;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen {
    private SettingsScreen() {
    }

    public static Screen create(Screen parent) {
        Settings settings = SettingsStore.get().copy();
        Settings defaults = new Settings();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("autoreplants.settings.title"))
            .setSavingRunnable(() -> SettingsStore.save(settings));
        var category = builder.getOrCreateCategory(Component.translatable("autoreplants.settings.general"));
        var entries = builder.entryBuilder();

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("autoreplants.settings.enabled"), settings.enabled)
            .setDefaultValue(defaults.enabled)
            .setTooltip(Component.translatable("autoreplants.settings.enabled.tooltip"))
            .setSaveConsumer(value -> settings.enabled = value)
            .build());

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("autoreplants.settings.requireHoe"), settings.requireHoe)
            .setDefaultValue(defaults.requireHoe)
            .setTooltip(Component.translatable("autoreplants.settings.requireHoe.tooltip"))
            .setSaveConsumer(value -> settings.requireHoe = value)
            .build());

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("autoreplants.settings.matureOnly"), settings.matureOnly)
            .setDefaultValue(defaults.matureOnly)
            .setTooltip(Component.translatable("autoreplants.settings.matureOnly.tooltip"))
            .setSaveConsumer(value -> settings.matureOnly = value)
            .build());

        category.addEntry(entries.startIntSlider(
                Component.translatable("autoreplants.settings.replantDelay"), settings.replantDelay, 0, 10)
            .setDefaultValue(defaults.replantDelay)
            .setTooltip(Component.translatable("autoreplants.settings.replantDelay.tooltip"))
            .setSaveConsumer(value -> settings.replantDelay = value)
            .build());

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("autoreplants.settings.sneakBypass"), settings.sneakBypass)
            .setDefaultValue(defaults.sneakBypass)
            .setTooltip(Component.translatable("autoreplants.settings.sneakBypass.tooltip"))
            .setSaveConsumer(value -> settings.sneakBypass = value)
            .build());

        return builder.build();
    }
}
