package kr.yuumi97.core.housing;

import kr.yuumi97.core.casino.CasinoManager;
import kr.yuumi97.core.hub.HubWorldManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.plot.PlotManager;
import kr.yuumi97.core.plot.PlotManager.PlotSpec;
import kr.yuumi97.core.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class GachaAndFurnitureManager implements Listener {
    public static final Set<String> FURNITURE=Set.of("plushie_yuumi","furn_cat_tower","furn_kotatsu","furn_neon_sign");
    private static final class Holder implements InventoryHolder {public Inventory getInventory(){return null;}}
    private final JavaPlugin plugin;private final YuumiItems items;private final CasinoManager casino;
    private final PlotManager plots;private final HubWorldManager hub;private final FileConfiguration data;
    private final Runnable save;private final RewardMailbox mailbox;private final NamespacedKey furnitureKey,seatKey;
    private final Map<UUID,Long> lastUse=new HashMap<>();private final Set<UUID> animating=new HashSet<>();
    public GachaAndFurnitureManager(JavaPlugin plugin,YuumiItems items,CasinoManager casino,PlotManager plots,HubWorldManager hub,FileConfiguration data,Runnable save,RewardMailbox mailbox){
        this.plugin=plugin;this.items=items;this.casino=casino;this.plots=plots;this.hub=hub;this.data=data;this.save=save;this.mailbox=mailbox;
        furnitureKey=new NamespacedKey(plugin,"furniture_uuid");seatKey=new NamespacedKey(plugin,"furniture_seat");
    }
    public void start(){Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskLater(plugin,this::restore,80L);}
    public void stop(){
        World world=hub.getHubWorld();if(world!=null)for(Entity e:new ArrayList<>(world.getEntities())){
            if(e.getPersistentDataContainer().has(seatKey,PersistentDataType.STRING)){e.eject();e.remove();}
        }
        animating.clear();
    }
    public void openGacha(Player p){
        Inventory inv=Bukkit.createInventory(new Holder(),27,TextUtil.mm("<gold><bold>🎁 황금 캡슐 가챠</bold></gold>"));
        inv.setItem(11,icon(Material.HEART_OF_THE_SEA,"<gold>캡슐 1개로 뽑기"));
        inv.setItem(13,icon(Material.SUNFLOWER,"<yellow>고대 고양이 금화 3개로 뽑기"));
        inv.setItem(15,icon(Material.PAPER,"<aqua>카지노 칩 1,500으로 뽑기"));
        double[] weights=weights();double total=java.util.Arrays.stream(weights).sum();
        String[] odds=new String[weights.length];for(int i=0;i<weights.length;i++){
            String name=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(items.create(V3Rules.GACHA_IDS[i]).getItemMeta().displayName());
            odds[i]="<gray>"+name+(V3Rules.GACHA_IDS[i].equals("starlight_stone")?" 3개":"")+": "+String.format(java.util.Locale.ROOT,"%.2f%%",weights[i]*100/total);
        }
        inv.setItem(22,icon(Material.BOOK,"<white>보상 확률",odds));
        p.openInventory(inv);
    }
    private boolean ready(Player p){
        long now=System.currentTimeMillis();if(now-lastUse.getOrDefault(p.getUniqueId(),0L)<350||animating.contains(p.getUniqueId()))return false;
        lastUse.put(p.getUniqueId(),now);return true;
    }
    public static boolean isFurniture(String id){return id!=null&&FURNITURE.contains(id);}
    private int count(Player p,String id){int n=0;for(ItemStack item:p.getInventory().getStorageContents())if(items.isCustomItem(item,id))n+=item.getAmount();return n;}
    private boolean take(Player p,String id,int amount){
        if(count(p,id)<amount)return false;
        for(int i=0;i<36&&amount>0;i++){ItemStack stack=p.getInventory().getItem(i);if(!items.isCustomItem(stack,id))continue;int n=Math.min(amount,stack.getAmount());stack.setAmount(stack.getAmount()-n);amount-=n;}
        return true;
    }
    private double[] weights(){
        double[] values=new double[V3Rules.GACHA_IDS.length];
        for(int i=0;i<values.length;i++)values[i]=plugin.getConfig().getDouble("gacha.weights."+V3Rules.GACHA_IDS[i],V3Rules.GACHA_WEIGHTS[i]);
        try{V3Rules.weightedIndex(0,values);}catch(IllegalArgumentException invalid){for(int i=0;i<values.length;i++)values[i]=V3Rules.GACHA_WEIGHTS[i];}
        return values;
    }
    private void draw(Player p,int payment,boolean handheld){
        if(!ready(p))return;
        if(!handheld){Location booth=hub.getGachaLocation();if(!p.getWorld().equals(booth.getWorld())||p.getLocation().distanceSquared(booth)>144){p.closeInventory();return;}}
        double[] weights=weights();
        int index=V3Rules.weightedIndex(ThreadLocalRandom.current().nextDouble(),weights);
        boolean paid=payment==0?take(p,"gacha_capsule",1):payment==1?take(p,"ancient_cat_coin",3):casino.spendChips(p,1500);
        if(!paid){p.sendMessage(TextUtil.msg("<red>선택한 결제 재료가 부족합니다."));return;}
        String tx=UUID.randomUUID().toString();String id=V3Rules.GACHA_IDS[index];ItemStack reward=items.create(id,id.equals("starlight_stone")?3:1);
        // Commit the chosen reward before starting any visual task. Disconnect cannot reroll it.
        mailbox.enqueueOnce(p.getUniqueId(),"gacha_"+tx,reward);
        data.set("gacha.last."+p.getUniqueId(),id);save.run();p.closeInventory();animating.add(p.getUniqueId());
        Location at=p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(1.5)).add(0,1.2,0);
        ItemDisplay capsule=at.getWorld().spawn(at,ItemDisplay.class);capsule.setItemStack(items.gachaCapsule(1));capsule.setPersistent(false);
        new BukkitRunnable(){int ticks;
            public void run(){
                if(!capsule.isValid()||ticks>=30){capsule.remove();animating.remove(p.getUniqueId());if(p.isOnline()){
                    mailbox.deliver(p);p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,0.8f,1.4f);p.sendMessage(TextUtil.msg("🎁 <gold>가챠 당첨!</gold> ").append(reward.displayName()));}cancel();return;}
                ticks+=2;capsule.setRotation(ticks*24f,0);at.getWorld().spawnParticle(Particle.END_ROD,at,3,0.25,0.3,0.25,0.01);
            }
        }.runTaskTimer(plugin,0L,2L);
    }
    @EventHandler public void onGachaClick(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!e.isLeftClick()||e.isShiftClick())return;
        int payment=switch(e.getRawSlot()){case 11->0;case 13->1;case 15->2;default->-1;};if(payment>=0)draw(p,payment,false);
    }
    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH) public void onUse(PlayerInteractEvent e){
        String id=items.getCustomId(e.getItem());if(id==null)return;
        boolean right=e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK;
        if(!right)return;
        if(id.equals("gacha_capsule")){
            if(e.getClickedBlock()!=null&&e.getClickedBlock().getType().isInteractable())return;
            e.setCancelled(true);if(e.getHand()==EquipmentSlot.HAND)draw(e.getPlayer(),0,true);return;
        }
        if(!isFurniture(id))return;e.setCancelled(true);
        if(e.getHand()!=EquipmentSlot.HAND||e.getClickedBlock()==null||e.getBlockFace()!=org.bukkit.block.BlockFace.UP||!ready(e.getPlayer()))return;
        Player p=e.getPlayer();Location at=e.getClickedBlock().getLocation().add(0.5,1.01,0.5);
        PlotSpec plot=plots.getPlotAt(at);
        if(plot==null||plots.getOwner(plot.id())==null||!plots.canBuildInHub(p,at)){
            p.sendMessage(TextUtil.msg("<red>본인 또는 공유 분양지 안에만 가구를 설치할 수 있습니다."));return;}
        int height=id.equals("furn_cat_tower")?3:2;
        for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1})if(!plot.contains(at.getBlockX()+dx,at.getBlockY()+height,at.getBlockZ()+dz)){
            p.sendMessage(TextUtil.msg("<red>가구가 필지 경계를 넘습니다. 안쪽에 설치하세요."));return;}
        for(int dy=0;dy<height;dy++)if(!at.clone().add(0,dy,0).getBlock().isPassable())return;
        for(Entity entity:at.getWorld().getNearbyEntities(at,0.9,1,0.9))if(entity instanceof Interaction&&entity.getPersistentDataContainer().has(furnitureKey,PersistentDataType.STRING))return;
        UUID furniture=UUID.randomUUID();String key="housing.furniture."+furniture;
        ItemStack stored=p.getInventory().getItemInMainHand().clone();stored.setAmount(1);
        data.set(key+".model",id);data.set(key+".plot",plot.id());data.set(key+".owner",p.getUniqueId().toString());
        data.set(key+".x",at.getX());data.set(key+".y",at.getY());data.set(key+".z",at.getZ());
        data.set(key+".yaw",Math.round(p.getYaw()/45f)*45f);data.set(key+".item",stored);
        p.getInventory().getItemInMainHand().subtract(1);save.run();spawnFurniture(furniture.toString());
        p.playSound(at,Sound.BLOCK_WOOL_PLACE,0.8f,1.2f);
    }
    public void restore(){
        ConfigurationSection section=data.getConfigurationSection("housing.furniture");if(section==null)return;
        for(String id:new ArrayList<>(section.getKeys(false)))spawnFurniture(id);
    }
    private Location furnitureLocation(String id){String k="housing.furniture."+id;return new Location(hub.getHubWorld(),data.getDouble(k+".x"),data.getDouble(k+".y"),data.getDouble(k+".z"),(float)data.getDouble(k+".yaw"),0);}
    private void spawnFurniture(String id){
        String key="housing.furniture."+id;String model=data.getString(key+".model");if(!isFurniture(model))return;
        Location at=furnitureLocation(id);if(at.getWorld()==null)return;at.getChunk().load();
        // Reconcile this one record, including duplicates from a crash during an earlier spawn.
        for(Entity e:new ArrayList<>(at.getWorld().getNearbyEntities(at,4,5,4)))if(id.equals(e.getPersistentDataContainer().get(furnitureKey,PersistentDataType.STRING)))e.remove();
        String owner=data.getString(key+".owner");
        if(data.contains("rewards."+owner+".furniture_"+id)||data.contains("reward_receipts."+owner+".furniture_"+id)){
            data.set(key,null);save.run();return;
        }
        ItemDisplay display=at.getWorld().spawn(at,ItemDisplay.class);display.setItemStack(items.create(model));display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setTransformation(new Transformation(new Vector3f(),new AxisAngle4f(),new Vector3f(model.equals("furn_cat_tower")?1.35f:1.0f),new AxisAngle4f()));
        display.setPersistent(true);display.getPersistentDataContainer().set(furnitureKey,PersistentDataType.STRING,id);
        Interaction interaction=at.getWorld().spawn(at,Interaction.class);interaction.setInteractionWidth(1.8f);interaction.setInteractionHeight(model.equals("furn_cat_tower")?2.5f:1.3f);interaction.setResponsive(true);interaction.setPersistent(true);
        interaction.getPersistentDataContainer().set(furnitureKey,PersistentDataType.STRING,id);
    }
    @EventHandler public void onFurnitureRight(PlayerInteractEntityEvent e){handleFurnitureRight(e);}
    @EventHandler public void onFurniturePrecise(PlayerInteractAtEntityEvent e){handleFurnitureRight(e);}
    private void handleFurnitureRight(PlayerInteractEntityEvent e){
        String id=e.getRightClicked().getPersistentDataContainer().get(furnitureKey,PersistentDataType.STRING);if(id==null)return;e.setCancelled(true);
        Player p=e.getPlayer();if(e.getHand()!=EquipmentSlot.HAND||!plots.canBuildInHub(p,e.getRightClicked().getLocation())||!ready(p))return;
        String key="housing.furniture."+id;
        if(p.isSneaking()&&p.getInventory().getItemInMainHand().getType().isAir()){
            data.set(key+".yaw",(data.getDouble(key+".yaw")+45)%360);save.run();spawnFurniture(id);
        }else if(!p.isSneaking()&&"furn_kotatsu".equals(data.getString(key+".model"))){
            Location at=furnitureLocation(id).add(0,0.1,0);ArmorStand seat=at.getWorld().spawn(at,ArmorStand.class);
            seat.setInvisible(true);seat.setMarker(true);seat.setGravity(false);seat.setInvulnerable(true);seat.setPersistent(false);seat.getPersistentDataContainer().set(seatKey,PersistentDataType.STRING,id);seat.addPassenger(p);
            new BukkitRunnable(){public void run(){if(!seat.isValid()||seat.getPassengers().isEmpty()){seat.remove();cancel();}}}.runTaskTimer(plugin,20L,20L);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void onFurnitureHit(EntityDamageByEntityEvent e){
        String id=e.getEntity().getPersistentDataContainer().get(furnitureKey,PersistentDataType.STRING);if(id==null)return;e.setCancelled(true);
        if(e.getDamager() instanceof Player p&&p.isSneaking()&&plots.canBuildInHub(p,e.getEntity().getLocation())&&ready(p)){
            String owner=data.getString("housing.furniture."+id+".owner");PlotSpec plot=plots.getPlotAt(e.getEntity().getLocation());
            if(p.getUniqueId().toString().equals(owner)||(plot!=null&&p.getUniqueId().equals(plots.getOwner(plot.id()))))recover(id);
        }
    }
    public void recoverPlot(PlotSpec plot){
        ConfigurationSection section=data.getConfigurationSection("housing.furniture");if(section==null)return;
        for(String id:new ArrayList<>(section.getKeys(false)))if(plot.id().equals(section.getString(id+".plot")))recover(id);
    }
    private void recover(String id){
        String key="housing.furniture."+id;String model=data.getString(key+".model");if(!isFurniture(model))return;
        UUID owner=UUID.fromString(data.getString(key+".owner"));ItemStack item=data.getItemStack(key+".item",items.create(model));
        mailbox.enqueueOnce(owner,"furniture_"+id,item);
        Location at=furnitureLocation(id);at.getChunk().load();for(Entity e:new ArrayList<>(at.getWorld().getNearbyEntities(at,4,5,4))){
            if(id.equals(e.getPersistentDataContainer().get(furnitureKey,PersistentDataType.STRING))||id.equals(e.getPersistentDataContainer().get(seatKey,PersistentDataType.STRING))){e.eject();e.remove();}
        }
        data.set(key,null);save.run();Player p=Bukkit.getPlayer(owner);if(p!=null)mailbox.deliver(p);
    }
    public void animateRoulette(){
        World world=hub.getHubWorld();if(world==null)return;
        NamespacedKey propKey=new NamespacedKey(plugin,"hub_3d_prop_id");
        for(ItemDisplay display:world.getEntitiesByClass(ItemDisplay.class))if("prop_roulette_wheel".equals(display.getPersistentDataContainer().get(propKey,PersistentDataType.STRING))){
            float yaw=display.getYaw();new BukkitRunnable(){int ticks;public void run(){if(!display.isValid()||ticks>=30){if(display.isValid())display.setRotation(yaw,0);cancel();return;}ticks++;display.setRotation(yaw+ticks*36f,0);}}.runTaskTimer(plugin,0L,1L);
        }
    }
    private ItemStack icon(Material m,String text,String...lore){ItemStack item=new ItemStack(m);ItemMeta meta=item.getItemMeta();meta.displayName(TextUtil.mm(text));meta.lore(TextUtil.lore(lore));item.setItemMeta(meta);return item;}
}
