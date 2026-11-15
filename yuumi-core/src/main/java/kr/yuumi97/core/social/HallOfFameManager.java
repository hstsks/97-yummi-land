package kr.yuumi97.core.social;

import io.papermc.paper.event.player.AsyncChatEvent;
import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class HallOfFameManager implements Listener,CommandExecutor {
    private static final class TitleHolder implements InventoryHolder {
        final List<String> titles;TitleHolder(List<String> titles){this.titles=titles;}public Inventory getInventory(){return null;}
    }
    private final JavaPlugin plugin;private final ChuruManager churu;private final FileConfiguration data;
    private final Runnable save;private final Supplier<World> hub;private final NamespacedKey boardKey;
    private final Map<UUID,Component> active=new ConcurrentHashMap<>();private BukkitTask task;
    private Runnable mythicRefresh=()->{};
    public HallOfFameManager(JavaPlugin plugin,ChuruManager churu,FileConfiguration data,Runnable save,Supplier<World> hub){
        this.plugin=plugin;this.churu=churu;this.data=data;this.save=save;this.hub=hub;boardKey=new NamespacedKey(plugin,"hall_board");
    }
    public void setMythicRefresh(Runnable callback){mythicRefresh=callback;}
    public void start(){Bukkit.getPluginManager().registerEvents(this,plugin);task=Bukkit.getScheduler().runTaskTimer(plugin,()->{mythicRefresh.run();refresh();},100L,1200L);}
    public void stop(){if(task!=null)task.cancel();for(Player p:Bukkit.getOnlinePlayers())applyTab(p,null);}
    public void recordSmith(Player p,int targetStar){
        String key="social.smith."+p.getUniqueId();data.set(key,data.getLong(key,0)+targetStar);save.run();
    }
    public void unlock(UUID id,String key,String label){
        String path="social.titles."+id+".unlocked."+key;
        if(!data.contains(path)){data.set(path,label);save.run();Player p=Bukkit.getPlayer(id);if(p!=null)p.sendMessage(TextUtil.msg("🏆 새 칭호 획득: "+label));}
    }
    private Set<UUID> candidates(){
        Set<UUID> ids=new HashSet<>();for(OfflinePlayer p:Bukkit.getOfflinePlayers())ids.add(p.getUniqueId());
        for(String key:List.of("players","bank","rpg","social.smith","dungeon.stats")){
            ConfigurationSection section=data.getConfigurationSection(key);if(section==null)continue;
            for(String raw:section.getKeys(false))try{ids.add(UUID.fromString(raw));}catch(IllegalArgumentException ignored){}
        }return ids;
    }
    private double score(UUID id,int category){return switch(category){
        case 0->churu.getBalance(Bukkit.getOfflinePlayer(id))+churu.getBankBalance(Bukkit.getOfflinePlayer(id));
        case 1->data.getInt("dungeon.stats."+id+".clears",0);
        default->data.getLong("social.smith."+id,0);
    };}
    public void refresh(){
        World world=hub.get();if(world==null)return;world.getChunkAt(14>>4,-14>>4).load();
        String[] labels={"최고 갑부","미궁 정복왕","별빛 세공 명장"};String[] keys={"wealth","dungeon","smith"};
        for(int category=0;category<3;category++){
            final int c=category;List<UUID> ranked=candidates().stream().filter(id->score(id,c)>0).sorted(Comparator.<UUID>comparingDouble(id->score(id,c)).reversed().thenComparing(UUID::toString)).limit(3).toList();
            Location at=new Location(world,10.5+category*4,69,-17.5);
            world.getChunkAt(at).load();
            StringBuilder text=new StringBuilder("<gold><bold>🏆 "+labels[category]+" Top 3</bold></gold>\n");
            for(int i=0;i<ranked.size();i++){
                UUID id=ranked.get(i);String name=Bukkit.getOfflinePlayer(id).getName();if(name==null)name=id.toString().substring(0,8);
                text.append("<yellow>").append(i+1).append(". <white>").append(name).append(" <aqua>").append(TextUtil.fmt(score(id,c))).append("</aqua></white></yellow>\n");
                unlock(id,keys[c],"<gold>["+labels[c]+"]</gold>");
            }
            if(ranked.isEmpty())text.append("<gray>첫 번째 주인공을 기다립니다!</gray>");
            List<TextDisplay> existing=world.getEntitiesByClass(TextDisplay.class).stream().filter(e->keys[c].equals(e.getPersistentDataContainer().get(boardKey,PersistentDataType.STRING))).toList();
            TextDisplay board=existing.isEmpty()?world.spawn(at,TextDisplay.class):existing.getFirst();for(int i=1;i<existing.size();i++)existing.get(i).remove();
            board.getPersistentDataContainer().set(boardKey,PersistentDataType.STRING,keys[c]);board.setBillboard(Display.Billboard.CENTER);board.setPersistent(true);board.text(TextUtil.mm(text.toString()));
        }
        for(Player p:Bukkit.getOnlinePlayers())loadTitle(p);
    }
    public void openHall(Player p){refresh();p.sendMessage(TextUtil.msg("🏆 광장 북동쪽 명예의 전당에서 세 부문의 Top 3를 확인하세요!"));openTitles(p);}
    public void openTitles(Player p){
        List<String> keys=new ArrayList<>();keys.add("none");ConfigurationSection section=data.getConfigurationSection("social.titles."+p.getUniqueId()+".unlocked");if(section!=null)keys.addAll(section.getKeys(false));
        Inventory inv=Bukkit.createInventory(new TitleHolder(keys),54,TextUtil.mm("<gold><bold>🏆 내 명예 칭호</bold></gold>"));
        for(int i=0;i<Math.min(keys.size(),54);i++){
            String label=keys.get(i).equals("none")?"<gray>칭호 해제":data.getString("social.titles."+p.getUniqueId()+".unlocked."+keys.get(i));
            ItemStack item=new ItemStack(Material.NAME_TAG);ItemMeta meta=item.getItemMeta();meta.displayName(TextUtil.mm(label));item.setItemMeta(meta);inv.setItem(i,item);
        }p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof TitleHolder h))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||e.getRawSlot()<0||e.getRawSlot()>=Math.min(54,h.titles.size())||!e.isLeftClick())return;
        String key=h.titles.get(e.getRawSlot());data.set("social.titles."+p.getUniqueId()+".active",key.equals("none")?null:key);save.run();loadTitle(p);p.closeInventory();
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof TitleHolder)e.setCancelled(true);}
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->loadTitle(e.getPlayer()),60L);}
    private void loadTitle(Player p){
        String key=data.getString("social.titles."+p.getUniqueId()+".active");String label=key==null?null:data.getString("social.titles."+p.getUniqueId()+".unlocked."+key);
        if(label==null)active.remove(p.getUniqueId());else active.put(p.getUniqueId(),TextUtil.mm(label+" "));
        applyTab(p,label);
    }
    private void applyTab(Player p,String label){
        Component prefix=label==null?Component.empty():TextUtil.mm(label+" ");
        if(Bukkit.getPluginManager().isPluginEnabled("TAB")){
            TabBridge.apply(p,prefix);return;
        }
        p.playerListName(prefix.append(Component.text(p.getName())));
    }
    private static final class TabBridge {
        static void apply(Player p,Component prefix){
            var api=me.neznamy.tab.api.TabAPI.getInstance();var player=api.getPlayer(p.getUniqueId());var manager=api.getTabListFormatManager();
            if(player==null||manager==null)return;
            String legacy=net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(prefix);
            manager.setPrefix(player,legacy.isEmpty()?null:legacy+Objects.toString(manager.getOriginalPrefix(player),""));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(AsyncChatEvent e){
        Component prefix=active.get(e.getPlayer().getUniqueId());if(prefix==null)return;var renderer=e.renderer();
        e.renderer((source,name,message,viewer)->prefix.append(renderer.render(source,name,message,viewer)));
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(sender instanceof Player p)openTitles(p);return true;}
}
