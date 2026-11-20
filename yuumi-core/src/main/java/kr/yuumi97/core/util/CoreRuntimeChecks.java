package kr.yuumi97.core.util;

import kr.yuumi97.core.hub.HubWorldManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.rpg.RpgJobManager;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import java.lang.reflect.Proxy;
import java.util.*;

/** Explicit console diagnostics: real Paper item serialization and structure placement, no live player needed. */
public final class CoreRuntimeChecks {
    private CoreRuntimeChecks(){}
    public static String run(JavaPlugin plugin,YuumiItems items,RpgJobManager rpg,HubWorldManager hub,FileConfiguration data,kr.yuumi97.core.dungeon.DungeonManager dungeon)throws Exception{
        check(new HashSet<>(YuumiItems.CUSTOM_IDS).size()==YuumiItems.CUSTOM_IDS.size(),"Unique custom IDs");
        check(kr.yuumi97.core.rpg.CombatClass.values().length==6 && kr.yuumi97.core.rpg.LifeProfession.values().length==6,"Dual domain roster");
        for(String id:YuumiItems.CUSTOM_IDS){
            ItemStack item=items.create(id);check(id.equals(items.getCustomId(item)),"PDC "+id);
            check(item.getItemMeta().hasItemModel(),"Missing item model "+id);
            if(id.startsWith("weapon_")||id.startsWith("tool_"))check(item.getMaxStackSize()==1&&item.getItemMeta().isUnbreakable(),"Signature invariants "+id);
            if(id.startsWith("hat_"))check(item.getItemMeta().getEquippable().getSlot()==EquipmentSlot.HEAD,"Hat slot "+id);
            YamlConfiguration yaml=new YamlConfiguration();yaml.set("item",item);YamlConfiguration loaded=new YamlConfiguration();loaded.loadFromString(yaml.saveToString());
            check(item.isSimilar(loaded.getItemStack("item")),"YAML item round trip "+id);
        }
        UUID id=UUID.nameUUIDFromBytes("core-runtime-check-player".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String root="rpg."+id;Object prior=data.get(root);
        try{
            Player fake=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
                return switch(method.getName()){case "getUniqueId"->id;case "getName"->"CoreCheck";case "toString"->"CoreCheck";case "hashCode"->id.hashCode();case "equals"->proxy==args[0];default->null;};
            });
            data.set(root+".active_combat","PALADIN");data.set(root+".combat.PALADIN.level",10);data.set(root+".combat.PALADIN.awakening","A");
            ItemStack signature=items.create("weapon_paladin_sword");var meta=signature.getItemMeta();meta.addEnchant(Enchantment.SHARPNESS,4,true);
            NamespacedKey star=new NamespacedKey(plugin,"starlight_level"),rune=new NamespacedKey(plugin,"yuumi_rune_type");
            meta.getPersistentDataContainer().set(star,PersistentDataType.INTEGER,6);meta.getPersistentDataContainer().set(rune,PersistentDataType.STRING,"FROST");signature.setItemMeta(meta);
            for(int level:new int[]{10,20,1}){
                data.set(root+".combat.PALADIN.level",level);rpg.getSignatures().refresh(signature,fake);
                check(signature.getEnchantmentLevel(Enchantment.SHARPNESS)==4,"Evolution lost enchantment");
                check(signature.getItemMeta().getPersistentDataContainer().get(star,PersistentDataType.INTEGER)==6,"Evolution lost forging");
                check("FROST".equals(signature.getItemMeta().getPersistentDataContainer().get(rune,PersistentDataType.STRING)),"Evolution lost rune");
            }
        }finally{data.set(root,prior);}
        // Extraction names are authoritative; enumerate the jar to catch all twenty rather than infer aliases.
        java.io.File jar=new java.io.File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        try(var zip=new java.util.zip.ZipFile(jar)){
            var entries=zip.stream().filter(e->e.getName().startsWith("structures/v3/")&&e.getName().endsWith(".nbt")).toList();
            check(entries.size()==20,"Expected 20 embedded hub structures, got "+entries.size());
            for(var entry:entries)try(var in=zip.getInputStream(entry)){check(Bukkit.getStructureManager().loadStructure(in).getSize().getBlockY()>0,"Invalid NBT "+entry.getName());}
        }
        // A chest immediately outside the NBT footprint caught the old over-wide sanitizer.
        World scratch=Bukkit.getWorld("yuumi_dungeon");check(scratch!=null,"Dungeon world missing");
        try(var in=plugin.getResource("structures/v3/jp_lamp.nbt")){
            check(in!=null,"Lamp resource missing");Structure lamp=Bukkit.getStructureManager().loadStructure(in);
            int x=-1600,y=65,z=0,dx=lamp.getSize().getBlockX(),dy=lamp.getSize().getBlockY(),dz=lamp.getSize().getBlockZ();
            List<org.bukkit.block.BlockState> before=new ArrayList<>();
            for(int bx=x;bx<=x+dx+1;bx++)for(int by=y;by<=y+dy;by++)for(int bz=z;bz<=z+dz;bz++)before.add(scratch.getBlockAt(bx,by,bz).getState());
            try{
                var chestBlock=scratch.getBlockAt(x+dx+1,y,z);chestBlock.setType(Material.CHEST,false);Chest chest=(Chest)chestBlock.getState();chest.getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,7));
                check(hub.stampStructure(scratch,"v3/jp_lamp.nbt",x,y,z,StructureRotation.NONE),"Lamp stamping failed");
                check(chestBlock.getType()==Material.CHEST&&((Chest)chestBlock.getState()).getBlockInventory().containsAtLeast(new ItemStack(Material.DIAMOND),7),"Sanitizer cleared adjacent inventory");
            }finally{for(var state:before)state.update(true,false);}
        }
        check(!data.getString("hub.blueprint_hash", "").isBlank(),"Hub blueprint incomplete");
        check(!data.contains("hub_v2")&&!data.contains("hub_v3")&&!data.contains("hub_v3_1"),"Obsolete hub flags retained");
        dungeon.verifyCore();
        check(dungeon.validateReadiness().isEmpty(),dungeon.diagnosticSummary());
        check(Bukkit.getWorld("yuumi_dungeon")!=null,"Dungeon world missing");
        if(plugin.getServer().getWorldContainer().getAbsolutePath().contains("server-integration")){
            var plot=((kr.yuumi97.core.YuumiPlugin)plugin).plots().getAllPlots().values().stream().filter(p->((kr.yuumi97.core.YuumiPlugin)plugin).plots().getOwner(p.id())==null).findFirst().orElse(null);
            if(plot!=null){
                World world=hub.getHubWorld();String ownerKey="plots."+plot.id()+".owner";List<org.bukkit.block.BlockState> before=new ArrayList<>();
                int x=plot.centerX(),z=plot.centerZ();for(int y:new int[]{20,65,120})before.add(world.getBlockAt(x,y,z).getState());
                org.bukkit.entity.ItemDisplay sentinel=null;
                try{
                    data.set(ownerKey,UUID.randomUUID().toString());
                    world.getBlockAt(x,20,z).setType(Material.DIAMOND_BLOCK,false);world.getBlockAt(x,120,z).setType(Material.EMERALD_BLOCK,false);
                    world.getBlockAt(x,65,z).setType(Material.CHEST,false);((Chest)world.getBlockAt(x,65,z).getState()).getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,7));
                    sentinel=world.spawn(new Location(world,x+.5,120,z+.5),org.bukkit.entity.ItemDisplay.class);
                    sentinel.getPersistentDataContainer().set(new NamespacedKey(plugin,"hub_3d_prop_id"),PersistentDataType.STRING,"verification_sentinel");
                    hub.buildEntireHubCity(world);
                    check(world.getBlockAt(x,20,z).getType()==Material.DIAMOND_BLOCK&&world.getBlockAt(x,120,z).getType()==Material.EMERALD_BLOCK,"Owned full-height blocks changed");
                    check(world.getBlockAt(x,65,z).getType()==Material.CHEST&&((Chest)world.getBlockAt(x,65,z).getState()).getBlockInventory().containsAtLeast(new ItemStack(Material.DIAMOND),7),"Owned inventory changed");
                    check(sentinel.isValid(),"Owned display removed by rebuild");
                    plugin.getLogger().info("CORE_OWNED_PLOT_CHECK PASS: blocks below/inside/above plot heights, chest contents and tagged display survived public rebuild");
                }finally{data.set(ownerKey,null);if(sentinel!=null)sentinel.remove();for(var state:before)state.update(true,false);((kr.yuumi97.core.YuumiPlugin)plugin).saveDataFile();}
            }
        }
        HubRuntimeChecks.run(plugin,hub,data);
        String result="CORE_RUNTIME_CHECKS PASS: item factories and YAML round trips; dual progress; signature metadata; NBT resources; owned columns; hub hash; training and dealer sightlines; "+dungeon.diagnosticSummary();
        plugin.getLogger().info(result);return result;
    }
    private static void check(boolean passed,String message){if(!passed)throw new IllegalStateException("Core verification: "+message);}
}
