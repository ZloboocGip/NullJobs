package org.nooll.nulljobs.autoforest;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.nooll.nulljobs.NullJobs;
import org.nooll.nulljobs.message.JobsPlaceholder;
import org.nooll.nulljobs.util.JobRewardMode;
import org.nooll.nulljobs.util.JobUtil;
import org.nooll.nulljobs.util.WorldGuardUtil;

import java.util.*;

public final class AutoForestManager implements Listener {

    private final NullJobs plugin;
    private final List<AutoForestTree> trees = new ArrayList<AutoForestTree>();
    private final Map<String, AutoForestTree> blockTreeMap = new HashMap<String, AutoForestTree>();
    private final Map<Integer, Integer> hits = new HashMap<Integer, Integer>();
    private final Random random = new Random();

    public AutoForestManager(NullJobs plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        disable();

        if (!enabled()) {
            return;
        }

        scanTrees();
    }

    public void disable() {
        trees.clear();
        blockTreeMap.clear();
        hits.clear();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!enabled()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!inRegion(block.getLocation())) {
            return;
        }

        AutoForestTree tree = blockTreeMap.get(key(block));

        if (tree == null || tree.fallen()) {
            event.setCancelled(true);
            plugin.messages().path(player, "autoforest.deny-break");
            return;
        }

        if (!isLog(block.getType())) {
            event.setCancelled(true);
            plugin.messages().path(player, "autoforest.deny-break");
            return;
        }

        event.setCancelled(true);

        int currentHits = hits.getOrDefault(tree.id(), 0) + 1;
        hits.put(tree.id(), currentHits);

        int neededHits = Math.max(1,
                tree.logs() * plugin.jobsConfig().config().getInt("autoforest.hits-per-log", 3));

        plugin.messages().path(player, "autoforest.progress",
                JobsPlaceholder.of("hits", String.valueOf(currentHits)),
                JobsPlaceholder.of("needed", String.valueOf(neededHits)));

        if (currentHits < neededHits) {
            return;
        }

