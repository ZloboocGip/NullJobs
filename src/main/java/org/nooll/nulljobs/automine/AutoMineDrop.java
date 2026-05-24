package org.nooll.nulljobs.automine;

import org.bukkit.Material;

public final class AutoMineDrop {

    private final Material material;
    private final double chance;
    private final double money;

    public AutoMineDrop(Material material, double chance, double money) {
        this.material = material;
        this.chance = chance;
        this.money = money;
    }

    public Material material() {
        return material;
    }

    public double chance() {
        return chance;
    }

    public double money() {
        return money;
    }
}