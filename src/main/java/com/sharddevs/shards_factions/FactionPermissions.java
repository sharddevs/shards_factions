package com.sharddevs.shards_factions;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

// ===========================================================================
// FactionPermissions — NeoForge PermissionAPI nodes (Addendum 2 §18 layer).
//
// Standalone: depends only on NeoForge's PermissionAPI, NOT the LuckPerms API
// jar. A permission manager (LuckPerms etc.) plugs in automatically if present
// and can grant/revoke these nodes; absent one, the node's default resolver
// (below) is the final word.
//
// Nodes are stored static and registered on PermissionGatherEvent.Nodes,
// which fires on the GAME bus (FactionPermissions.class is registered to
// NeoForge.EVENT_BUS in ShardsFactions' constructor).
//
// GOTCHA: querying an UNREGISTERED node throws. Because the command tree is
// sent to clients during the join handshake (which runs the .requires
// predicates that query these nodes), a null/unregistered node does not fail
// the command quietly — it bricks player JOIN with "Invalid player data".
// So: every node a command .requires MUST be assigned AND addNodes'd here.
// ===========================================================================
public class FactionPermissions {

    // /f map — explicit-grant-only (resolver -> false). On a server with no
    // permission manager, nobody can use /f map until the node is granted.
    // (This was a deliberate choice; see §18 — gated even though it's a
    // gameplay command.)
    public static PermissionNode<Boolean> MAP;

    // /f bypass — explicit-grant-only (resolver -> false). Not even OPs get it
    // without a grant (§13.5: "not merely OP"). The genuinely-locked node.
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