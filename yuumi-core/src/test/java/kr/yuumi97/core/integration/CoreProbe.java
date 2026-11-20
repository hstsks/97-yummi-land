package kr.yuumi97.core.integration;

import com.google.gson.Gson;
import kr.yuumi97.core.YuumiPlugin;
import kr.yuumi97.core.guide.HubTravelManager.Destination;
import kr.yuumi97.core.rpg.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Test-only companion plugin; cannot enable outside the disposable server. */
public final class CoreProbe extends JavaPlugin implements CommandExecutor {
    private YuumiPlugin core;
    private final Gson json=new Gson();
    @Override public void onEnable() {
        var root=getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        if(!root.endsWith(java.nio.file.Path.of("yuumi-core","build","server-integration")))
            throw new IllegalStateException("CoreProbe is restricted to the disposable integration server");
        core=(YuumiPlugin)getServer().getPluginManager().getPlugin("YuumiLandCore");
        Objects.requireNonNull(getCommand("coreprobe")).setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)||!sender.isOp()||args.length==0)return true;
        try {
            switch(args[0]) {
                case "profile" -> {
                    String combat=args[1],life=args[2];int level=Integer.parseInt(args[3]);String branch=args.length>4?args[4]:"A";
                    CombatClass.valueOf(combat);LifeProfession.valueOf(life);require(level>=1&&level<=20,"level");
                    core.rpg().combatSkills().clear(p,RpgDomain.COMBAT);core.rpg().lifeSkills().clear(p,RpgDomain.LIFE);
                    String root="rpg."+p.getUniqueId();core.progression().set(root+".active_combat",combat);core.progression().set(root+".active_life",life);
                    for(String domain:List.of("combat","life")){String path=root+"."+domain+"."+(domain.equals("combat")?combat:life);
                        core.progression().set(path+".level",level);core.progression().set(path+".cooldowns",null);
                        core.progression().set(path+(domain.equals("combat")?".awakening":".spec"),branch.equals("NONE")?"":branch);}
                    core.rpg().getSignatures().refreshInventory(p);core.saveDataFile();reply(p,"profile",status(p));
                }
                case "status" -> reply(p,"status",status(p));
                case "models" -> reply(p,"models",Map.of("arcane",String.valueOf(core.items().create("weapon_arcane_orb_tome").getItemMeta().getItemModel())));
                case "dummyhits" -> {
                    List<Map<String,Object>> values=new ArrayList<>();
                    for(LivingEntity e:p.getWorld().getLivingEntities())if(e.getPersistentDataContainer().has(new NamespacedKey(core,"yuumi_training_dummy"))){
                        var ray=p.getWorld().rayTraceBlocks(p.getEyeLocation(),e.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize(),p.getEyeLocation().distance(e.getEyeLocation()),FluidCollisionMode.NEVER,true);
                        values.add(Map.of("index",e.getPersistentDataContainer().getOrDefault(new NamespacedKey(core,"yuumi_training_dummy_index"),PersistentDataType.INTEGER,-1),
                                "sequence",e.getPersistentDataContainer().getOrDefault(new NamespacedKey(core,"yuumi_training_hit_sequence"),PersistentDataType.LONG,0L),"los",p.hasLineOfSight(e),"blocked",ray==null?"none":ray.getHitBlock().getType()+" "+ray.getHitBlock().getLocation(),"distance",p.getLocation().distance(e.getLocation()),"direction",List.of(p.getLocation().getDirection().getX(),p.getLocation().getDirection().getZ())));
                    } reply(p,"dummyhits",values);
                }
                case "inventory" -> reply(p,"inventory",inventory(p));
                case "reset" -> {
                    for(RpgDomain d:RpgDomain.values())core.progression().set(core.rpg().path(p,d)+".cooldowns",null);
                    p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());p.setFoodLevel(20);p.setFireTicks(0);
                    p.getActivePotionEffects().forEach(effect->p.removePotionEffect(effect.getType()));reply(p,"reset",true);
                }
                case "books" -> {
                    p.getInventory().clear();ItemStack old=core.items().create("guidebook");old.editMeta(m->m.getPersistentDataContainer().set(core.items().getItemIdKey(),PersistentDataType.STRING,"book_of_thresholds"));old.setAmount(2);
                    p.getInventory().setItem(0,old);p.getInventory().setItem(1,core.items().create("guidebook"));p.setItemOnCursor(core.items().create("guidebook"));
                    core.guidebook().normalize(p);reply(p,"books",inventory(p));
                }
                case "metadata" -> {metadata(p);reply(p,"metadata",true);}
                case "homecheck" -> reply(p,"homecheck",homecheck(p));
                case "outside" -> {World w=Bukkit.getWorld("world");Location at=w.getSpawnLocation();at.setY(w.getHighestBlockYAt(at)+1);p.teleport(at);reply(p,"outside",p.getWorld().getName());}
                case "warps" -> {
                    Map<String,Object> result=new LinkedHashMap<>();
                    for(Destination d:Destination.values()){Location wanted=core.travel().location(p,d),safe=core.travel().safeLanding(wanted);require(safe!=null,"unsafe landing "+d);result.put(d.name(),List.of(safe.getX(),safe.getY(),safe.getZ()));}
                    reply(p,"warps",result);
                }
                case "sustain" -> {p.setInvulnerable(Boolean.parseBoolean(args[1]));reply(p,"sustain",p.isInvulnerable());}
                case "damage" -> {
                    Entity e=Bukkit.getEntity(UUID.fromString(args[1]));require(e instanceof LivingEntity,"living target");
                    ((LivingEntity)e).setNoDamageTicks(0);((LivingEntity)e).damage(Double.parseDouble(args[2]),p);reply(p,"damage",e.isValid());
                }
                case "rpgworldcheck" -> { var future=(java.util.concurrent.CompletableFuture<?>)Class.forName("kr.yuumi97.core.integration.RpgWorldCheck").getMethod("run",YuumiPlugin.class,Player.class).invoke(null,core,p);future.whenComplete((evidence,failure) -> {if(failure!=null){getLogger().log(java.util.logging.Level.SEVERE,"CORE_RPG_WORLD_CHECK FAILED",failure);reply(p,"failure",failure.toString());}else reply(p,"rpgworldcheck",evidence);}); }
                case "scenariocheck" -> { var future=(java.util.concurrent.CompletableFuture<?>)Class.forName("kr.yuumi97.core.integration.DungeonScenarioCheck").getMethod("run",YuumiPlugin.class,Player.class).invoke(null,core,p);future.whenComplete((evidence,failure) -> {if(failure!=null){getLogger().log(java.util.logging.Level.SEVERE,"CORE_SCENARIO_CHECK FAILED",failure);reply(p,"failure",failure.toString());}else reply(p,"scenariocheck",evidence);}); }
                case "combatcheck", "lifepolishcheck", "vfxcheck" -> {
                    String helper=switch(args[0]){case "combatcheck"->"RpgCombatCheck";case "lifepolishcheck"->"LifePolishCheck";default->"VfxWorldCheck";};
                    String kind=args[0];var future=(java.util.concurrent.CompletableFuture<?>)Class.forName("kr.yuumi97.core.integration."+helper).getMethod("run",YuumiPlugin.class,Player.class).invoke(null,core,p);
                    future.whenComplete((evidence,failure)->{if(failure!=null){getLogger().log(java.util.logging.Level.SEVERE,"CORE_POLISH_CHECK FAILED "+kind,failure);reply(p,"failure",failure.toString());}else reply(p,kind,evidence);});
                }
                case "worldcheck" -> DungeonWorldCheck.run(core,p).whenComplete((evidence,failure) -> { if(failure!=null){getLogger().log(java.util.logging.Level.SEVERE,"CORE_WORLD_CHECK FAILED",failure);reply(p,"failure",failure.toString());}else reply(p,"worldcheck",evidence); });
                case "snapshot" -> {
                    Object state=core.dungeon().getClass().getMethod("snapshot",UUID.class).invoke(core.dungeon(),p.getUniqueId());
                    Object mobs=core.dungeon().getClass().getMethod("mobSnapshots",UUID.class).invoke(core.dungeon(),p.getUniqueId());
                    reply(p,"snapshot",Map.of("run",state,"mobs",mobs));
                }
                case "entitycounts" -> reply(p,"entitycounts",Map.of("hub",core.hub().getHubWorld().getEntityCount(),"dungeon",Bukkit.getWorld("yuumi_dungeon").getEntityCount()));
                default -> throw new IllegalArgumentException("Unknown probe action");
            }
        } catch(Throwable failure){getLogger().log(java.util.logging.Level.SEVERE,"CORE_PROBE FAILED "+args[0],failure);reply(p,"failure",failure.toString());}
        return true;
    }
    private Map<String,Object> status(Player p) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("combat",String.valueOf(core.rpg().getCombat(p)));out.put("life",String.valueOf(core.rpg().getLife(p)));
        out.put("mode",p.getGameMode().name());out.put("combatXp",core.progression().getInt(core.rpg().path(p,RpgDomain.COMBAT)+".xp"));
        var pending=core.progression().getConfigurationSection("rewards."+p.getUniqueId());out.put("pendingRewards",pending==null?0:pending.getKeys(false).size());out.put("clears",core.dungeon().getClearCount(p.getUniqueId()));
        out.put("levelCombat",core.rpg().combatLevel(p));out.put("levelLife",core.rpg().lifeLevel(p));out.put("health",p.getHealth());out.put("world",p.getWorld().getName());
        out.put("position",List.of(p.getX(),p.getY(),p.getZ()));out.put("held",String.valueOf(core.items().getCustomId(p.getInventory().getItemInMainHand())));
        var cc=core.progression().getConfigurationSection(core.rpg().path(p,RpgDomain.COMBAT)+".cooldowns");out.put("combatCooldowns",cc==null?Map.of():cc.getValues(false));
        var lc=core.progression().getConfigurationSection(core.rpg().path(p,RpgDomain.LIFE)+".cooldowns");out.put("lifeCooldowns",lc==null?Map.of():lc.getValues(false));return out;
    }
    private List<Map<String,Object>> inventory(Player p) {
        List<Map<String,Object>> out=new ArrayList<>();
        for(int slot=0;slot<p.getInventory().getSize();slot++){ItemStack item=p.getInventory().getItem(slot);if(item!=null&&!item.getType().isAir())out.add(Map.of("slot",slot,"id",String.valueOf(core.items().getCustomId(item)),"amount",item.getAmount(),"material",item.getType().name()));}
        ItemStack cursor=p.getItemOnCursor();if(!cursor.getType().isAir())out.add(Map.of("slot",-1,"id",String.valueOf(core.items().getCustomId(cursor)),"amount",cursor.getAmount()));return out;
    }
    private Map<String,Object> homecheck(Player p) {
        var plot=core.plots().getAllPlots().values().stream().filter(spec->core.plots().getOwner(spec.id())==null).findFirst().orElseThrow();
        String key="plots."+plot.id()+".owner",trust="trust."+UUID.nameUUIDFromBytes("core-home-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Object priorOwner=core.progression().get(key),priorTrust=core.progression().get(trust);Location before=p.getLocation();
        try {
            core.progression().set(key,p.getUniqueId().toString());Location own=core.plots().getHomeEntranceOrNull(p.getUniqueId());
            require(core.travel().warp(p,Destination.HOME)&&p.getLocation().distanceSquared(own)<10,"owned plot landing");
            core.progression().set(key,trust.substring(6));core.progression().set(trust,List.of(p.getUniqueId().toString()));
            Location shared=core.plots().getHomeEntranceOrNull(p.getUniqueId());require(shared!=null&&core.travel().warp(p,Destination.HOME)&&p.getLocation().distanceSquared(shared)<10,"trusted plot landing");
            core.progression().set(trust,null);require(core.plots().getHomeEntranceOrNull(p.getUniqueId())==null,"revoked trust still grants home");
            return Map.of("owned",true,"shared",true,"revoked",true);
        } finally {core.progression().set(key,priorOwner);core.progression().set(trust,priorTrust);p.teleport(before);core.saveDataFile();}
    }
    private void metadata(Player p) {
        var data=core.progression();String root="rpg."+p.getUniqueId();Object combat=data.get(root+".active_combat"),life=data.get(root+".active_life");
        try {
            List<String> ids=new ArrayList<>();for(var c:CombatClass.values())ids.add(c.itemId);for(var l:LifeProfession.values())ids.add(l.itemId);
            NamespacedKey stars=new NamespacedKey(core,"starlight_level"),rune=new NamespacedKey(core,"yuumi_rune_type");
            for(String id:ids){
                for(int[] order:new int[][]{{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}}){
                    ItemStack item=core.items().create(id);item.addUnsafeEnchantment(Enchantment.UNBREAKING,3);
                    for(int operation:order){
                        if(operation==0)core.forge().applyStar(item,7);
                        else if(operation==1)core.rpg().getSignatures().refresh(item,p);
                        else require(core.rpg().getWorkshop().inscribe(item,"FROST",true),"rune application "+id);
                    }
                    require(item.getEnchantmentLevel(Enchantment.UNBREAKING)==3,"enchantment lost "+id);
                    require(item.getItemMeta().getPersistentDataContainer().get(stars,PersistentDataType.INTEGER)==7,"star lost "+id);
                    require("FROST".equals(item.getItemMeta().getPersistentDataContainer().get(rune,PersistentDataType.STRING)),"rune lost "+id);
                }
            }
        } finally {data.set(root+".active_combat",combat);data.set(root+".active_life",life);}
    }
    private void reply(Player p,String kind,Object value){
        String text=json.toJson(Map.of("kind",kind,"value",value));
        if(text.length()<2800){p.sendMessage("CORE_PROBE "+text);return;}
        String encoded=Base64.getEncoder().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token=UUID.randomUUID().toString().substring(0,8);int total=(encoded.length()+2399)/2400;
        for(int i=0;i<total;i++)p.sendMessage("CORE_PROBE_CHUNK "+token+" "+(i+1)+"/"+total+" "+encoded.substring(i*2400,Math.min(encoded.length(),(i+1)*2400)));
    }
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
