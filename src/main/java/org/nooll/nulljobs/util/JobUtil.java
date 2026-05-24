package org.nooll.nulljobs.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nooll.nulljobs.NullJobs;

import java.util.List;

public final class JobUtil {

    private JobUtil() {
    }

    public static void runCommands(NullJobs plugin, Player player, double amount, List<String> commands) {
        for (String command : commands) {
            if (command == null || command.trim().isEmpty()) {
                continue;
            }

            String parsed = command
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{amount}", String.valueOf(amount));

            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }
}