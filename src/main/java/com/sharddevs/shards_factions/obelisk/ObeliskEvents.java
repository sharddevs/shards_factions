package com.sharddevs.shards_factions.obelisk;

import com.sharddevs.shards_factions.ShardsFactions;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.network.chat.Component;
@EventBusSubscriber(modid = ShardsFactions.MOD_ID)
public class ObeliskEvents {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        //System.out.println("[DEBUG] LivingDropsEvent fired, entity: " + event.getEntity());
        //System.out.println("[DEBUG] drops list size: " + event.getDrops().size());
        //for (ItemEntity ie : event.getDrops()) {
            //System.out.println("[DEBUG] drop: " + ie.getItem()
            //+ " | item class: " + ie.getItem().getItem().getClass().getName());
       // }
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
