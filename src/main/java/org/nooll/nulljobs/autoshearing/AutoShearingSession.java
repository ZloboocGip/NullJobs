package org.nooll.nulljobs.autoshearing;

import org.bukkit.entity.Sheep;

public final class AutoShearingSession {

    private final Sheep sheep;
    private int progress;

    public AutoShearingSession(Sheep sheep) {
        this.sheep = sheep;
    }

    public Sheep sheep() {
        return sheep;
    }

    public int progress() {
        return progress;
    }

    public void progress(int progress) {
        this.progress = progress;
    }
}