        hits.remove(tree.id());
        fallTree(player, tree);
    }

    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) {
            return;
        }

        if (!event.getEntity().hasMetadata("nulljobs_autoforest")) {
            return;
        }

        event.setCancelled(true);
        event.getEntity().remove();
    }

    private void scanTrees() {
        String worldName = plugin.jobsConfig().config().getString("autoforest.world", "world");
        String regionName = plugin.jobsConfig().config().getString("autoforest.wg-region", "autoforest");

        ProtectedRegion region = WorldGuardUtil.region(worldName, regionName);
        World world = WorldGuardUtil.world(worldName);

        if (region == null || world == null) {
            plugin.getLogger().warning("[AutoForest] Region or world not found");
            return;
        }

        Set<String> visited = new HashSet<String>();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int id = 0;

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);

                    if (!isTreeMaterial(block.getType())) {
                        continue;
                    }

                    String key = key(block);

                    if (visited.contains(key)) {
                        continue;
                    }

                    AutoForestTree tree = collectTree(++id, block, region, visited);

                    if (tree.logs() <= 0) {
                        continue;
                    }

                    trees.add(tree);

                    for (AutoForestBlock treeBlock : tree.blocks()) {
                        blockTreeMap.put(key(treeBlock.x(), treeBlock.y(), treeBlock.z()), tree);
                    }
                }
            }
        }

        plugin.getLogger().info("[AutoForest] Loaded trees: " + trees.size());
    }

    private AutoForestTree collectTree(int id, Block start, ProtectedRegion region, Set<String> visited) {
        AutoForestTree tree = new AutoForestTree(id);
        Queue<Block> queue = new LinkedList<Block>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Block block = queue.poll();
            String key = key(block);

            if (visited.contains(key)) {
                continue;
            }

            visited.add(key);

            if (!region.contains(block.getX(), block.getY(), block.getZ())) {
                continue;
            }

            if (!isTreeMaterial(block.getType())) {
                continue;
            }

            tree.add(new AutoForestBlock(
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    block.getBlockData().clone()
            ));

            for (Block near : neighbours(block)) {
                if (!visited.contains(key(near)) && isTreeMaterial(near.getType())) {
                    queue.add(near);
                }
            }
        }

        return tree;
    }

    private List<Block> neighbours(Block block) {
        List<Block> result = new ArrayList<Block>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    result.add(block.getRelative(dx, dy, dz));
                }
            }
        }

        return result;
    }

    private void fallTree(Player player, AutoForestTree tree) {
        String worldName = plugin.jobsConfig().config().getString("autoforest.world", "world");
        World world = WorldGuardUtil.world(worldName);

        if (world == null) {
            return;
        }

        tree.fallen(true);

        boolean animation = plugin.jobsConfig().config().getBoolean("autoforest.animation.enabled", true);
        int lifeTicks = Math.max(20,
                plugin.jobsConfig().config().getInt("autoforest.animation.falling-block-life-ticks", 60));
        double velocityY = plugin.jobsConfig().config().getDouble("autoforest.animation.velocity-y", 0.25D);
        double randomXZ = plugin.jobsConfig().config().getDouble("autoforest.animation.velocity-random-xz", 0.15D);

        int logs = 0;

        for (AutoForestBlock treeBlock : tree.blocks()) {
            Block block = world.getBlockAt(treeBlock.x(), treeBlock.y(), treeBlock.z());

            if (isLog(treeBlock.data().getMaterial())) {
                logs++;
            }

            block.setType(Material.AIR, false);

            if (animation) {
                Location location = new Location(
                        world,
                        treeBlock.x() + 0.5D,
                        treeBlock.y(),
                        treeBlock.z() + 0.5D
                );

                FallingBlock falling = world.spawnFallingBlock(location, treeBlock.data());
                falling.setDropItem(false);
                falling.setHurtEntities(false);
                falling.setMetadata("nulljobs_autoforest", new FixedMetadataValue(plugin, true));
                falling.setVelocity(new Vector(
                        randomRange(randomXZ),
                        velocityY,
                        randomRange(randomXZ)
                ));

                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (!falling.isDead()) {
                            falling.remove();
                        }
                    }
                }, lifeTicks);
            }
        }

        giveReward(player, logs);

        plugin.messages().path(player, "autoforest.felled",
                JobsPlaceholder.of("logs", String.valueOf(logs)));

        long restoreDelay = Math.max(1L,
                plugin.jobsConfig().config().getLong("autoforest.restore-delay-seconds", 30L)) * 20L;

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                restoreTree(tree);
            }
        }, restoreDelay);
    }

    private void restoreTree(AutoForestTree tree) {
        String worldName = plugin.jobsConfig().config().getString("autoforest.world", "world");
        World world = WorldGuardUtil.world(worldName);

        if (world == null) {
            return;
        }

        for (AutoForestBlock treeBlock : tree.blocks()) {
            Block block = world.getBlockAt(treeBlock.x(), treeBlock.y(), treeBlock.z());
            block.setBlockData(treeBlock.data().clone(), false);
        }

        tree.fallen(false);
    }

    private void giveReward(Player player, int logs) {
        if (!plugin.jobsConfig().config().getBoolean("autoforest.rewards.enabled", true)) {
            return;
        }

        JobRewardMode mode = JobRewardMode.from(
                plugin.jobsConfig().config().getString("autoforest.rewards.type", "items")
        );

        double amount = logs * plugin.jobsConfig().config().getDouble("autoforest.rewards.money-per-log", 1.0D);

        if (mode == JobRewardMode.MONEY || mode == JobRewardMode.BOTH) {
            JobUtil.runCommands(
                    plugin,
                    player,
                    amount,
                    plugin.jobsConfig().config().getStringList("autoforest.money-commands")
            );
        }

        if (mode == JobRewardMode.ITEMS || mode == JobRewardMode.BOTH) {
            Material item = Material.matchMaterial(
                    plugin.jobsConfig().config().getString("autoforest.rewards.item", "OAK_LOG")
            );

            if (item == null) {
                item = Material.OAK_LOG;
            }

            player.getInventory().addItem(new ItemStack(item, Math.max(1, logs)));
        }

        if (mode == JobRewardMode.MONEY) {
            plugin.messages().path(player, "autoforest.reward-money",
                    JobsPlaceholder.of("amount", String.valueOf(amount)));
            return;
        }

        if (mode == JobRewardMode.BOTH) {
            plugin.messages().path(player, "autoforest.reward-both",
                    JobsPlaceholder.of("amount", String.valueOf(amount)),
                    JobsPlaceholder.of("logs", String.valueOf(logs)));
            return;
        }

        plugin.messages().path(player, "autoforest.reward-item",
                JobsPlaceholder.of("logs", String.valueOf(logs)));
    }

    private double randomRange(double value) {
        return (random.nextDouble() * value * 2.0D) - value;
    }

    private boolean enabled() {
        return plugin.jobsConfig().config().getBoolean("autoforest.enabled", false);
    }

    private boolean inRegion(Location location) {
        String world = plugin.jobsConfig().config().getString("autoforest.world", "world");
        String region = plugin.jobsConfig().config().getString("autoforest.wg-region", "autoforest");

        return WorldGuardUtil.contains(world, region, location);
    }

    private boolean isTreeMaterial(Material material) {
        return isLog(material) || isLeaves(material);
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || (name.startsWith("STRIPPED_") && (name.endsWith("_LOG") || name.endsWith("_WOOD")));
    }

    private boolean isLeaves(Material material) {
        return material.name().endsWith("_LEAVES");
    }

    private String key(Block block) {
        return key(block.getX(), block.getY(), block.getZ());
    }

    private String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}