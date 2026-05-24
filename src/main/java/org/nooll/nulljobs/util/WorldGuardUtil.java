package org.nooll.nulljobs.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class WorldGuardUtil {

    private WorldGuardUtil() {
    }

    public static World world(String worldName) {
        return Bukkit.getWorld(worldName);
    }

    public static ProtectedRegion region(String worldName, String regionName) {
        World world = world(worldName);

        if (world == null) {
            return null;
        }

        RegionManager manager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));

        if (manager == null) {
            return null;
        }

        return manager.getRegion(regionName);
    }

    public static boolean contains(String worldName, String regionName, Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        World world = world(worldName);

        if (world == null || !location.getWorld().equals(world)) {
            return false;
        }

        ProtectedRegion region = region(worldName, regionName);

        if (region == null) {
            return false;
        }

        return region.contains(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }
}