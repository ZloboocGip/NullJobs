package org.nooll.nulljobs.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nooll.nulljobs.NullJobs;

import java.io.File;

public final class JobsConfig {

    private final NullJobs plugin;

    private FileConfiguration config;
    private FileConfiguration messages;

    public JobsConfig(NullJobs plugin) {
        this.plugin = plugin;
    }

    public void load() {
        saveDefault("config.yml");
        saveDefault("messages.yml");
        reload();
    }

    public void reload() {
        saveDefault("config.yml");
        saveDefault("messages.yml");

        this.config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        this.messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration messages() {
        return messages;
    }

    public String prefix() {
        return config.getString("settings.prefix", "<green>NullJobs <dark_gray>» <reset>");
    }

    private void saveDefault(String name) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File file = new File(plugin.getDataFolder(), name);

        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }
}