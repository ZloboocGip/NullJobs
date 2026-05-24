package org.nooll.nulljobs.autoshearing;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
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
import java.util.concurrent.ThreadLocalRandom;

public final class AutoShearingManager implements Listener {

    private final NullJobs plugin;
    private final Map<UUID, AutoShearingSession> sessions = new HashMap<UUID, AutoShearingSession>();
    private final Map<UUID, Long> sheepCooldowns = new HashMap<UUID, Long>();

    private BukkitTask task;

    public AutoShearingManager(NullJobs plugin) {
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
        sheepCooldowns.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!enabled()) {
            return;
        }

        Entity entity = event.getRightClicked();

        if (!(entity instanceof Sheep)) {
            return;
        }

        Player player = event.getPlayer();
        Sheep sheep = (Sheep) entity;

        if (!inRegion(sheep.getLocation())) {
            return;
        }

        event.setCancelled(true);

        if (!hasShears(player)) {
            plugin.messages().path(player, "autoshearing.wrong-item");
            return;
        }

        if (sheep.isSheared()) {
            plugin.messages().path(player, "autoshearing.already-sheared");
            return;
        }

        long cooldownLeft = cooldownLeft(sheep.getUniqueId());

        if (cooldownLeft > 0L) {
            plugin.messages().path(player, "autoshearing.cooldown",
                    JobsPlaceholder.of("time", String.valueOf(cooldownLeft)));
            return;
        }

        if (sessions.containsKey(player.getUniqueId())) {
            return;
        }

        sessions.put(player.getUniqueId(), new AutoShearingSession(sheep));

