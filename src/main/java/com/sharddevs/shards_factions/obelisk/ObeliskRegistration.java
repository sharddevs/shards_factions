package com.sharddevs.shards_factions.obelisk;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.sharddevs.shards_factions.ShardsFactions;

// ===========================================================================
// ObeliskRegistration — registers the faction obelisk block, item, and block
// entity. The three DeferredRegisters are bound to the mod bus in
// ShardsFactions' constructor (BLOCKS/ITEMS/BLOCK_ENTITIES .register(modBus)).
//
// All three use the same registry name "faction_obelisk" — fine, since they
// live in different registries (block / item / block-entity-type).
// ===========================================================================
public class ObeliskRegistration {

    // -----------------------------------------------------------------------
    // BLOCK — strength 3, light level 7, no loot table (it's never a drop;
    // the item is handed out via /f obelisk give and destroyed on break).
    // -----------------------------------------------------------------------
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ShardsFactions.MOD_ID);

    public static final DeferredBlock<Block> FACTION_OBELISK =
            BLOCKS.register("faction_obelisk",
                    () -> new FactionObeliskBlock(BlockBehaviour.Properties.of()
                            .strength(3.0f)
                            .lightLevel(state -> 7)
                            .noLootTable()));

    // -----------------------------------------------------------------------
    // ITEM — the placeable obelisk item (FactionObeliskItem extends BlockItem).
    // -----------------------------------------------------------------------
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ShardsFactions.MOD_ID);

    public static final DeferredItem<BlockItem> FACTION_OBELISK_ITEM =
            ITEMS.register("faction_obelisk",
                    () -> new FactionObeliskItem(FACTION_OBELISK.get(), new Item.Properties()));

    // -----------------------------------------------------------------------
    // BLOCK ENTITY — stores the owning faction id so the binding survives
    // restarts and chunk unloads.
    // -----------------------------------------------------------------------
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ShardsFactions.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactionObeliskBlockEntity>> FACTION_OBELISK_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("faction_obelisk",
                    () -> BlockEntityType.Builder.of(
                            FactionObeliskBlockEntity::new,
                            FACTION_OBELISK.get()
                    ).build(null));
}