package org.nooll.nulljobs.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.nooll.nulljobs.NullJobs;
import org.nooll.nulljobs.message.JobsPlaceholder;

public final class NullJobsCommand implements CommandExecutor {

    private final NullJobs plugin;

    public NullJobsCommand(NullJobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            plugin.messages().path(sender, "nulljobs.usage",
                    JobsPlaceholder.of("label", label));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("nulljobs.command.reload")) {
                plugin.messages().path(sender, "no-permission",
                        JobsPlaceholder.of("permission", "nulljobs.command.reload"));
                return true;
            }

            plugin.reloadJobs();
            plugin.messages().path(sender, "nulljobs.reloaded");
            return true;
        }

        if (args[0].equalsIgnoreCase("version")) {
            plugin.messages().path(sender, "nulljobs.version",
                    JobsPlaceholder.of("version", plugin.getDescription().getVersion()));
            return true;
        }

        plugin.messages().path(sender, "nulljobs.usage",
                JobsPlaceholder.of("label", label));
        return true;
    }
}