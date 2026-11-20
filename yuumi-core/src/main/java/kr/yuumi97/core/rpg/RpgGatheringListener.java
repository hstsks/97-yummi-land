package kr.yuumi97.core.rpg;

import org.bukkit.*;import org.bukkit.block.Block;import org.bukkit.block.data.Ageable;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.block.*;import org.bukkit.event.entity.EntityDeathEvent;import org.bukkit.event.player.PlayerFishEvent;import org.bukkit.inventory.ItemStack;import org.bukkit.potion.*;
import java.util.*;import java.util.concurrent.ThreadLocalRandom;

final class RpgGatheringListener implements Listener {
    private final RpgJobManager r;
    private final Set<UUID> processing=new HashSet<>();
    private final Set<String> placed=new HashSet<>();
    private final Map<BlockBreakEvent,Boolean> natural=new WeakHashMap<>();
    boolean naturalBreak(BlockBreakEvent event){return natural.getOrDefault(event,false);}
    RpgGatheringListener(RpgJobManager r){this.r=r;placed.addAll(r.data.getStringList("rpg_placed_resources"));}
    private String key(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    void stop(){r.data.set("rpg_placed_resources",new ArrayList<>(placed));}
    private boolean ore(Material m){return m.name().endsWith("_ORE")||m==Material.ANCIENT_DEBRIS;}
    private boolean log(Material m){return Tag.LOGS.isTagged(m);}
    private boolean mature(Block b){return switch(b.getType()){case WHEAT,CARROTS,POTATOES,BEETROOTS,NETHER_WART,COCOA,SWEET_BERRY_BUSH,TORCHFLOWER_CROP,PITCHER_CROP -> b.getBlockData() instanceof Ageable a&&a.getAge()==a.getMaximumAge();default->false;};}
    private boolean eligible(Player p,Block b){return p.getGameMode()==GameMode.SURVIVAL&&(!b.getWorld().getName().equals("yuumi_hub")||r.plots.canBuildInHub(p,b.getLocation()))&&!b.getWorld().getName().equals("yuumi_dungeon");}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(ore(e.getBlock().getType())||log(e.getBlock().getType())){placed.add(key(e.getBlock()));r.data.set("rpg_placed_resources",new ArrayList<>(placed));r.changed();}}
    public double specialtyChance(Player p,double base){return Math.clamp(base*(1+r.perk(p,RpgDomain.LIFE,"luck")*r.cfg("rpg.perks.luck.per-rank",.05))*(r.lifeLevel(p)>=15?1.10:1)*(1+r.getWorkshop().feastEffect(p,"HARVEST","drop",.15))*(r.holdingLife(p)&&r.getSignatures().star(p.getInventory().getItemInMainHand())>=5?1.15:1),0,1);}
    private void roll(Player p,String id,double chance){if(ThreadLocalRandom.current().nextDouble()<specialtyChance(p,chance))r.deliver(p,r.items.create(id,id.equals("golden_catnip")?r.lifeSkills().cropMultiplier(p):1));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void kill(EntityDeathEvent e){if(!r.isTrainingDummy(e.getEntity())&&e.getEntity().getKiller()!=null&&(e.getEntity() instanceof Monster||r.taggedEnemy(e.getEntity())))r.damage().killed(e.getEntity().getKiller(),e.getEntity());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fish(PlayerFishEvent e){Player p=e.getPlayer();if(p.getGameMode()!=GameMode.SURVIVAL)return;
        if(e.getState()==PlayerFishEvent.State.FISHING){r.lifeSkills().beginFishing(p);FishHook h=e.getHook();double multiplier=r.lifeSkills().waitMultiplier(p);h.setMinWaitTime(Math.max(1,(int)(h.getMinWaitTime()*multiplier)));h.setMaxWaitTime(Math.max(h.getMinWaitTime(),(int)(h.getMaxWaitTime()*multiplier)));}
        if(e.getState()==PlayerFishEvent.State.BITE)r.lifeSkills().biteFishing(p);
        if(e.getState()==PlayerFishEvent.State.REEL_IN||e.getState()==PlayerFishEvent.State.FAILED_ATTEMPT||e.getState()==PlayerFishEvent.State.CAUGHT_ENTITY||e.getState()==PlayerFishEvent.State.IN_GROUND)r.lifeSkills().endFishing(p);
        if(e.getState()!=PlayerFishEvent.State.CAUGHT_FISH||!(e.getCaught() instanceof Item item))return;
        boolean pro=r.getLife(p)==LifeProfession.ANGLER;roll(p,"deep_sea_fillet",r.cfg("rpg.drops.deep_sea_fillet."+(pro?"specialist":"base"),pro?.10:.025));
        if(r.lifeSkills().doubleFish(p)||pro&&ThreadLocalRandom.current().nextDouble()<(20+r.lifeLevel(p)*2)/100.0)r.deliver(p,item.getItemStack().clone());
        if(r.lifeSkills().fishingWeight(p)>=2&&ThreadLocalRandom.current().nextDouble()<.08)r.deliver(p,r.items.ancientCatCoin(1));r.lifeSkills().endFishing(p);r.awardLifeXp(p,"fishing",(int)r.cfg("rpg.xp.fish",24));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){Player p=e.getPlayer();Block b=e.getBlock();if(!eligible(p,b))return;
        boolean artificial=placed.remove(key(b));natural.put(e,!artificial);if(artificial){r.data.set("rpg_placed_resources",new ArrayList<>(placed));r.changed();}
        Material type=b.getType();boolean crop=mature(b);LifeProfession job=r.getLife(p);
        if(ore(type)&&!artificial){r.awardLifeXp(p,"mining",(int)r.cfg("rpg.xp.ore",6));if(type.name().startsWith("DEEPSLATE_")||type==Material.ANCIENT_DEBRIS)roll(p,"unidentified_geode",r.cfg("rpg.drops.unidentified_geode."+(job==LifeProfession.MINER?"specialist":"base"),job==LifeProfession.MINER?.05:.012)*r.lifeSkills().geodeMultiplier(p));}
        if(crop){r.awardLifeXp(p,"farming",(int)r.cfg("rpg.xp.crop",2));roll(p,"golden_catnip",r.cfg("rpg.drops.golden_catnip."+(job==LifeProfession.FARMER?"specialist":"base"),job==LifeProfession.FARMER?.04:.01));}
        if(log(type)&&!artificial)r.awardLifeXp(p,"wood",(int)r.cfg("rpg.xp.log",3));
        if(processing.contains(p.getUniqueId())||!r.holdingLife(p))return;
        Location location=b.getLocation();
        if(job==LifeProfession.MINER&&ore(type)&&!artificial){int cap=RpgRules.oreChainCap(r.lifeLevel(p),r.getSignatures().star(p.getInventory().getItemInMainHand()),0)+(int)(r.perk(p,RpgDomain.LIFE,"chain")*r.cfg("rpg.perks.chain.ore-per-rank",1));Bukkit.getScheduler().runTask(r.plugin,()->chain(p,location,type,cap,false));}
        if(job==LifeProfession.CARPENTER&&log(type)&&!artificial&&treeCanopy(b)){Bukkit.getScheduler().runTask(r.plugin,()->{chain(p,location,type,Math.min(48,16+r.lifeLevel(p)/2+(r.getSignatures().star(p.getInventory().getItemInMainHand())>=3?2:0)+(int)(r.cfg("rpg.perks.chain.log-per-rank",3)*r.perk(p,RpgDomain.LIFE,"chain"))),true);Block base=location.getBlock();Material sapling=sapling(type);if(base.getType().isAir()&&sapling!=null&&base.getRelative(0,-1,0).getType().isSolid())base.setType(sapling);});}
        if(job==LifeProfession.FARMER&&crop)Bukkit.getScheduler().runTask(r.plugin,()->harvest(p,location.getBlock(),type));
        if(job==LifeProfession.MINER&&r.lifeSkills().excavation(p)&&(type==Material.STONE||type==Material.DEEPSLATE||type==Material.NETHERRACK))Bukkit.getScheduler().runTask(r.plugin,()->excavate(p,location,type));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void smelt(BlockDropItemEvent e){Player p=e.getPlayer();if(r.getLife(p)!=LifeProfession.MINER||!r.holdingLife(p))return;
        if(ThreadLocalRandom.current().nextDouble()>=(20+r.lifeLevel(p)*2)/100.0)return;
        for(Item item:e.getItems()){ItemStack s=item.getItemStack();Material m=switch(s.getType()){case RAW_IRON->Material.IRON_INGOT;case RAW_GOLD->Material.GOLD_INGOT;case RAW_COPPER->Material.COPPER_INGOT;default->null;};if(m!=null){s.setType(m);item.setItemStack(s);}}}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void cropDrops(BlockDropItemEvent e){Player p=e.getPlayer();if(r.getLife(p)!=LifeProfession.FARMER||!r.holdingLife(p))return;
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
        ArrayDeque<Block> queue=new ArrayDeque<>();Set<String> seen=new HashSet<>();queue.add(origin.getBlock());int broken=1;double limit=tree?10:8;
        while(!queue.isEmpty()&&broken<cap){Block b=queue.removeFirst();for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dy==0&&dz==0)continue;if(!tree&&Math.abs(dx)+Math.abs(dy)+Math.abs(dz)!=1)continue;Block n=b.getRelative(dx,dy,dz);if(!seen.add(key(n))||n.getType()!=type||placed.contains(key(n))||n.getLocation().distanceSquared(origin)>limit*limit||!eligible(p,n))continue;
            if(broken>=cap)return;if(p.breakBlock(n)){broken++;queue.add(n);}}
        }}finally{processing.remove(p.getUniqueId());}}
    int harvest(Player p,Block center,Material original){if(!r.holdingLife(p)||r.getLife(p)!=LifeProfession.FARMER)return 0;int radius=r.lifeLevel(p)>=10?2:1,bonus=(int)(r.perk(p,RpgDomain.LIFE,"chain")*r.cfg("rpg.perks.chain.crop-per-rank",2))+(r.getSignatures().star(p.getInventory().getItemInMainHand())>=3?2:0),broken=0;
        processing.add(p.getUniqueId());try{for(int dx=-radius-1;dx<=radius+1;dx++)for(int dz=-radius-1;dz<=radius+1;dz++){boolean fringe=Math.abs(dx)>radius||Math.abs(dz)>radius;if(fringe&&bonus<=0)continue;Block b=center.getRelative(dx,0,dz);if(mature(b)&&eligible(p,b)&&p.breakBlock(b)){broken++;if(fringe)bonus--;}}
        }finally{processing.remove(p.getUniqueId());}return broken;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void collect(BlockDropItemEvent e){Player p=e.getPlayer();if(!eligible(p,e.getBlock())||!r.holdingLife(p))return;boolean crop=e.getBlockState().getBlockData() instanceof Ageable age&&age.getAge()==age.getMaximumAge();boolean direct=r.getLife(p)==LifeProfession.MINER&&r.lifeLevel(p)>=5||r.getLife(p)==LifeProfession.FARMER&&crop;int rank=r.perk(p,RpgDomain.LIFE,"magnet");boolean magnet=direct||rank>0;
        Iterator<Item> it=e.getItems().iterator();while(it.hasNext()){Item item=it.next();ItemStack stack=item.getItemStack();if(crop&&r.getLife(p)==LifeProfession.FARMER)stack.setAmount(Math.min(stack.getMaxStackSize(),stack.getAmount()*r.lifeSkills().cropMultiplier(p)));if(r.getLife(p)==LifeProfession.MINER&&List.of(Material.DIAMOND,Material.EMERALD,Material.LAPIS_LAZULI,Material.REDSTONE,Material.QUARTZ).contains(stack.getType())&&ThreadLocalRandom.current().nextDouble()<r.lifeSkills().gemChance(p))stack.setAmount(Math.min(stack.getMaxStackSize(),stack.getAmount()+1));if(magnet&&(direct||p.getLocation().distanceSquared(item.getLocation())<=Math.pow(1.5+rank*r.cfg("rpg.perks.magnet.radius-per-rank",.5),2))){var left=p.getInventory().addItem(stack);if(left.isEmpty()){it.remove();item.remove();}else{item.setItemStack(left.values().iterator().next());item.teleport(p.getLocation());}}else item.setItemStack(stack);}}
    boolean fellNearby(Player p,int cap){Block target=p.getTargetBlockExact(8);if(target==null||!log(target.getType())||placed.contains(key(target))||!treeCanopy(target)||!eligible(p,target))return false;Material type=target.getType();Location origin=target.getLocation();processing.add(p.getUniqueId());try{if(!p.breakBlock(target))return false;chain(p,origin,type,Math.min(48,cap),true);}finally{processing.remove(p.getUniqueId());}Material sapling=sapling(type);if(sapling!=null&&origin.getBlock().getType().isAir()&&origin.getBlock().getRelative(0,-1,0).getType().isSolid())origin.getBlock().setType(sapling);r.deliver(p,new ItemStack(Material.STICK,4));return true;}
    private Material seed(Material original){return switch(original){case WHEAT->Material.WHEAT_SEEDS;case BEETROOTS->Material.BEETROOT_SEEDS;case CARROTS->Material.CARROT;case POTATOES->Material.POTATO;case NETHER_WART->Material.NETHER_WART;case COCOA->Material.COCOA_BEANS;case SWEET_BERRY_BUSH->Material.SWEET_BERRIES;case TORCHFLOWER_CROP->Material.TORCHFLOWER_SEEDS;case PITCHER_CROP->Material.PITCHER_POD;default->null;};}
    private void replant(Player p,Block b,Material original){Material seed=seed(original);if(seed==null)return;
        ItemStack[] contents=p.getInventory().getStorageContents();for(ItemStack s:contents)if(s!=null&&s.getType()==seed&&r.items.getCustomId(s)==null){s.setAmount(s.getAmount()-1);b.setType(original,false);return;}}
    private void excavate(Player p,Location origin,Material ignored){processing.add(p.getUniqueId());try{boolean ns=Math.abs(p.getLocation().getDirection().getZ())>=Math.abs(p.getLocation().getDirection().getX());for(int side=-1;side<=1;side++)for(int y=-1;y<=1;y++){
        Block b=origin.clone().add(ns?side:0,y,ns?0:side).getBlock();if((b.getType()==Material.STONE||b.getType()==Material.DEEPSLATE||b.getType()==Material.NETHERRACK)&&eligible(p,b))p.breakBlock(b);}}
        finally{processing.remove(p.getUniqueId());}}
}
