package org.nooll.nulljobs;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.nooll.nulljobs.automilking.AutoMilkingManager;
import org.nooll.nulljobs.automine.AutoMineManager;
import org.nooll.nulljobs.autofish.AutoFishManager;
import org.nooll.nulljobs.autoforest.AutoForestManager;
import org.nooll.nulljobs.autoshearing.AutoShearingManager;
import org.nooll.nulljobs.command.NullJobsCommand;
import org.nooll.nulljobs.config.JobsConfig;
import org.nooll.nulljobs.message.JobsMessage;

public final class NullJobs extends JavaPlugin {

    private AutoShearingManager autoShearingManager;
    private JobsConfig jobsConfig;
    private JobsMessage messages;
    private AutoMilkingManager autoMilkingManager;
    private AutoMineManager autoMineManager;
    private AutoForestManager autoForestManager;
    private AutoFishManager autoFishManager;

    @Override
    public void onEnable() {
        this.jobsConfig = new JobsConfig(this);
        this.jobsConfig.load();
        this.autoMilkingManager = new AutoMilkingManager(this);
        Bukkit.getPluginManager().registerEvents(autoMilkingManager, this);
        this.messages = new JobsMessage(this);
        this.autoShearingManager = new AutoShearingManager(this);
        Bukkit.getPluginManager().registerEvents(autoShearingManager, this);
        this.autoMineManager = new AutoMineManager(this);
        this.autoForestManager = new AutoForestManager(this);
        this.autoFishManager = new AutoFishManager(this);

        Bukkit.getPluginManager().registerEvents(autoMineManager, this);
        Bukkit.getPluginManager().registerEvents(autoForestManager, this);
        Bukkit.getPluginManager().registerEvents(autoFishManager, this);

        reloadJobs();

        if (getCommand("nulljobs") != null) {
            getCommand("nulljobs").setExecutor(new NullJobsCommand(this));
        }

        getLogger().info("NullJobs enabled");
    }

    @Override
    public void onDisable() {
        if (autoMineManager != null) {
            autoMineManager.disable();
        }
        if (autoMilkingManager != null) {
            autoMilkingManager.disable();
        }
        if (autoForestManager != null) {
            autoForestManager.disable();
        }

        if (autoFishManager != null) {
            autoFishManager.disable();
        }
        if (autoShearingManager != null) {
            autoShearingManager.disable();
        }
        getLogger().info("NullJobs disabled");
    }

    public void reloadJobs() {
        jobsConfig.reload();
        autoMilkingManager.reload();
        autoMineManager.reload();
        autoForestManager.reload();
        autoFishManager.reload();
        autoShearingManager.reload();
    }

    public JobsConfig jobsConfig() {
        return jobsConfig;
    }

    public JobsMessage messages() {
        return messages;
    }
}