        plugin.messages().path(player, "autoshearing.started");
    }

    @EventHandler
    public void onVanillaShear(PlayerShearEntityEvent event) {
        if (!enabled()) {
            return;
        }

        if (!(event.getEntity() instanceof Sheep)) {
            return;
        }

        if (!inRegion(event.getEntity().getLocation())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onRegrow(SheepRegrowWoolEvent event) {
        if (!enabled()) {
            return;
        }

        Sheep sheep = event.getEntity();

        if (!inRegion(sheep.getLocation())) {
            return;
        }

        if (cooldownLeft(sheep.getUniqueId()) > 0L) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEatGrass(EntityChangeBlockEvent event) {
        if (!enabled()) {
            return;
        }

        if (!(event.getEntity() instanceof Sheep)) {
            return;
        }

        if (!inRegion(event.getEntity().getLocation())) {
            return;
        }

        event.setCancelled(true);
    }

    private void tickSessions() {
        for (UUID uuid : new java.util.ArrayList<UUID>(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            AutoShearingSession session = sessions.get(uuid);

            if (player == null || session == null) {
                sessions.remove(uuid);
                continue;
            }

            Sheep sheep = session.sheep();

            if (!valid(player, sheep)) {
                sessions.remove(uuid);
                plugin.messages().path(player, "autoshearing.cancelled");
                continue;
            }

            int needed = Math.max(1, plugin.jobsConfig().config().getInt("autoshearing.hold-seconds", 3));
            int progress = session.progress() + 1;
            session.progress(progress);

            plugin.messages().path(player, "autoshearing.progress",
                    JobsPlaceholder.of("progress", String.valueOf(progress)),
                    JobsPlaceholder.of("needed", String.valueOf(needed)));

            if (progress < needed) {
                continue;
            }

            sessions.remove(uuid);
            complete(player, sheep);
        }
    }

    private boolean valid(Player player, Sheep sheep) {
        if (sheep == null || sheep.isDead() || !sheep.isValid()) {
            return false;
        }

        if (!player.isOnline() || player.isDead()) {
            return false;
        }

        if (!hasShears(player)) {
            return false;
        }

        if (sheep.isSheared()) {
            return false;
        }

        if (!inRegion(sheep.getLocation())) {
            return false;
        }

        if (!player.getWorld().equals(sheep.getWorld())) {
            return false;
        }

        double maxDistance = plugin.jobsConfig().config().getDouble("autoshearing.max-distance", 4.0D);

        if (player.getLocation().distance(sheep.getLocation()) > maxDistance) {
            return false;
        }

        return isLookingAt(player, sheep);
    }

    private void complete(Player player, Sheep sheep) {
        DyeColor color = sheep.getColor();
        Material wool = woolMaterial(color);

        sheep.setSheared(true);

        long cooldown = Math.max(1L, plugin.jobsConfig().config().getLong("autoshearing.sheep-global-cooldown-seconds", 30L));
        sheepCooldowns.put(sheep.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);

        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!sheep.isDead() && sheep.isValid()) {
                    if (randomWoolMode()) {
                        sheep.setColor(randomColor());
                    }

                    sheep.setSheared(false);
                    sheepCooldowns.remove(sheep.getUniqueId());
                }
            }
        }, cooldown * 20L);

        JobRewardMode mode = JobRewardMode.from(
                plugin.jobsConfig().config().getString("autoshearing.reward.type", "wool")
        );

        double money = moneyForColor(color);

        if (mode == JobRewardMode.MONEY || mode == JobRewardMode.BOTH) {
            JobUtil.runCommands(
                    plugin,
                    player,
                    money,
                    plugin.jobsConfig().config().getStringList("autoshearing.money-commands")
            );
        }

        if (mode == JobRewardMode.ITEMS || mode == JobRewardMode.BOTH) {
            int amount = Math.max(1, plugin.jobsConfig().config().getInt("autoshearing.reward.wool-amount", 1));
            player.getInventory().addItem(new ItemStack(wool, amount));
        }

        if (mode == JobRewardMode.MONEY) {
            plugin.messages().path(player, "autoshearing.completed-money",
                    JobsPlaceholder.of("amount", String.valueOf(money)),
                    JobsPlaceholder.of("color", color.name()));
            return;
        }

        if (mode == JobRewardMode.BOTH) {
            plugin.messages().path(player, "autoshearing.completed-both",
                    JobsPlaceholder.of("amount", String.valueOf(money)),
                    JobsPlaceholder.of("color", color.name()));
            return;
        }

        plugin.messages().path(player, "autoshearing.completed-wool",
                JobsPlaceholder.of("color", color.name()));
    }
    private boolean randomWoolMode() {
        return plugin.jobsConfig().config()
                .getString("autoshearing.reward.wool-mode", "normal")
                .equalsIgnoreCase("random");
    }

    private DyeColor randomColor() {
        DyeColor[] colors = DyeColor.values();
        return colors[ThreadLocalRandom.current().nextInt(colors.length)];
    }
    private double moneyForColor(DyeColor color) {
        String path = "autoshearing.reward.money-by-color." + color.name();
        double fallback = plugin.jobsConfig().config().getDouble("autoshearing.reward.money", 3.0D);
        return plugin.jobsConfig().config().getDouble(path, fallback);
    }

    private Material woolMaterial(DyeColor color) {
        Material material = Material.matchMaterial(color.name() + "_WOOL");
        return material == null ? Material.WHITE_WOOL : material;
    }

    private boolean hasShears(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item != null && item.getType() == Material.SHEARS;
    }

    private boolean isLookingAt(Player player, Sheep sheep) {
        Location eye = player.getEyeLocation();
        Location target = sheep.getLocation().add(0.0D, sheep.getHeight() * 0.5D, 0.0D);

        org.bukkit.util.Vector direction = eye.getDirection().normalize();
        org.bukkit.util.Vector toTarget = target.toVector().subtract(eye.toVector()).normalize();

        return direction.dot(toTarget) > 0.85D;
    }

    private long cooldownLeft(UUID sheepUuid) {
        Long until = sheepCooldowns.get(sheepUuid);

        if (until == null) {
            return 0L;
        }

        long left = until - System.currentTimeMillis();

        if (left <= 0L) {
            sheepCooldowns.remove(sheepUuid);
            return 0L;
        }

        return (left + 999L) / 1000L;
    }

    private boolean inRegion(Location location) {
        String world = plugin.jobsConfig().config().getString("autoshearing.world", "world");
        String region = plugin.jobsConfig().config().getString("autoshearing.wg-region", "autoshearing");

        return WorldGuardUtil.contains(world, region, location);
    }

    private boolean enabled() {
        return plugin.jobsConfig().config().getBoolean("autoshearing.enabled", false);
    }
}