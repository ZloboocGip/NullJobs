package org.nooll.nulljobs.automine;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;
import org.nooll.nulljobs.NullJobs;
import org.nooll.nulljobs.message.JobsPlaceholder;
import org.nooll.nulljobs.util.JobRewardMode;
import org.nooll.nulljobs.util.JobUtil;
import org.nooll.nulljobs.util.WorldGuardUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class AutoMineManager implements Listener {

    private final NullJobs plugin;
    private final List<AutoMineDrop> drops = new ArrayList<AutoMineDrop>();
    private final Set<Material> replaceBlocks = new HashSet<Material>();
    private final Random random = new Random();
    private final AutoMineHologram hologram;

    private BukkitTask refreshTask;
    private BukkitTask hologramTask;
    private long nextRefreshMillis;

    public AutoMineManager(NullJobs plugin) {
        this.plugin = plugin;
        this.hologram = new AutoMineHologram(plugin);
    }

    public void reload() {
        disable();

        for (String raw : plugin.jobsConfig().config().getStringList("automine.mine-blocks")) {
            AutoMineDrop drop = parseDrop(raw);

            if (drop != null) {
                drops.add(drop);
            }
        }

        for (String raw : plugin.jobsConfig().config().getStringList("automine.replace-blocks")) {
            Material material = Material.matchMaterial(raw);

            if (material != null) {
                replaceBlocks.add(material);
            }
        }

        if (!enabled()) {
            return;
        }

        long seconds = Math.max(1L, plugin.jobsConfig().config().getLong("automine.refresh-interval-seconds", 300L));
        long interval = Math.max(20L, seconds * 20L);

        this.nextRefreshMillis = System.currentTimeMillis() + seconds * 1000L;

        this.refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                refreshMine();
                nextRefreshMillis = System.currentTimeMillis() + seconds * 1000L;
            }
        }, interval, interval);

        this.hologramTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                long left = Math.max(0L, (nextRefreshMillis - System.currentTimeMillis() + 999L) / 1000L);
                hologram.update(left);
            }
        }, 20L, 20L);

        plugin.getLogger().info("[AutoMine] Loaded drops: " + drops.size());
    }

    public void disable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }

        if (hologram != null) {
            hologram.remove();
        }

        drops.clear();
        replaceBlocks.clear();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!enabled()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!inRegion(block)) {
            return;
        }

        AutoMineDrop drop = findDrop(block.getType());

        if (drop == null) {
            event.setCancelled(true);
            plugin.messages().path(player, "automine.deny-break");
            return;
        }

        Material originalType = block.getType();

        JobRewardMode mode = JobRewardMode.from(
                plugin.jobsConfig().config().getString("automine.type", "items")
        );

        if (mode == JobRewardMode.MONEY) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            block.setType(Material.AIR, false);

            runMoney(player, drop.money());

            plugin.messages().path(player, "automine.reward-money",
                    JobsPlaceholder.of("amount", String.valueOf(drop.money())),
                    JobsPlaceholder.of("block", originalType.name()));
            return;
        }

        if (mode == JobRewardMode.BOTH) {
            runMoney(player, drop.money());

            plugin.messages().path(player, "automine.reward-both",
                    JobsPlaceholder.of("amount", String.valueOf(drop.money())),
                    JobsPlaceholder.of("block", originalType.name()));
            return;
        }

        plugin.messages().path(player, "automine.reward-item",
                JobsPlaceholder.of("block", originalType.name()));
    }

    private void refreshMine() {
        String worldName = plugin.jobsConfig().config().getString("automine.world", "world");
        String regionName = plugin.jobsConfig().config().getString("automine.wg-region", "automine");

        ProtectedRegion region = WorldGuardUtil.region(worldName, regionName);
        World world = WorldGuardUtil.world(worldName);

        if (region == null || world == null) {
            return;
        }

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int changed = 0;

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);

                    if (!replaceBlocks.contains(block.getType())) {
                        continue;
                    }

                    AutoMineDrop selected = randomDrop();

                    if (selected == null) {
                        continue;
                    }

                    block.setType(selected.material(), false);
                    changed++;
                }
            }
        }

        plugin.getLogger().info("[AutoMine] Refreshed blocks: " + changed);
    }

    private AutoMineDrop parseDrop(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        String[] split = raw.split(":");

        if (split.length < 2) {
            return null;
        }

        Material material = Material.matchMaterial(split[0]);

        if (material == null) {
            return null;
        }

        try {
            double chance = Double.parseDouble(split[1].replace(",", "."));
            double money = split.length >= 3
                    ? Double.parseDouble(split[2].replace(",", "."))
                    : 0.0D;

            return new AutoMineDrop(material, chance, money);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AutoMineDrop randomDrop() {
        double total = 0.0D;

        for (AutoMineDrop drop : drops) {
            total += Math.max(0.0D, drop.chance());
        }

        if (total <= 0.0D) {
            return null;
        }

        double value = random.nextDouble() * total;
        double current = 0.0D;

        for (AutoMineDrop drop : drops) {
            current += Math.max(0.0D, drop.chance());

            if (value <= current) {
                return drop;
            }
        }

        return null;
    }

    private AutoMineDrop findDrop(Material material) {
        for (AutoMineDrop drop : drops) {
            if (drop.material() == material) {
                return drop;
            }
        }

        return null;
    }

    private void runMoney(Player player, double amount) {
        JobUtil.runCommands(
                plugin,
                player,
                amount,
                plugin.jobsConfig().config().getStringList("automine.money-commands")
        );
    }

    private boolean inRegion(Block block) {
        String worldName = plugin.jobsConfig().config().getString("automine.world", "world");
        String regionName = plugin.jobsConfig().config().getString("automine.wg-region", "automine");

        return WorldGuardUtil.contains(worldName, regionName, block.getLocation());
    }

    private boolean enabled() {
        return plugin.jobsConfig().config().getBoolean("automine.enabled", false);
    }
}