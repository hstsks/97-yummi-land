package kr.yuumi97.core.rpg;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.plot.PlotManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;

/** Persistent identity and growth. Only the active job earns XP; other job records are untouched. */
public final class RpgJobManager implements Listener {
    public static final class RpgHolder implements InventoryHolder {
        public final String screen;
        public RpgHolder(String screen) { this.screen = screen; }
        @Override public Inventory getInventory() { return null; }
    }
    public final JavaPlugin plugin;
    public final YuumiItems items;
    public final ChuruManager churu;
    public final FileConfiguration data;
    public final Runnable save;
    public final PlotManager plots;
    private final SignatureWeaponManager signatures;
    private final PerkTreeManager perks;
    private final BookmarkPouchManager pouch;
    private final ArtisanWorkshopManager workshop;
    private final RpgGatheringListener gathering;
    private final RpgCombatListener combat;
    private final FishingRarityBridge fishingBridge;
    private Function<Player, Collection<Player>> partyMembers = p -> List.of(p);
    private Predicate<Player> dungeon = p -> false;
    private BiPredicate<Player, Entity> canAffect = (p,e) -> true;
    private BiConsumer<Player,Double> obeliskDamage = (p,d) -> {};
    private Consumer<Player> reviveOne = p -> {};
    private boolean dirty;
    private kr.yuumi97.core.util.RewardMailbox mailbox;
    private Predicate<String> models = id -> false;
    private Predicate<Entity> additionalHostile = entity -> false;
    private Predicate<Entity> trainingDummy = entity -> false;
    private ToDoubleFunction<org.bukkit.event.entity.EntityDamageEvent> trainingDamage = event -> -1;
    private org.bukkit.scheduler.BukkitTask saveTask;

