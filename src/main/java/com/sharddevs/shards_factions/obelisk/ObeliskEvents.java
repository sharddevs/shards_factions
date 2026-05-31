package com.sharddevs.shards_factions.obelisk;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import com.sharddevs.shards_factions.ShardsFactions;

// ===========================================================================
// ObeliskEvents — destroy the obelisk item on player death (Addendum 6 §39.1).
//
// Obelisks are NOT soulbound and do NOT drop on death — they're destroyed.
// This (plus the continuous /f obelisk give cooldown, §39.3, the sole
// leak-prevention mechanism) is what keeps "one obelisk per faction"
// enforceable. Removes any obelisk item from the death-drops list and tells
// the player.
// ===========================================================================
@EventBusSubscriber(modid = ShardsFactions.MOD_ID)
public class ObeliskEvents {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        int before = event.getDrops().size();

        event.getDrops().removeIf(itemEntity ->
                itemEntity.getItem().getItem() instanceof FactionObeliskItem);

        int removed = before - event.getDrops().size();
        if (removed > 0 && event.getEntity() instanceof Player player) {
            player.sendSystemMessage(Component.literal(
                    "Your Obelisk was destroyed on death. Your faction owner can issue another with /f obelisk give."));
        }
    }
}