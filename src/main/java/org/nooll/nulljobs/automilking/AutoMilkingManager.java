package org.nooll.nulljobs.automilking;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.nooll.nulljobs.NullJobs;
import org.nooll.nulljobs.message.JobsPlaceholder;
import org.nooll.nulljobs.util.JobRewardMode;
import org.nooll.nulljobs.util.JobUtil;
import org.nooll.nulljobs.util.WorldGuardUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutoMilkingManager implements Listener {

    private final NullJobs plugin;
    private final Map<UUID, AutoMilkingSession> sessions = new HashMap<UUID, AutoMilkingSession>();
    private final Map<UUID, Long> cowCooldowns = new HashMap<UUID, Long>();

    private BukkitTask task;

    public AutoMilkingManager(NullJobs plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        disable();

        if (!enabled()) {
            return;
        }

        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tickSessions();
            }
        }, 20L, 20L);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        sessions.clear();
        cowCooldowns.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!enabled()) {
            return;
        }

        Entity entity = event.getRightClicked();

        if (!(entity instanceof Cow)) {
            return;
        }

        Player player = event.getPlayer();
        Cow cow = (Cow) entity;

        if (!inRegion(cow.getLocation())) {
            return;
        }

        if (!hasBucket(player)) {
            plugin.messages().path(player, "automilking.wrong-item");
            return;
        }

        event.setCancelled(true);

        long cooldownLeft = cooldownLeft(cow.getUniqueId());

        if (cooldownLeft > 0L) {
            plugin.messages().path(player, "automilking.cooldown",
                    JobsPlaceholder.of("time", String.valueOf(cooldownLeft)));
            return;
        }

        if (sessions.containsKey(player.getUniqueId())) {
            return;
        }

        sessions.put(player.getUniqueId(), new AutoMilkingSession(cow));

        plugin.messages().path(player, "automilking.started");
    }

    private void tickSessions() {
        for (UUID uuid : new java.util.ArrayList<UUID>(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            AutoMilkingSession session = sessions.get(uuid);

            if (player == null || session == null) {
                sessions.remove(uuid);
                continue;
            }

            Cow cow = session.cow();

            if (!valid(player, cow)) {
                sessions.remove(uuid);
                plugin.messages().path(player, "automilking.cancelled");
                continue;
            }

            int needed = Math.max(1, plugin.jobsConfig().config().getInt("automilking.hold-seconds", 3));
            int progress = session.progress() + 1;
            session.progress(progress);

            plugin.messages().path(player, "automilking.progress",
                    JobsPlaceholder.of("progress", String.valueOf(progress)),
                    JobsPlaceholder.of("needed", String.valueOf(needed)));

            if (progress < needed) {
                continue;
            }

            sessions.remove(uuid);
            complete(player, cow);
        }
    }

    private boolean valid(Player player, Cow cow) {
        if (cow == null || cow.isDead() || !cow.isValid()) {
            return false;
        }

        if (!player.isOnline() || player.isDead()) {
            return false;
        }

        if (!hasBucket(player)) {
            return false;
        }

        if (!inRegion(cow.getLocation())) {
            return false;
        }

        if (!player.getWorld().equals(cow.getWorld())) {
            return false;
        }

        double maxDistance = plugin.jobsConfig().config().getDouble("automilking.max-distance", 4.0D);

        if (player.getLocation().distance(cow.getLocation()) > maxDistance) {
            return false;
        }

        return isLookingAt(player, cow);
    }

    private void complete(Player player, Cow cow) {
        long cooldown = Math.max(1L, plugin.jobsConfig().config().getLong("automilking.cow-global-cooldown-seconds", 30L));
        cowCooldowns.put(cow.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);

        JobRewardMode mode = JobRewardMode.from(
                plugin.jobsConfig().config().getString("automilking.reward.type", "milk")
        );

        double money = plugin.jobsConfig().config().getDouble("automilking.reward.money", 5.0D);

        if (mode == JobRewardMode.MONEY || mode == JobRewardMode.BOTH) {
            JobUtil.runCommands(
                    plugin,
                    player,
                    money,
                    plugin.jobsConfig().config().getStringList("automilking.money-commands")
            );
        }

        if (mode == JobRewardMode.ITEMS || mode == JobRewardMode.BOTH) {
            takeBucket(player);
            player.getInventory().addItem(new ItemStack(Material.MILK_BUCKET, 1));
        }

        if (mode == JobRewardMode.MONEY) {
            plugin.messages().path(player, "automilking.completed-money",
                    JobsPlaceholder.of("amount", String.valueOf(money)));
            return;
        }

        if (mode == JobRewardMode.BOTH) {
            plugin.messages().path(player, "automilking.completed-both",
                    JobsPlaceholder.of("amount", String.valueOf(money)));
            return;
        }

        plugin.messages().path(player, "automilking.completed-milk");
    }

    private boolean hasBucket(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item != null && item.getType() == Material.BUCKET;
    }

    private void takeBucket(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.BUCKET) {
            return;
        }

        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }

        item.setAmount(item.getAmount() - 1);
    }

    private boolean isLookingAt(Player player, Cow cow) {
        Location eye = player.getEyeLocation();
        Location target = cow.getLocation().add(0.0D, cow.getHeight() * 0.5D, 0.0D);

        org.bukkit.util.Vector direction = eye.getDirection().normalize();
        org.bukkit.util.Vector toTarget = target.toVector().subtract(eye.toVector()).normalize();

        return direction.dot(toTarget) > 0.85D;
    }

    private long cooldownLeft(UUID cowUuid) {
        Long until = cowCooldowns.get(cowUuid);

        if (until == null) {
            return 0L;
        }

        long left = until - System.currentTimeMillis();

        if (left <= 0L) {
            cowCooldowns.remove(cowUuid);
            return 0L;
        }

        return (left + 999L) / 1000L;
    }

    private boolean inRegion(Location location) {
        String world = plugin.jobsConfig().config().getString("automilking.world", "world");
        String region = plugin.jobsConfig().config().getString("automilking.wg-region", "automilking");

        return WorldGuardUtil.contains(world, region, location);
    }

    private boolean enabled() {
        return plugin.jobsConfig().config().getBoolean("automilking.enabled", false);
    }
}