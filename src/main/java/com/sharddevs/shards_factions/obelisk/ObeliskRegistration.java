package com.sharddevs.shards_factions.obelisk;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import com.sharddevs.shards_factions.ShardsFactions;

public class ObeliskRegistration {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ShardsFactions.MOD_ID);
    public static final DeferredBlock<Block> FACTION_OBELISK=
            BLOCKS.register("faction_obelisk",
                    () -> new FactionObeliskBlock(BlockBehaviour.Properties.of()
                            .strength(3.0f)
                            .lightLevel(state -> 7)
                            .noLootTable()));
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ShardsFactions.MOD_ID);
    public static final DeferredItem<BlockItem> FACTION_OBELISK_ITEM =
            ITEMS.register("faction_obelisk",
                    () -> new FactionObeliskItem(FACTION_OBELISK.get(), new Item.Properties()));
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ShardsFactions.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactionObeliskBlockEntity>> FACTION_OBELISK_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("faction_obelisk",
                    () -> BlockEntityType.Builder.of(
                            FactionObeliskBlockEntity::new,
                            FACTION_OBELISK.get()
                    ).build(null));
}
