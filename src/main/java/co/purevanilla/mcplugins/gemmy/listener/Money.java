package co.purevanilla.mcplugins.gemmy.listener;

import co.purevanilla.mcplugins.gemmy.Main;
import co.purevanilla.mcplugins.gemmy.event.Death;
import co.purevanilla.mcplugins.gemmy.event.Pickup;
import co.purevanilla.mcplugins.gemmy.util.Drop;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Money implements Listener {

    @EventHandler
    public void blockDestroy(final BlockBreakEvent e){

        if(!Main.settings.disabledWorlds.contains(e.getBlock().getWorld().getName())) {

            Player player = e.getPlayer();

            final Material blockBroken = e.getBlock().getType();
            final Block blockBrokenObj = e.getBlock();
            Ageable crop = null;
            try{
                crop = (Ageable) blockBrokenObj.getBlockData();
            } catch(ClassCastException err){
                // do nothing
            }

            final Ageable finalCropParent = crop;
            Bukkit.getScheduler().runTaskAsynchronously(Main.plugin, new Runnable() {
                @Override
                public void run() {

                    try{
                        if(Main.settings.getBlocksHashMap().containsKey(blockBroken) || Main.settings.getProducts().contains(blockBroken) || Main.settings.getSeeds().contains(blockBroken)){

                            if(Main.playerPlaced.contains(blockBrokenObj)){
                                Main.playerPlaced.remove(blockBrokenObj);
                            } else {

                                final Ageable finalCrop = finalCropParent;

                                // already running on async, check if it has been turned into air

                                if(e.getBlock().getLocation().getWorld().getBlockAt(e.getBlock().getLocation()).getType() == Material.AIR){

                                    if(Main.settings.getBlocks().contains(blockBroken)){

                                        new Drop(e.getBlock().getLocation(),Main.settings.getBlockRange(blockBroken).getAmount(),player).spawn();

                                    } else if (finalCrop != null && (Main.settings.getProducts().contains(blockBroken) || Main.settings.getSeeds().contains(blockBroken))){

                                        if(finalCrop.getAge()== finalCrop.getMaximumAge()){

                                            new Drop(e.getBlock().getLocation(),Main.settings.getHarvest(blockBroken).getHarvest().getAmount(),player).spawn();

                                            // expect harvesting change
                                            Main.expectedReplants.put(blockBrokenObj.getLocation(),Main.settings.getHarvest(blockBroken));

                                        }

                                    }


                                }


                            }

                        }

                    } catch(NullPointerException err) {
                        // item already removed
                    }

                }
            });
        }

    }

    @EventHandler
    public void onDrop(final EntityDropItemEvent e){
        Bukkit.getScheduler().runTaskAsynchronously(Main.plugin, new Runnable() {
            @Override
            public void run() {
                try{
                    Drop drop = new Drop(e.getItemDrop().getItemStack());
                    if(drop.hasQuantity()){
                        e.getItemDrop().remove();
                        drop.setLocation(e.getItemDrop().getLocation());
                        drop.spawn();
                    }
                } catch(NullPointerException ignored){

                }
            }
        });
    }

    @EventHandler
    public void inventoryEvent(final InventoryClickEvent e){

        Bukkit.getScheduler().runTaskAsynchronously(Main.plugin, new Runnable() {
            @Override
            public void run() {

                try{
                    List<ItemStack> itemStacks = new ArrayList<>();
                    if(e.getAction()== InventoryAction.PLACE_ALL||e.getAction()==InventoryAction.PLACE_ONE ||e.getAction()==InventoryAction.PLACE_SOME){
                        if(e.getClickedInventory().getType()==InventoryType.PLAYER){
                            ItemStack[] Slots = e.getClickedInventory().getStorageContents();
                            for (ItemStack item:Slots) {
                                if(item!=null){
                                    itemStacks.add(item);
                                }
                            }
                        }
                    } else if(e.getAction()==InventoryAction.MOVE_TO_OTHER_INVENTORY){
                        for (HumanEntity entity :e.getViewers()) {
                            ItemStack[] Slots = entity.getInventory().getStorageContents();
                            for (ItemStack item:Slots) {
                                if(item != null){
                                    itemStacks.add(item);
                                }
                            }
                        }
                    }

                    for (ItemStack item:itemStacks) {
                        try{
                            Drop drop = new Drop(item);
                            if(drop.hasQuantity()){

                                Main.plugin.getServer().getScheduler().runTask(Main.plugin, () -> {
                                    Pickup event = new Pickup((Player) e.getWhoClicked(), drop.getQuantity());
                                    Bukkit.getPluginManager().callEvent(event);
                                });

                                Main.econ.depositPlayer((OfflinePlayer) e.getWhoClicked(),(float) drop.getQuantity());
                                item.setAmount(0);
                            }
                        } catch (NullPointerException err){
                            // invalid drop
                        }
                    }
                } catch(NullPointerException err){

                }
            }
        });

    }

    @EventHandler
    public void pistonPush(BlockPistonExtendEvent event){
        if(event.isCancelled()) return;
        pistonPushEvent(event.getBlocks(), event.getDirection(), event.getBlock().getWorld());
    }

    @EventHandler
    public void pistonPush(BlockPistonRetractEvent event){
        if(event.isCancelled()) return;
        pistonPushEvent(event.getBlocks(), event.getDirection(), event.getBlock().getWorld());
    }

    private void pistonPushEvent(List<Block> blocks, BlockFace direction, World world){
        double deltaX = 0;
        double deltaY = 0;
        double deltaZ = 0;
        switch (direction) {
            case UP -> deltaY++;
            case DOWN -> deltaY--;
            case EAST -> deltaX++;
            case WEST -> deltaX--;
            case SOUTH -> deltaZ++;
            case NORTH -> deltaZ--;
        }
        for(Block movedBlock: blocks){
            Material blockBroken = movedBlock.getType();
            if(Main.settings.getBlocksHashMap().containsKey(blockBroken) || Main.settings.getProducts().contains(blockBroken) || Main.settings.getSeeds().contains(blockBroken)){
                Main.playerPlaced.remove(movedBlock);
                Main.playerPlaced.add(world.getBlockAt(movedBlock.getLocation().add(deltaX, deltaY, deltaZ)));
            }
        }
    }

    @EventHandler
    public void entityConvertEvent(EntityTransformEvent event){
        Entity parentEntity = event.getEntity();
        if(parentEntity.hasMetadata("Spawner")){

            // if an entity gets transformed, and the original entity was spawned from an spawner, it will also get
            // transformed.

            event.getTransformedEntity().setMetadata("Spawner",new FixedMetadataValue(Main.plugin,true));
        }
    }


    @EventHandler
    public void entityDeath(final EntityDeathEvent e){

        if(!e.getEntity().hasMetadata("Spawner")){
            Bukkit.getScheduler().runTaskAsynchronously(Main.plugin, new Runnable() {
                @Override
                public void run() {
                    if(!Main.settings.disabledWorlds.contains(e.getEntity().getWorld().getName())) {
                        try {
                            Player killer = e.getEntity().getKiller();
                            if (killer != null) {
                                Drop drop = new Drop(e.getEntity().getLocation(), Main.settings.entityTypeRangeHashMap().get(e.getEntityType()).getAmount(),killer);
                                drop.spawn();

                            }
                        } catch (NullPointerException err) {

                        }
                    }
                }
            });
        }
    }

    @EventHandler
    public void nearPickupGem(final PlayerMoveEvent e){

        if(!Main.settings.disabledWorlds.contains(e.getPlayer().getWorld().getName())){
            if(e.getPlayer().getInventory().firstEmpty()==-1){
                // the inventory of this player is full, check if the player is standing on top of a drop instead for adding the money
                List<Entity> entities = e.getPlayer().getNearbyEntities(2, 2, 2);
                Bukkit.getScheduler().runTaskAsynchronously(Main.plugin, new Runnable() {
                    @Override
                    public void run() {
                        for (Entity entity : entities)  {
                            if (entity instanceof Item) {
                                try{
                                    Item item = (Item) entity;
                                    if(item.getItemStack().getType()==Main.settings.getGem()||item.getItemStack().getType()==Main.settings.getLargeGem()) {

                                        Drop drop = new Drop(item.getItemStack());
                                        if(drop.hasQuantity()){
                                            Bukkit.getScheduler().runTaskLater(Main.plugin, new Runnable() {
                                                @Override
                                                public void run() {
                                                    if(Main.settings.nearPickupParticleEnabled){
                                                        entity.getWorld().spawnParticle(Main.settings.nearPickupParticle,entity.getLocation(),2);
                                                    }
                                                    entity.remove();
                                                }
                                            }, 0L);
                                        }

                                        Main.plugin.getServer().getScheduler().runTask(Main.plugin, () -> {
                                            Pickup event = new Pickup(e.getPlayer(), drop.getQuantity());
                                            Bukkit.getPluginManager().callEvent(event);
                                        });

                                        Main.econ.depositPlayer(e.getPlayer(),(float) drop.getQuantity());
                                    }
                                } catch(NullPointerException err){
                                    // fix it later I guess?
                                }
                            }
                        }
                    }
                });
            }
        }


    }

    @EventHandler
    public void pickupGem(final EntityPickupItemEvent e){

        if(e.getEntity() instanceof Player){

            try{
                if(e.getItem().getItemStack().getType()==Main.settings.getGem()||e.getItem().getItemStack().getType()==Main.settings.getLargeGem()) {

                    Drop drop = new Drop(e.getItem().getItemStack());
                    if(drop.hasQuantity()){
                        e.setCancelled(true);
                        e.getItem().remove();

                        Main.plugin.getServer().getScheduler().runTask(Main.plugin, () -> {
                            Pickup event = new Pickup((Player) e.getEntity(), drop.getQuantity());
                            Bukkit.getPluginManager().callEvent(event);
                        });

                        Main.econ.depositPlayer((OfflinePlayer) e.getEntity(),(float) drop.getQuantity());
                    }

                }
            } catch(NullPointerException err){
                // fix it later I guess?
            }

        }

    }

    @EventHandler
    public void entityMating(final EntityBreedEvent e){

        Bukkit.getScheduler().runTaskAsynchronously(Main.plugin, new Runnable() {
            @Override
            public void run() {

                if(!Main.settings.disabledWorlds.contains(e.getEntity().getWorld().getName())){
                    try{
                        LivingEntity cupid = e.getBreeder();
                        if(cupid instanceof Player){
                            Drop drop = new Drop(e.getFather().getLocation(),Main.settings.getBreedingRange().getAmount(),(Player) cupid);
                            drop.spawn();
                        }
                    } catch(NullPointerException err){
                        // fix it later I guess?
                    }
                }

            }
        });

    }

    @EventHandler
    public void blockPlace(final BlockPlaceEvent e){

            Bukkit.getScheduler().runTaskAsynchronously(Main.plugin, new Runnable() {
                @Override
                public void run() {

                    if(!Main.settings.disabledWorlds.contains(e.getBlockPlaced().getWorld().getName())){
                        try{
                            if(Main.settings.getBlocksHashMap().containsKey(e.getBlock().getType()) && !(Main.settings.getProducts().contains(e.getBlock().getType()) || Main.settings.getSeeds().contains(e.getBlock().getType()))){

                                // remove old placings
                                if(Main.playerPlacedIndex>=Main.settings.lengthPlaced-1){
                                    Main.playerPlacedIndex=0;
                                }

                                if(Main.playerPlaced.size()<Main.settings.lengthPlaced){
                                    Main.playerPlaced.add(e.getBlockPlaced());
                                } else {
                                    Main.playerPlaced.set(Main.playerPlacedIndex,e.getBlockPlaced());
                                }

                                Main.playerPlacedIndex++;
                            } else if(Main.expectedReplants.containsKey(e.getBlock().getLocation()) && (Main.settings.getProducts().contains(e.getBlock().getType()) || Main.settings.getSeeds().contains(e.getBlock().getType()))){

                                final Material originalType = e.getBlock().getType();
                                Bukkit.getScheduler().scheduleSyncDelayedTask(Main.plugin, new Runnable() {
                                    @Override
                                    public void run() {

                                        if(e.getBlock().getLocation().getWorld().getBlockAt(e.getBlock().getLocation()).getType() == originalType){
                                            Main.expectedReplants.remove(e.getBlock().getLocation());
                                            new Drop(e.getBlock().getLocation(),Main.settings.getHarvest(originalType).getReplant().getAmount()).spawn();
                                        }

                                    }
                                },(long) 1/2);
                            }
                        } catch (NullPointerException err){
                            // Fix it later I guess?
                        }
                    }

                }
            });

    }

    @EventHandler
    public void entitySpawn(final SpawnerSpawnEvent e){
        e.getEntity().setMetadata("Spawner",new FixedMetadataValue(Main.plugin,true));
    }

    @EventHandler
    public void playerDeath(final PlayerDeathEvent e){

        Bukkit.getScheduler().runTaskAsynchronously(Main.plugin, new Runnable() {
            @Override
            public void run() {
                if(!Main.settings.disabledWorlds.contains(e.getEntity().getWorld().getName())){
                    try{
                        if(Main.settings.isDeathEnabled()){
                            int deathPercent = Main.settings.getDeathPercent();
                            for (int i = 100; i > -1 ; i--) {
                                if(Objects.requireNonNull(e.getEntity().getPlayer()).hasPermission("gemmy.death."+i)){
                                    deathPercent=i;
                                }
                            }

                            int amountToRemove = (int) (Main.econ.getBalance(e.getEntity())*((float) deathPercent/100));
                            Main.econ.withdrawPlayer(e.getEntity(),amountToRemove);

                            Main.plugin.getServer().getScheduler().runTask(Main.plugin, () -> {
                                Death event = new Death(e.getEntity(), amountToRemove);
                                Bukkit.getPluginManager().callEvent(event);
                            });

                            Drop drop = new Drop(e.getEntity().getLocation(),amountToRemove);
                            drop.spawn();
                        }
                    } catch (NullPointerException err){
                        // Fix it later I guess?
                    }
                }
            }
        });

    }

}
