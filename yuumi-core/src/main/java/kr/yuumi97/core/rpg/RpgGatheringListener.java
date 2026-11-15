package kr.yuumi97.core.rpg;

import org.bukkit.*;import org.bukkit.block.Block;import org.bukkit.block.data.Ageable;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.block.*;import org.bukkit.event.entity.EntityDeathEvent;import org.bukkit.event.player.PlayerFishEvent;import org.bukkit.inventory.ItemStack;import org.bukkit.potion.*;
import java.util.*;import java.util.concurrent.ThreadLocalRandom;

final class RpgGatheringListener implements Listener {
    private final RpgJobManager r;
    private final Set<UUID> processing=new HashSet<>();
    private final Set<String> placed=new HashSet<>();
    RpgGatheringListener(RpgJobManager r){this.r=r;placed.addAll(r.data.getStringList("rpg_placed_resources"));}
    private String key(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    void stop(){r.data.set("rpg_placed_resources",new ArrayList<>(placed));}
    private boolean ore(Material m){return m.name().endsWith("_ORE")||m==Material.ANCIENT_DEBRIS;}
    private boolean log(Material m){return Tag.LOGS.isTagged(m);}
    private boolean mature(Block b){return switch(b.getType()){case WHEAT,CARROTS,POTATOES,BEETROOTS,NETHER_WART,COCOA,SWEET_BERRY_BUSH,TORCHFLOWER_CROP,PITCHER_CROP -> b.getBlockData() instanceof Ageable a&&a.getAge()==a.getMaximumAge();default->false;};}
    private boolean eligible(Player p,Block b){return p.getGameMode()==GameMode.SURVIVAL&&(!b.getWorld().getName().equals("yuumi_hub")||r.plots.canBuildInHub(p,b.getLocation()))&&!b.getWorld().getName().equals("yuumi_dungeon");}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(ore(e.getBlock().getType())||log(e.getBlock().getType())){placed.add(key(e.getBlock()));r.data.set("rpg_placed_resources",new ArrayList<>(placed));r.changed();}}
    public double specialtyChance(Player p,double base){return RpgRules.specialtyChance(base,r.perk(p,"efficiency"),r.getWorkshop().feastEffect(p,"HARVEST","drop",.15));}
    private void roll(Player p,String id,double chance){if(ThreadLocalRandom.current().nextDouble()<specialtyChance(p,chance))r.deliver(p,r.items.create(id));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void kill(EntityDeathEvent e){if(r.isTrainingDummy(e.getEntity())||!(e.getEntity() instanceof Monster)||e.getEntity().getKiller()==null)return;Player p=e.getEntity().getKiller();if(p.getGameMode()!=GameMode.SURVIVAL)return;
        JobType j=r.getJob(p);roll(p,"monster_soul_core",r.cfg("rpg.drops.monster_soul_core."+(j!=null&&j.combat()?"specialist":"base"),j!=null&&j.combat()?.14:.04));r.activityXp(p,"combat",(int)r.cfg("rpg.xp.kill",12));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fish(PlayerFishEvent e){Player p=e.getPlayer();if(p.getGameMode()!=GameMode.SURVIVAL)return;
        if(e.getState()==PlayerFishEvent.State.FISHING&&r.getJob(p)==JobType.ANGLER){FishHook h=e.getHook();h.setMinWaitTime(Math.max(1,(int)(h.getMinWaitTime()*.60)));h.setMaxWaitTime(Math.max(h.getMinWaitTime(),(int)(h.getMaxWaitTime()*.60)));}
        if(e.getState()!=PlayerFishEvent.State.CAUGHT_FISH||!(e.getCaught() instanceof Item item))return;
        boolean pro=r.getJob(p)==JobType.ANGLER;roll(p,"deep_sea_fillet",r.cfg("rpg.drops.deep_sea_fillet."+(pro?"specialist":"base"),pro?.10:.025));
        if(pro&&ThreadLocalRandom.current().nextDouble()<(20+r.getLevel(p)*2)/100.0)r.deliver(p,item.getItemStack().clone());
        r.activityXp(p,"fishing",(int)r.cfg("rpg.xp.fish",24));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){Player p=e.getPlayer();Block b=e.getBlock();if(!eligible(p,b))return;
        boolean artificial=placed.remove(key(b));if(artificial){r.data.set("rpg_placed_resources",new ArrayList<>(placed));r.changed();}
        Material type=b.getType();boolean crop=mature(b);JobType job=r.getJob(p);
        if(ore(type)&&!artificial){r.activityXp(p,"mining",(int)r.cfg("rpg.xp.ore",6));if(type.name().startsWith("DEEPSLATE_")||type==Material.ANCIENT_DEBRIS)roll(p,"unidentified_geode",r.cfg("rpg.drops.unidentified_geode."+(job==JobType.MINER?"specialist":"base"),job==JobType.MINER?.05:.012));}
        if(crop){r.activityXp(p,"farming",(int)r.cfg("rpg.xp.crop",2));roll(p,"golden_catnip",r.cfg("rpg.drops.golden_catnip."+(job==JobType.FARMER?"specialist":"base"),job==JobType.FARMER?.04:.01));}
        if(log(type)&&!artificial)r.activityXp(p,"wood",(int)r.cfg("rpg.xp.log",3));
        if(processing.contains(p.getUniqueId())||!r.holdingSignature(p))return;
        if(b.getWorld().getName().equals("yuumi_hub")&&job!=JobType.FARMER)return;
        Location location=b.getLocation();
        if(job==JobType.MINER&&ore(type)&&!artificial){int cap=Math.min(10,4+r.getLevel(p)/3);Bukkit.getScheduler().runTask(r.plugin,()->chain(p,location,type,cap,false));}
        if(job==JobType.CARPENTER&&log(type)&&!artificial&&treeCanopy(b)){Bukkit.getScheduler().runTask(r.plugin,()->{chain(p,location,type,16,true);Block base=location.getBlock();Material sapling=sapling(type);if(base.getType().isAir()&&sapling!=null&&base.getRelative(0,-1,0).getType().isSolid())base.setType(sapling);});}
        if(job==JobType.FARMER&&crop)Bukkit.getScheduler().runTask(r.plugin,()->harvest(p,location.getBlock(),type));
        if(job==JobType.MINER&&r.getCombat().buff(p,"excavation")&&(type==Material.STONE||type==Material.DEEPSLATE||type==Material.NETHERRACK))Bukkit.getScheduler().runTask(r.plugin,()->excavate(p,location,type));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void smelt(BlockDropItemEvent e){Player p=e.getPlayer();if(r.getJob(p)!=JobType.MINER||!r.holdingSignature(p))return;
        if(ThreadLocalRandom.current().nextDouble()>=(20+r.getLevel(p)*2)/100.0)return;
        for(Item item:e.getItems()){ItemStack s=item.getItemStack();Material m=switch(s.getType()){case RAW_IRON->Material.IRON_INGOT;case RAW_GOLD->Material.GOLD_INGOT;case RAW_COPPER->Material.COPPER_INGOT;default->null;};if(m!=null){s.setType(m);item.setItemStack(s);}}}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void cropDrops(BlockDropItemEvent e){Player p=e.getPlayer();if(r.getJob(p)!=JobType.FARMER||!r.holdingSignature(p))return;
        Material original=e.getBlockState().getType(),seed=seed(original);if(seed==null||!(e.getBlockState().getBlockData() instanceof Ageable age)||age.getAge()!=age.getMaximumAge())return;
        Block block=e.getBlock();if(!eligible(p,block))return;boolean reserved=false;
        Iterator<Item> iterator=e.getItems().iterator();while(iterator.hasNext()){Item item=iterator.next();ItemStack stack=item.getItemStack();if(stack.getType()==seed&&r.items.getCustomId(stack)==null){
            if(stack.getAmount()==1){iterator.remove();item.remove();}else{stack.setAmount(stack.getAmount()-1);item.setItemStack(stack);}reserved=true;break;}}
        boolean fromDrop=reserved;Bukkit.getScheduler().runTask(r.plugin,()->{if(!block.getType().isAir()||!eligible(p,block))return;
            if(fromDrop)block.setBlockData(e.getBlockState().getBlockData().clone(),false);else replant(p,block,original);
            if(block.getBlockData() instanceof Ageable planted){planted.setAge(0);block.setBlockData(planted,false);}});}
    private boolean treeCanopy(Block b){for(int y=1;y<=8;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(Tag.LEAVES.isTagged(b.getRelative(x,y,z).getType()))return true;return false;}
    private Material sapling(Material log){String s=log.name().replace("_LOG","_SAPLING");return Material.matchMaterial(s);}
    private void chain(Player p,Location origin,Material type,int cap,boolean tree){if(!p.isOnline())return;processing.add(p.getUniqueId());try{
        ArrayDeque<Block> queue=new ArrayDeque<>();Set<String> seen=new HashSet<>();queue.add(origin.getBlock());int broken=1;double limit=(tree?8:6)+r.perk(p,"power")*.5;
        while(!queue.isEmpty()&&broken<cap){Block b=queue.removeFirst();for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dy==0&&dz==0)continue;if(!tree&&Math.abs(dx)+Math.abs(dy)+Math.abs(dz)!=1)continue;Block n=b.getRelative(dx,dy,dz);if(!seen.add(key(n))||n.getType()!=type||placed.contains(key(n))||n.getLocation().distanceSquared(origin)>limit*limit||!eligible(p,n))continue;
            if(broken>=cap)return;if(p.breakBlock(n)){broken++;queue.add(n);}}
        }}finally{processing.remove(p.getUniqueId());}}
    void harvest(Player p,Block center,Material original){if(!r.holdingSignature(p)||r.getJob(p)!=JobType.FARMER)return;int radius=(r.getLevel(p)>=10&&r.getAwakening(p).equals("A")?2:1)+r.perk(p,"power")/5;
        processing.add(p.getUniqueId());try{for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){Block b=center.getRelative(dx,0,dz);if(mature(b)&&eligible(p,b))harvestOne(p,b);}
        }finally{processing.remove(p.getUniqueId());}}
    private void harvestOne(Player p,Block b){p.breakBlock(b);}
    private Material seed(Material original){return switch(original){case WHEAT->Material.WHEAT_SEEDS;case BEETROOTS->Material.BEETROOT_SEEDS;case CARROTS->Material.CARROT;case POTATOES->Material.POTATO;case NETHER_WART->Material.NETHER_WART;case COCOA->Material.COCOA_BEANS;case SWEET_BERRY_BUSH->Material.SWEET_BERRIES;case TORCHFLOWER_CROP->Material.TORCHFLOWER_SEEDS;case PITCHER_CROP->Material.PITCHER_POD;default->null;};}
    private void replant(Player p,Block b,Material original){Material seed=seed(original);if(seed==null)return;
        ItemStack[] contents=p.getInventory().getStorageContents();for(ItemStack s:contents)if(s!=null&&s.getType()==seed&&r.items.getCustomId(s)==null){s.setAmount(s.getAmount()-1);b.setType(original,false);return;}}
    private void excavate(Player p,Location origin,Material ignored){processing.add(p.getUniqueId());try{boolean ns=Math.abs(p.getLocation().getDirection().getZ())>=Math.abs(p.getLocation().getDirection().getX());for(int side=-1;side<=1;side++)for(int y=-1;y<=1;y++){
        Block b=origin.clone().add(ns?side:0,y,ns?0:side).getBlock();if((b.getType()==Material.STONE||b.getType()==Material.DEEPSLATE||b.getType()==Material.NETHERRACK)&&eligible(p,b))p.breakBlock(b);}}
        finally{processing.remove(p.getUniqueId());}}
}
