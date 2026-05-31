package com.sharddevs.shards_factions.obelisk;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

// ===========================================================================
// FactionObeliskItem — the obelisk's placeable item.
//
// A thin BlockItem subclass with no overrides (Addendum 6 §42). It exists as
// the home for future item-level behavior — notably the v2 styled display
// name (V2_BACKLOG #7) via a getName override. The no-containers / drop
// overrides once considered here were dropped (§39.2); containers and Q-drop
// are allowed, so nothing is overridden today.
// ===========================================================================
public class FactionObeliskItem extends BlockItem {

    public FactionObeliskItem(Block block, Properties properties) {
        super(block, properties);
    }
}