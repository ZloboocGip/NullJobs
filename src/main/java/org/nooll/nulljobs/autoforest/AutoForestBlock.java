package org.nooll.nulljobs.autoforest;

import org.bukkit.block.data.BlockData;

public final class AutoForestBlock {

    private final int x;
    private final int y;
    private final int z;
    private final BlockData data;

    public AutoForestBlock(int x, int y, int z, BlockData data) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.data = data;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public BlockData data() { return data; }
}