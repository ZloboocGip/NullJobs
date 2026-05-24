package org.nooll.nulljobs.automilking;

import org.bukkit.entity.Cow;

public final class AutoMilkingSession {

    private final Cow cow;
    private int progress;

    public AutoMilkingSession(Cow cow) {
        this.cow = cow;
    }

    public Cow cow() {
        return cow;
    }

    public int progress() {
        return progress;
    }

    public void progress(int progress) {
        this.progress = progress;
    }
}