package com.autoreplants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("autoreplants");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autoreplants.json");
    private static Settings settings = new Settings();

    private SettingsStore() {
    }

    public static Settings get() {
        return settings;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            if (loaded == null) {
                throw new IllegalArgumentException("Empty settings");
            }
            loaded.validate();
            settings = loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}, using default settings", CONFIG_PATH, e);
        }
    }

    public static void save(Settings updated) {
        Settings saved = updated.copy();
        saved.validate();
        Path temporary = null;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            temporary = Files.createTempFile(CONFIG_PATH.getParent(), "autoreplants-", ".tmp");
            Files.writeString(temporary, GSON.toJson(saved) + System.lineSeparator());
            try {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            settings = saved;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not save {}", CONFIG_PATH, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    LOGGER.warn("Could not remove temporary settings file", e);
                }
            }
        }
    }
}

