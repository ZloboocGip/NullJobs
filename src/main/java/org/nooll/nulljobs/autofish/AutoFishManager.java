package org.nooll.nulljobs.autofish;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.nooll.nulljobs.NullJobs;
import org.nooll.nulljobs.message.JobsPlaceholder;
import org.nooll.nulljobs.util.JobRewardMode;
import org.nooll.nulljobs.util.JobUtil;
import org.nooll.nulljobs.util.WorldGuardUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class AutoFishManager implements Listener {

    private final NullJobs plugin;
    private final List<AutoFishDrop> drops = new ArrayList<>();
    private final Random random = new Random();

    public AutoFishManager(NullJobs plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        disable();

        for (String raw : plugin.jobsConfig().config().getStringList("autofish.drops")) {
            AutoFishDrop drop = parseDrop(raw);
            if (drop != null) {
                drops.add(drop);
            }
        }

        plugin.getLogger().info("[AutoFish] Loaded drops: " + drops.size());
    }

    public void disable() {
        drops.clear();
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (!enabled()) {
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        Location location = event.getHook() == null ? player.getLocation() : event.getHook().getLocation();

        if (!inRegion(location)) {
            return;
        }

        AutoFishDrop drop = randomDrop();
        if (drop == null) {
            return;
        }

        JobRewardMode mode = JobRewardMode.from(
                plugin.jobsConfig().config().getString("autofish.type", "items")
        );

        if (event.getCaught() instanceof Item) {
            Item caught = (Item) event.getCaught();

            if (mode == JobRewardMode.MONEY) {
                caught.remove();
            } else {
                caught.setItemStack(new ItemStack(drop.material(), 1));
            }
        }

        if (mode == JobRewardMode.MONEY || mode == JobRewardMode.BOTH) {
            JobUtil.runCommands(
                    plugin,
                    player,
                    drop.money(),
                    plugin.jobsConfig().config().getStringList("autofish.money-commands")
            );
        }

        if (mode == JobRewardMode.MONEY) {
            plugin.messages().path(player, "autofish.reward-money",
                    JobsPlaceholder.of("amount", String.valueOf(drop.money())),
                    JobsPlaceholder.of("item", drop.material().name()));
            return;
        }

        if (mode == JobRewardMode.BOTH) {
            plugin.messages().path(player, "autofish.reward-both",
                    JobsPlaceholder.of("amount", String.valueOf(drop.money())),
                    JobsPlaceholder.of("item", drop.material().name()));
            return;
        }

        plugin.messages().path(player, "autofish.reward-item",
                JobsPlaceholder.of("item", drop.material().name()));
    }

    private AutoFishDrop parseDrop(String raw) {
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
            double money = split.length >= 3 ? Double.parseDouble(split[2].replace(",", ".")) : 0.0D;
            return new AutoFishDrop(material, chance, money);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AutoFishDrop randomDrop() {
        double total = 0.0D;

        for (AutoFishDrop drop : drops) {
            total += Math.max(0.0D, drop.chance());
        }

        if (total <= 0.0D) {
            return null;
        }

        double value = random.nextDouble() * total;
        double current = 0.0D;

        for (AutoFishDrop drop : drops) {
            current += Math.max(0.0D, drop.chance());

            if (value <= current) {
                return drop;
            }
        }

        return null;
    }

    private boolean inRegion(Location location) {
        String world = plugin.jobsConfig().config().getString("autofish.world", "world");
        String region = plugin.jobsConfig().config().getString("autofish.wg-region", "autofish");

        return WorldGuardUtil.contains(world, region, location);
    }

    private boolean enabled() {
        return plugin.jobsConfig().config().getBoolean("autofish.enabled", false);
    }
}