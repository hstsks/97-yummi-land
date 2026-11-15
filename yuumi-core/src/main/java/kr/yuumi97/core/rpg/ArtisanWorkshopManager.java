package kr.yuumi97.core.rpg;

import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;import org.bukkit.configuration.ConfigurationSection;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.inventory.*;import org.bukkit.event.player.PlayerItemConsumeEvent;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import org.bukkit.persistence.PersistentDataType;
import java.util.*;import java.util.concurrent.ThreadLocalRandom;

/** Public recipes remain available regardless of online job distribution. */
public final class ArtisanWorkshopManager implements Listener {
    public static final List<String> RUNES=List.of("FIRE","FROST","THUNDER","GALE","BASTION");
    private record Holder(String kind) implements InventoryHolder {public Inventory getInventory(){return null;}}
    private final RpgJobManager r;
    private final Map<String,Map<String,Integer>> recipes=new LinkedHashMap<>();
    public ArtisanWorkshopManager(RpgJobManager r){this.r=r;
        recipes.put("plushie_yuumi",Map.of("WHITE_WOOL",20,"BLUE_WOOL",10,"monster_soul_core",5));
        recipes.put("furn_cat_tower",Map.of("OAK_LOG",20,"PINK_WOOL",20,"monster_soul_core",5));
        recipes.put("furn_kotatsu",Map.of("CHERRY_LOG",20,"WHITE_WOOL",20,"monster_soul_core",5));
        recipes.put("furn_neon_sign",Map.of("GLASS",20,"GLOW_INK_SAC",10,"monster_soul_core",5));
        recipes.put("VALOR",Map.of("COOKED_BEEF",10,"deep_sea_fillet",5,"monster_soul_core",5));
        recipes.put("SWIFT",Map.of("COOKED_SALMON",10,"deep_sea_fillet",5,"golden_catnip",5));
        recipes.put("HARVEST",Map.of("BAKED_POTATO",10,"golden_catnip",5,"monster_soul_core",5));
    }
    public void openSmith(Player p){open(p,"smith");}
    public void openFurniture(Player p){open(p,"furniture");}
    public void openFeast(Player p){open(p,"feast");}
    public static String runeName(String type){return switch(type){case "FIRE"->"홍련";case "FROST"->"빙결";case "THUNDER"->"뇌전";case "GALE"->"질풍";case "BASTION"->"철벽";default->"미감정";};}
    public static String feastName(String type){return switch(type){case "VALOR"->"용맹";case "SWIFT"->"질풍";case "HARVEST"->"풍요";default->"특선";};}
    private net.kyori.adventure.text.Component ingredientName(String id){Material material=Material.matchMaterial(id);return material!=null?net.kyori.adventure.text.Component.translatable(material.translationKey()):r.items.create(id).getItemMeta().displayName();}
    private List<String> outputs(String kind){return switch(kind){case "smith","rune"->RUNES;case "furniture"->List.of("plushie_yuumi","furn_cat_tower","furn_kotatsu","furn_neon_sign");default->List.of("VALOR","SWIFT","HARVEST");};}
    private boolean specialist(Player p,String kind){return r.getJob(p)==switch(kind){case "smith"->JobType.BLACKSMITH;case "furniture"->JobType.CARPENTER;default->JobType.CHEF;};}
    private Map<String,Integer> materials(Player p,String kind,String id){Map<String,Integer> base;
        if(kind.equals("smith"))return Map.of("monster_soul_core",specialist(p,kind)?3:5,"starlight_stone",specialist(p,kind)?2:3);
        ConfigurationSection section=r.plugin.getConfig().getConfigurationSection("workshops.recipes."+id);base=new LinkedHashMap<>(recipes.get(id));
        if(section!=null){base.clear();for(String key:section.getKeys(false)){int n=section.getInt(key);if(n>0)base.put(key,n);}}
        if(specialist(p,kind)){double f=kind.equals("furniture")?.55:.60;base.replaceAll((k,v)->RpgRules.materialCost(v,f));}return base;}
    private double cost(Player p,String kind){return specialist(p,kind)?0:r.cfg("workshops."+kind+".fee",switch(kind){case "smith"->2500;case "furniture"->3000;default->1500;});}
    private ItemStack preview(String kind,String id){return kind.equals("smith")||kind.equals("rune")?r.items.runeScroll(id,false):kind.equals("feast")?r.items.michelinFeast(id,2):r.items.create(id);}
    private void open(Player p,String kind){String title=switch(kind){case "smith"->"대장간 공방";case "furniture"->"목공 가구 공방";case "feast"->"특선 만찬 조리대";default->"마법 룬 각인";};Inventory i=Bukkit.createInventory(new Holder(kind),36,TextUtil.mm("<gold>공공 공방 · "+title));List<String> out=outputs(kind);
        for(int n=0;n<out.size();n++){String id=out.get(n);ItemStack icon=preview(kind,id);ItemMeta m=icon.getItemMeta();List<net.kyori.adventure.text.Component> lore=new ArrayList<>();
            if(kind.equals("rune")){lore.add(TextUtil.mm("<yellow>주손 장비에 이 종류 각인서 1개 부여"));lore.add(TextUtil.mm("<gray>기존 룬을 덮어씁니다 · 공임 무료"));}
            else{for(var entry:materials(p,kind,id).entrySet())lore.add(TextUtil.mm("<gray>• ").append(ingredientName(entry.getKey())).append(TextUtil.mm("<gray> × "+entry.getValue())));lore.add(TextUtil.mm("<gold>공임: "+TextUtil.fmt(cost(p,kind))+" 츄르"));lore.add(TextUtil.mm("<yellow>클릭하여 1회 제작"));}
            m.lore(lore);icon.setItemMeta(m);i.setItem(10+n,icon);}
        if(kind.equals("smith")){i.setItem(22,RpgJobManager.icon(Material.RAW_GOLD,"<aqua>별빛 지오드 감정","<gray>지오드 1개 + 공임 "+(specialist(p,kind)?0:1500)+" 츄르","<gray>대장장이: 공임 무료 · 희귀율 2배"));i.setItem(24,RpgJobManager.icon(Material.ENCHANTED_BOOK,"<light_purple>장비 룬 부여 슬롯","<gray>대상 장비를 주손에 들고 클릭"));}
        if(kind.equals("rune")){ItemStack hand=p.getInventory().getItemInMainHand();i.setItem(22,hand.getType().isAir()?RpgJobManager.icon(Material.BARRIER,"<red>주손에 장비를 들어주세요"):hand.clone());}
        i.setItem(31,RpgJobManager.icon(Material.BOOK,"<green>공공 기본 제작 + 전문직 효율 우대","<gray>현재 성향: "+r.getSummary(p)));p.openInventory(i);}
    private boolean ingredient(ItemStack s,String id){if(s==null||s.getType().isAir())return false;Material material=Material.matchMaterial(id);
        return material==null?r.items.isCustomItem(s,id):s.getType()==material&&r.items.getCustomId(s)==null;}
    private int count(Player p,String id){int n=0;for(ItemStack s:p.getInventory().getStorageContents())if(ingredient(s,id))n+=s.getAmount();return n;}
    private void take(Player p,String id,int n){for(ItemStack s:p.getInventory().getStorageContents())if(ingredient(s,id)){int a=Math.min(n,s.getAmount());s.setAmount(s.getAmount()-a);n-=a;if(n==0)return;}}
    private boolean pay(Player p,Map<String,Integer> ingredients,double fee){for(var e:ingredients.entrySet())if(count(p,e.getKey())<e.getValue()){p.sendMessage(TextUtil.msg("<red>재료 부족: ").append(ingredientName(e.getKey())).append(TextUtil.mm("<red> × "+e.getValue())));return false;}
        if(p.getInventory().firstEmpty()<0){p.sendMessage(TextUtil.msg("<red>완제품을 받을 빈 칸이 필요합니다."));return false;}
        if(!r.churu.withdraw(p,fee)){p.sendMessage(TextUtil.msg("<red>공임이 부족합니다."));return false;}ingredients.forEach((k,v)->take(p,k,v));return true;}
    private void craft(Player p,String kind,String id){if(!pay(p,materials(p,kind,id),cost(p,kind)))return;boolean pro=specialist(p,kind);
        double extra=r.perk(p,"efficiency")*.03+(pro?(kind.equals("furniture")?.30:kind.equals("feast")?.35:0):0);
        boolean master=kind.equals("smith")&&pro&&ThreadLocalRandom.current().nextDouble()<Math.min(1,r.cfg("runes.masterwork-chance",.25)+r.perk(p,"efficiency")*.03+(r.getCombat().buff(p,"masterwork")?.20:0));
        ItemStack result=kind.equals("smith")?r.items.runeScroll(id,master):kind.equals("feast")?r.items.michelinFeast(id,pro?3:2):r.items.create(id);
        result.setAmount(ThreadLocalRandom.current().nextDouble()<Math.min(1,extra)?2:1);r.deliver(p,result);r.activityXp(p,kind.equals("smith")?"smith":kind.equals("feast")?"cooking":"furniture",(int)r.cfg("rpg.xp.craft",40));
        p.playSound(p.getLocation(),Sound.BLOCK_SMITHING_TABLE_USE,.8f,1.3f);p.sendMessage(TextUtil.msg("<green>제작 완료! "+(result.getAmount()==2?"대성공 1+1!":"")+(master?" 명장 각인 +25%!":"")));open(p,kind);}
    private void appraise(Player p){boolean pro=r.getJob(p)==JobType.BLACKSMITH;if(!pay(p,Map.of("unidentified_geode",1),pro?0:r.cfg("workshops.geode-fee",1500)))return;
        double x=ThreadLocalRandom.current().nextDouble();ItemStack result;
        if(x<(pro?.30:.15))result=r.items.create("starlight_scroll");else if(x<(pro?.50:.25))result=r.items.create("ancient_cat_coin",2);
        else result=r.items.create("starlight_stone",ThreadLocalRandom.current().nextDouble()<2.0/3?2:4);
        r.deliver(p,result);r.activityXp(p,"smith",40);openSmith(p);}
    private void applyRune(Player p,String type){ItemStack gear=p.getInventory().getItemInMainHand();Material m=gear.getType();String name=m.name();
        boolean valid=r.isSignature(gear)||r.items.getCustomId(gear)==null&&(name.endsWith("_SWORD")||name.endsWith("_AXE")||name.endsWith("_PICKAXE")||name.endsWith("_SHOVEL")||name.endsWith("_HOE")||name.endsWith("_HELMET")||name.endsWith("_CHESTPLATE")||name.endsWith("_LEGGINGS")||name.endsWith("_BOOTS")||m==Material.BOW||m==Material.CROSSBOW||m==Material.TRIDENT||m==Material.MACE);
        if(!valid){p.sendMessage(TextUtil.msg("<red>주손에 세공 가능한 장비를 들어주세요."));return;}
        ItemStack scroll=null;for(ItemStack s:p.getInventory().getStorageContents())if(r.items.isCustomItem(s,"rune_scroll")&&type.equals(s.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(r.plugin,"yuumi_rune_type"),PersistentDataType.STRING))){scroll=s;break;}
        if(scroll==null){p.sendMessage(TextUtil.msg("<red>해당 룬 각인서가 없습니다."));return;}ItemMeta meta=gear.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(r.plugin,"yuumi_rune_type"),PersistentDataType.STRING,type);
        int master=scroll.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"yuumi_rune_masterwork"),PersistentDataType.INTEGER,0);
        meta.getPersistentDataContainer().set(new NamespacedKey(r.plugin,"yuumi_rune_masterwork"),PersistentDataType.INTEGER,master);gear.setItemMeta(meta);scroll.setAmount(scroll.getAmount()-1);
        if(r.isSignature(gear))r.getSignatures().refresh(gear,p);else{meta=gear.getItemMeta();var lore=meta.lore()==null?new ArrayList<net.kyori.adventure.text.Component>():new ArrayList<>(meta.lore());
            lore.removeIf(c->net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c).startsWith("룬:"));lore.add(TextUtil.mm("<light_purple>룬: "+runeName(type)+(master==1?" [명장 +25%]":"")));meta.lore(lore);gear.setItemMeta(meta);}
        p.sendMessage(TextUtil.msg("<green>룬 각인 완료!"));open(p,"rune");}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;int raw=e.getRawSlot();
        if(h.kind.equals("smith")&&raw==22){appraise(p);return;}if(h.kind.equals("smith")&&raw==24){open(p,"rune");return;}
        int n=raw-10;List<String> ids=outputs(h.kind);if(n<0||n>=ids.size())return;if(h.kind.equals("rune"))applyRune(p,ids.get(n));else craft(p,h.kind,ids.get(n));}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    public double feastEffect(Player p,String type,String effect,double fallback){return type.equals(r.data.getString(r.root(p)+".feast.type"))&&System.currentTimeMillis()<r.data.getLong(r.root(p)+".feast.expires")?r.cfg("feasts."+type+"."+effect,fallback)*(r.data.getInt(r.root(p)+".feast.stars",2)==3?1.35:1):0;}
    @EventHandler(ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e){if(!r.items.isCustomItem(e.getItem(),"michelin_feast"))return;
        var meta=e.getItem().getItemMeta().getPersistentDataContainer();String type=meta.get(new NamespacedKey(r.plugin,"yuumi_feast_type"),PersistentDataType.STRING);if(type==null||!List.of("VALOR","SWIFT","HARVEST").contains(type))return;
        int stars=meta.getOrDefault(new NamespacedKey(r.plugin,"yuumi_feast_stars"),PersistentDataType.INTEGER,2);Player p=e.getPlayer();r.data.set(r.root(p)+".feast.type",type);r.data.set(r.root(p)+".feast.stars",stars);
        r.data.set(r.root(p)+".feast.expires",System.currentTimeMillis()+(long)(r.cfg("feasts.duration-seconds",480)*(stars==3?1.35:1)*1000));r.save.run();p.sendMessage(TextUtil.msg("<gold>"+(stars==3?"★★★ 미슐랭":"★★ 공공")+" "+feastName(type)+" 만찬 효과 적용!"));}
}
