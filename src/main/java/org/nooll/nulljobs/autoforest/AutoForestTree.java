package org.nooll.nulljobs.autoforest;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class AutoForestTree {

    private final int id;
    private final List<AutoForestBlock> blocks = new ArrayList<AutoForestBlock>();
    private int logs;
    private boolean fallen;

    public AutoForestTree(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public List<AutoForestBlock> blocks() {
        return blocks;
    }

    public void add(AutoForestBlock block) {
        blocks.add(block);

        Material material = block.data().getMaterial();
        if (isLog(material)) {
            logs++;
        }
    }

    public int logs() {
        return logs;
    }

    public boolean fallen() {
        return fallen;
    }

    public void fallen(boolean fallen) {
        this.fallen = fallen;
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD");
    }
}