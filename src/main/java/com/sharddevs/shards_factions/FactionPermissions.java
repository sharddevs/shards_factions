package com.sharddevs.shards_factions;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public class FactionPermissions {

    // Everyday gameplay node — default TRUE for everyone. The in-handler
    // role gates (§16.2) do the real authority work; this layer just lets a
    // server admin revoke per-group via a permission manager if they want.
    public static PermissionNode<Boolean> MAP;

    // Bypass — default OP-FALSE. Not even OPs get it without an explicit
    // grant (§13.5: "not merely OP"). The one node that is genuinely gated.
    public static PermissionNode<Boolean> BYPASS;

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        MAP = new PermissionNode<>(
                ShardsFactions.MOD_ID, "map",
                PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> false);
        event.addNodes(MAP);

        BYPASS = new PermissionNode<>(
                ShardsFactions.MOD_ID, "bypass",
                PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> false);
        event.addNodes(BYPASS);
    }
}