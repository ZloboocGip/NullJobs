package org.nooll.nulljobs.autofish;

import org.bukkit.Material;

public final class AutoFishDrop {

    private final Material material;
    private final double chance;
    private final double money;

    public AutoFishDrop(Material material, double chance, double money) {
        this.material = material;
        this.chance = chance;
        this.money = money;
    }

    public Material material() { return material; }

    public double chance() { return chance; }

    public double money() { return money; }
}