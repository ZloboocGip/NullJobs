package org.nooll.nulljobs.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.nooll.nulljobs.NullJobs;

public final class JobsMessage {

    private static final char SECTION = '\u00A7';

    private final NullJobs plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .character(SECTION)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public JobsMessage(NullJobs plugin) {
        this.plugin = plugin;
    }

    public void path(CommandSender sender, String path, JobsPlaceholder... placeholders) {
        String raw = plugin.jobsConfig().messages().getString(path, "<red>Missing message: " + path);
        message(sender, raw, placeholders);
    }

    public void message(CommandSender sender, String raw, JobsPlaceholder... placeholders) {
        if (sender == null || raw == null || raw.isEmpty()) {
            return;
        }

        sender.sendMessage(format(raw, placeholders));
    }

    public String format(String raw, JobsPlaceholder... placeholders) {
        String result = raw.replace("{prefix}", plugin.jobsConfig().prefix());

        if (placeholders != null) {
            for (JobsPlaceholder placeholder : placeholders) {
                if (placeholder == null) {
                    continue;
                }

                result = result.replace("{" + placeholder.key() + "}", placeholder.value());
            }
        }

        result = legacyToMini(result);

        try {
            Component component = miniMessage.deserialize(result);
            return legacy.serialize(component);
        } catch (Exception ex) {
            return ChatColor.translateAlternateColorCodes('&', result);
        }
    }

    private String legacyToMini(String input) {
        return input
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>")
                .replace("&r", "<reset>");
    }
}