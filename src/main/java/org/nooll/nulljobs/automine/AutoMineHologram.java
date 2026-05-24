package org.nooll.nulljobs.automine;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.nooll.nulljobs.NullJobs;

import java.util.List;

public final class AutoMineHologram {

    private final NullJobs plugin;

    public AutoMineHologram(NullJobs plugin) {
        this.plugin = plugin;
    }

    public void update(long secondsLeft) {
        if (!enabled()) {
            remove();
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            return;
        }

        String name = plugin.jobsConfig().config().getString("automine.hologram.name", "nulljobs_automine");
        World world = Bukkit.getWorld(plugin.jobsConfig().config().getString("automine.hologram.world", "world"));

        if (world == null) {
            return;
        }

        Location location = new Location(
                world,
                plugin.jobsConfig().config().getDouble("automine.hologram.x", 0.5D),
                plugin.jobsConfig().config().getDouble("automine.hologram.y", 90.0D),
                plugin.jobsConfig().config().getDouble("automine.hologram.z", 0.5D)
        );

        List<String> lines = plugin.jobsConfig().config().getStringList("automine.hologram.lines");

        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, lines.get(i).replace("{time}", formatTime(secondsLeft)));
        }

        Hologram hologram = DHAPI.getHologram(name);

        if (hologram == null) {
            DHAPI.createHologram(name, location, lines);
            return;
        }

        DHAPI.moveHologram(hologram, location);
        DHAPI.setHologramLines(hologram, lines);
    }

    public void remove() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            return;
        }

        String name = plugin.jobsConfig().config().getString("automine.hologram.name", "nulljobs_automine");
        Hologram hologram = DHAPI.getHologram(name);

        if (hologram != null) {
            DHAPI.removeHologram(name);
        }
    }

    private boolean enabled() {
        return plugin.jobsConfig().config().getBoolean("automine.hologram.enabled", false);
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60L;
        long left = seconds % 60L;

        if (minutes <= 0L) {
            return left + " сек.";
        }

        return minutes + " мин. " + left + " сек.";
    }
}