    public RpgJobManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu,
                         FileConfiguration data, Runnable save, PlotManager plots) {
        this.plugin=plugin; this.items=items; this.churu=churu; this.data=data; this.save=save; this.plots=plots;
        signatures=new SignatureWeaponManager(this); perks=new PerkTreeManager(this);
        pouch=new BookmarkPouchManager(this); workshop=new ArtisanWorkshopManager(this);
        gathering=new RpgGatheringListener(this); combat=new RpgCombatListener(this);fishingBridge=new FishingRarityBridge(this);
    }
    public void start() {
        for (Listener l : List.of(this, signatures, perks, pouch, workshop, gathering, combat, fishingBridge))
            Bukkit.getPluginManager().registerEvents(l,plugin);
        combat.start(); pouch.start();fishingBridge.start();
        saveTask=Bukkit.getScheduler().runTaskTimer(plugin, () -> { if(dirty) {save.run();dirty=false;} },100,100);
    }
    public void stop() {
        if(saveTask!=null)saveTask.cancel(); combat.stop(); pouch.stop(); gathering.stop();fishingBridge.stop(); save.run();
    }
    public SignatureWeaponManager getSignatures(){return signatures;}
    public BookmarkPouchManager getPouch(){return pouch;}
    public ArtisanWorkshopManager getWorkshop(){return workshop;}
    public RpgCombatListener getCombat(){return combat;}
    public void setMailbox(kr.yuumi97.core.util.RewardMailbox mailbox){this.mailbox=mailbox;}
    public void deliver(Player p,ItemStack...stacks){for(ItemStack stack:stacks)if(mailbox!=null)mailbox.give(p,stack);else give(p,stack);}
    public void setModelAvailability(Predicate<String> availability){models=availability;}
    public void setHostilePredicate(Predicate<Entity> predicate){additionalHostile=predicate;}
    public boolean taggedEnemy(Entity entity){return additionalHostile.test(entity);}
    public void setTrainingDummyHooks(Predicate<Entity> predicate,ToDoubleFunction<org.bukkit.event.entity.EntityDamageEvent> approvedDamage){trainingDummy=predicate;trainingDamage=approvedDamage;}
    public boolean isTrainingDummy(Entity entity){return trainingDummy.test(entity);}
    public double approvedTrainingDamage(org.bukkit.event.entity.EntityDamageEvent event){return trainingDamage.applyAsDouble(event);}
    public void recordTrainingEffect(Player player,LivingEntity entity,org.bukkit.event.entity.EntityDamageEvent.DamageCause cause,int ticks){if(!isTrainingDummy(entity)||ticks<=0)return;
        String key=cause==org.bukkit.event.entity.EntityDamageEvent.DamageCause.POISON?"yuumi_training_poison_owner":"yuumi_training_fire_owner";
        String expiry=cause==org.bukkit.event.entity.EntityDamageEvent.DamageCause.POISON?"yuumi_training_poison_expires":"yuumi_training_fire_expires";
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin,key),org.bukkit.persistence.PersistentDataType.STRING,player.getUniqueId().toString());
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin,expiry),org.bukkit.persistence.PersistentDataType.LONG,System.nanoTime()+ticks*50_000_000L);}
    public ItemStack prop(String id,Material fallback){ItemStack stack=new ItemStack(fallback);if(models.test(id))stack.editMeta(meta->meta.setItemModel(new NamespacedKey("yuumi97",id)));return stack;}
    public void setPartyMembers(Function<Player,Collection<Player>> provider){partyMembers=provider;}
    public void setDungeonHooks(Predicate<Player> participant, BiPredicate<Player,Entity> affect,
                               BiConsumer<Player,Double> obelisks, Consumer<Player> revive) {
        dungeon=participant;canAffect=affect;obeliskDamage=obelisks;reviveOne=revive;
    }
    public boolean inDungeon(Player p){return dungeon.test(p);}
    public boolean canAffect(Player p, Entity e){return canAffect.test(p,e);}
    public void damageObelisks(Player p,double damage){obeliskDamage.accept(p,damage);}
    public void harvest(Player p,org.bukkit.block.Block block){gathering.harvest(p,block,block.getType());}
    public void reviveOne(Player p){reviveOne.accept(p);}
    public Collection<Player> allies(Player p,double radius) {
        LinkedHashSet<Player> result=new LinkedHashSet<>(); result.add(p);
        for(Player a:partyMembers.apply(p)) if(a.isOnline() && !a.isDead() && a.getGameMode()!=GameMode.SPECTATOR
            && a.getWorld().equals(p.getWorld()) && a.getLocation().distanceSquared(p.getLocation())<=radius*radius) result.add(a);
        return result;
    }
    public String root(Player p){return "rpg."+p.getUniqueId();}
    public String jobPath(Player p){JobType j=getJob(p);return root(p)+".jobs."+(j==null?"NONE":j.name());}
    public JobType getJob(Player p){return JobType.parse(data.getString(root(p)+".active_job"));}
    public int getLevel(Player p){return getJob(p)==null?0:Math.clamp(data.getInt(jobPath(p)+".level",1),1,20);}
    public String getAwakening(Player p){return data.getString(jobPath(p)+".awakening","");}
    public int perk(Player p,String branch){return Math.clamp(data.getInt(jobPath(p)+".perks."+branch,0),0,10);}
    public double cfg(String path,double fallback){return plugin.getConfig().getDouble(path,fallback);}
    public String getSummary(Player p){JobType j=getJob(p); if(j==null)return "미선택";
        String a=getAwakening(p);return j.display+" Lv."+getLevel(p)+(a.equals("A")?" · "+j.awakeningA:a.equals("B")?" · "+j.awakeningB:"");}
    public boolean isSignature(ItemStack item){return JobType.fromItem(items.getCustomId(item))!=null;}
    public boolean nearTeacher(Player p){if(!p.getWorld().getName().equals("yuumi_hub"))return false;
        NamespacedKey key=new NamespacedKey(plugin,"hub_npc_role");for(Entity e:p.getNearbyEntities(7,5,7))if("class_master".equals(e.getPersistentDataContainer().get(key,org.bukkit.persistence.PersistentDataType.STRING)))return true;
        p.sendMessage(TextUtil.msg("<yellow>직업·각성 변경과 성물 재발급은 광장 북서쪽 전직 교관을 방문하세요."));return false;}
    public boolean holdingSignature(Player p){JobType j=getJob(p);return j!=null && items.isCustomItem(p.getInventory().getItemInMainHand(),j.itemId);}
    public boolean hasCharm(Player p,String id){return pouch.hasCharm(p,id);}
    public double getCoinMultiplier(Player p){return hasCharm(p,"charm_golden_whisker")?1.25:1;}
    public void changed(){dirty=true;}
    public int xpNeeded(int level){return RpgRules.xpNeeded(Math.clamp(level,1,19),(int)cfg("rpg.xp.base",80),(int)cfg("rpg.xp.linear",40),(int)cfg("rpg.xp.quadratic",10));}
    public void awardXp(Player p,int amount){
        if(getJob(p)==null||getLevel(p)>=20||amount<=0||p.getGameMode()==GameMode.CREATIVE)return;
        double multiplier=workshop.feastEffect(p,"HARVEST","xp",.15);
        int xp=data.getInt(jobPath(p)+".xp",0)+(int)Math.round(amount*(1+multiplier));int level=getLevel(p);
        while(level<20 && xp>=xpNeeded(level)){xp-=xpNeeded(level);level++;p.sendMessage(TextUtil.msg("<gold>성향 성장! "+getJob(p).display+" Lv."+level+" · 특전 1 SP 획득"));}
        data.set(jobPath(p)+".level",level);data.set(jobPath(p)+".xp",level==20?0:xp);changed();signatures.refreshInventory(p);
    }
    public void activityXp(Player p,String activity,int base){
        JobType j=getJob(p);if(j==null)return;
        boolean match=switch(activity){case "combat"->j.combat();case "mining"->j==JobType.MINER;
            case "farming"->j==JobType.FARMER;case "fishing"->j==JobType.ANGLER;case "wood"->j==JobType.CARPENTER;
            case "smith"->j==JobType.BLACKSMITH;case "furniture"->j==JobType.CARPENTER;case "cooking"->j==JobType.CHEF;default->false;};
        awardXp(p,Math.max(1,(int)Math.round(base*(match?1:cfg("rpg.xp.other-activity-multiplier",.25)))));
    }
    public static ItemStack icon(Material material,String name,String...lore){ItemStack s=new ItemStack(material);ItemMeta m=s.getItemMeta();m.displayName(TextUtil.mm(name));m.lore(TextUtil.lore(lore));s.setItemMeta(m);return s;}
    public static void give(Player p,ItemStack...items){for(ItemStack s:items)for(ItemStack left:p.getInventory().addItem(s).values())p.getWorld().dropItemNaturally(p.getLocation(),left);}
    public int count(Player p,String id){int n=0;for(ItemStack s:p.getInventory().getStorageContents())if(items.isCustomItem(s,id))n+=s.getAmount();return n;}
    public boolean consume(Player p,String id,int amount){if(count(p,id)<amount)return false;int n=amount;
        for(ItemStack s:p.getInventory().getStorageContents())if(items.isCustomItem(s,id)){int take=Math.min(n,s.getAmount());s.setAmount(s.getAmount()-take);n-=take;if(n==0)break;}return true;}
    public void openClassGui(Player p){
        Inventory inv=Bukkit.createInventory(new RpgHolder("class"),54,TextUtil.mm("<gold>성향 교관 마스터냥 · 10대 직업"));
        for(JobType j:JobType.values()){ItemStack s=items.create(j.itemId);ItemMeta m=s.getItemMeta();m.lore(TextUtil.lore("<yellow>클릭: "+j.display+" 선택","<gray>A: "+j.awakeningA,"<gray>B: "+j.awakeningB,"<gray>성장과 특전은 직업별 영구 보존"));s.setItemMeta(m);inv.setItem(10+j.ordinal()+(j.ordinal()>=7?2:0),s);}
        inv.setItem(40,icon(Material.NETHER_STAR,"<aqua>내 성향 · 특전 · 책갈피함",getSummary(p)));
        inv.setItem(49,icon(Material.BOOK,"<yellow>최초 변경 무료 · 이후 물약 1개 또는 5,000 츄르","<gray>변경 쿨타임 1시간 · A/B 재선택 3,000 츄르"));p.openInventory(inv);
    }
    public void openProfile(Player p){Inventory inv=Bukkit.createInventory(new RpgHolder("profile"),27,TextUtil.mm("<aqua>집사 성향 문장"));
        inv.setItem(4,icon(Material.NETHER_STAR,"<gold>"+getSummary(p),"<gray>XP "+data.getInt(jobPath(p)+".xp",0)+" / "+xpNeeded(Math.max(1,getLevel(p)))));
        inv.setItem(10,icon(Material.EXPERIENCE_BOTTLE,"<red>20 SP 특전 트리"));inv.setItem(12,icon(Material.BUNDLE,"<green>3슬롯 책갈피 유물함"));
        inv.setItem(14,icon(Material.BLAZE_POWDER,"<yellow>각성 A 선택 / 재선택","<gray>Lv.10 이상 · 최초 무료 · 이후 3,000 츄르"));
        inv.setItem(15,icon(Material.ENDER_PEARL,"<light_purple>각성 B 선택 / 재선택","<gray>Lv.10 이상 · 최초 무료 · 이후 3,000 츄르"));
        inv.setItem(16,icon(Material.DIAMOND_SWORD,"<aqua>성물 / 문장 무료 재발급","<gray>스킬·보너스는 활성 직업의 성물만 적용"));p.openInventory(inv);}
    private void selectJob(Player p,JobType next){if(!nearTeacher(p))return;JobType old=getJob(p);if(old==next){openProfile(p);return;}
        long now=System.currentTimeMillis();int changes=data.getInt(root(p)+".changes",0);
        if(old!=null&&now<data.getLong(root(p)+".change_ready_at",0)){p.sendMessage(TextUtil.msg("<red>성향 변경은 1시간마다 가능합니다."));return;}
        if(old!=null&&changes>=1){if(count(p,"respec_potion")>0)consume(p,"respec_potion",1);else if(!churu.withdraw(p,cfg("rpg.job-change-cost",5000))){p.sendMessage(TextUtil.msg("<red>망각 물약 또는 5,000 츄르가 필요합니다."));return;}}
        if(old!=null){data.set(root(p)+".changes",changes+1);data.set(root(p)+".change_ready_at",now+(long)(cfg("rpg.job-change-cooldown-seconds",3600)*1000));}
        data.set(root(p)+".active_job",next.name());if(!data.contains(jobPath(p)+".level"))data.set(jobPath(p)+".level",1);
        save.run();combat.clearTemporary(p);signatures.issue(p);p.sendMessage(TextUtil.msg("<gold>이제 "+next.display+"입니다! 다른 성향의 성장은 보존되었습니다."));openProfile(p);
    }
    private void awaken(Player p,String branch){if(!nearTeacher(p))return;if(getLevel(p)<10){p.sendMessage(TextUtil.msg("<red>초월 각성에는 Lv.10이 필요합니다."));return;}
        String old=getAwakening(p);if(old.equals(branch))return;if(!old.isEmpty()&&!churu.withdraw(p,cfg("rpg.reawakening-cost",3000))){p.sendMessage(TextUtil.msg("<red>각성 변경에는 3,000 츄르가 필요합니다."));return;}
        data.set(jobPath(p)+".awakening",branch);save.run();combat.clearTemporary(p);signatures.refreshInventory(p);openProfile(p);}
    @EventHandler public void onClick(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof RpgHolder h))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;
        int slot=e.getRawSlot();if(slot<0||slot>=e.getView().getTopInventory().getSize())return;
        if(h.screen.equals("class")){if(slot==40){openProfile(p);return;}for(JobType j:JobType.values())if(slot==10+j.ordinal()+(j.ordinal()>=7?2:0)){selectJob(p,j);return;}}
        if(h.screen.equals("profile"))switch(slot){case 10->perks.open(p);case 12->pouch.open(p);case 14->awaken(p,"A");case 15->awaken(p,"B");case 16->signatures.issue(p);}
    }
    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof RpgHolder)e.setCancelled(true);}
    @EventHandler public void onJoin(PlayerJoinEvent e){Bukkit.getScheduler().runTask(plugin,()->signatures.refreshInventory(e.getPlayer()));}
}
