package kr.yuumi97.core.stock;

import kr.yuumi97.core.casino.CasinoManager;
import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.util.TextUtil;
import kr.yuumi97.core.util.V3Rules;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class ChuruStockManager implements Listener, CommandExecutor {
    public enum Stock {
        CATNIP_BIO("캣닢 바이오",100,0.03), TUNA_MARINE("참치 마린",150,0.02),
        STARLIGHT_MINING("별빛 마이닝",200,0.04), VOID_TECH("공허 테크",80,0.08);
        public final String title; public final int initial; public final double volatility;
        Stock(String title,int initial,double volatility){this.title=title;this.initial=initial;this.volatility=volatility;}
    }
    private static final class Holder implements InventoryHolder {public Inventory getInventory(){return null;}}
    private final JavaPlugin plugin; private final ChuruManager churu; private final CasinoManager casino;
    private final FileConfiguration data; private final Runnable save; private final Supplier<Location> broker;
    private final NamespacedKey boardKey; private BukkitTask task; private int smithSuccess; private int elapsed;
    public ChuruStockManager(JavaPlugin plugin,ChuruManager churu,CasinoManager casino,FileConfiguration data,Runnable save,Supplier<Location> broker){
        this.plugin=plugin;this.churu=churu;this.casino=casino;this.data=data;this.save=save;this.broker=broker;
        boardKey=new NamespacedKey(plugin,"stock_board");
    }
    private String cfg(Stock s,String field){return "stocks.instruments."+s.name()+"."+field;}
    public double price(Stock s){return data.getDouble("stocks.prices."+s.name(),plugin.getConfig().getDouble(cfg(s,"initial"),s.initial));}
    public int holdings(UUID player,Stock s){return data.getInt("stocks.holdings."+player+"."+s.name(),0);}
    public void onSmithSuccess(){smithSuccess++;}
    public void start(){
        for(Stock s:Stock.values())if(!data.contains("stocks.prices."+s.name()))data.set("stocks.prices."+s.name(),price(s));
        Bukkit.getPluginManager().registerEvents(this,plugin);save.run();
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            elapsed++;
            int period=Math.max(10,plugin.getConfig().getInt("stocks.update-seconds",600));
            if(elapsed%period==0)updatePrices();
            if(elapsed%Math.max(60,plugin.getConfig().getInt("stocks.dividend-seconds",3600))==0)payDividends();
            if(elapsed%10==0)refreshBoard();
        },100L,20L);
    }
    public void stop(){if(task!=null)task.cancel();}
    public void updatePrices(){
        for(Stock s:Stock.values()){
            double initial=plugin.getConfig().getDouble(cfg(s,"initial"),s.initial);
            double vol=Math.max(0,plugin.getConfig().getDouble(cfg(s,"volatility"),s.volatility));
            double delta=(ThreadLocalRandom.current().nextDouble()*2-1)*vol;
            if(s==Stock.STARLIGHT_MINING)delta+=Math.min(0.02,smithSuccess*0.001);
            double next=Math.max(Math.ceil(initial*0.2),Math.min(Math.floor(initial*5),Math.round(price(s)*(1+delta))));
            data.set("stocks.prices."+s.name(),next);
        }
        smithSuccess=0;data.set("stocks.last-update",System.currentTimeMillis());save.run();refreshBoard();
        String[] news={"캣닢 연구소, 새 향기 신약 발표!","참치 항로에 황금 어군 출현!","대장간의 단조 불꽃이 주가를 달굽니다!","공허 테크의 차원 실험이 시장을 흔듭니다!"};
        Bukkit.broadcast(TextUtil.msg("📈 <gold>[증권 속보]</gold> "+news[ThreadLocalRandom.current().nextInt(news.length)]+" <gray>카지노 2층 시세판이 갱신되었습니다."));
    }
    private void payDividends(){
        ConfigurationSection section=data.getConfigurationSection("stocks.holdings");if(section==null)return;
        for(String raw:section.getKeys(false)){
            UUID id;try{id=UUID.fromString(raw);}catch(IllegalArgumentException ex){continue;}
            double dividend=V3Rules.money(holdings(id,Stock.TUNA_MARINE)*price(Stock.TUNA_MARINE)*plugin.getConfig().getDouble("stocks.dividend-rate",0.001));
            if(dividend>0 && churu.tryDeposit(Bukkit.getOfflinePlayer(id),dividend)){
                Player p=Bukkit.getPlayer(id);if(p!=null)p.sendMessage(TextUtil.msg("🐟 참치 마린 배당금 "+TextUtil.churu(dividend)+" 입금!"));
            }
        }
        data.set("stocks.last-dividend",System.currentTimeMillis());save.run();
    }
    public void openBroker(Player p){
        Location at=broker.get();if(at==null||!p.getWorld().equals(at.getWorld())||p.getLocation().distanceSquared(at)>100){guide(p);return;}
        Inventory inv=Bukkit.createInventory(new Holder(),45,TextUtil.mm("<gold><bold>📈 츄르 증권 거래소</bold></gold>"));
        for(Stock s:Stock.values()){
            int row=s.ordinal()*9;int held=holdings(p.getUniqueId(),s);
            inv.setItem(row+1,icon(Material.PAPER,"<aqua>"+s.title,"<white>현재가: "+TextUtil.fmt(price(s)),"<gray>보유 "+held+" / 200주"));
            int[] quantities={1,10,50};
            for(int i=0;i<3;i++){
                inv.setItem(row+2+i,icon(Material.LIME_DYE,"<green>"+quantities[i]+"주 매수","<gray>비용 "+TextUtil.fmt(price(s)*quantities[i])+" 츄르"));
                inv.setItem(row+5+i,icon(Material.RED_DYE,"<red>"+quantities[i]+"주 매도","<gray>매도세 2% → 카지노 잭팟"));
            }
        }
        inv.setItem(40,icon(Material.GOLD_INGOT,"<gold>지갑 "+TextUtil.fmt(churu.getBalance(p))+" 츄르","<gray>실시간 현재가로 체결 · 종목당 최대 200주"));p.openInventory(inv);
    }
    @EventHandler public void onClick(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p)||event.getRawSlot()<0||event.getRawSlot()>=36||!event.isLeftClick()||event.isShiftClick())return;
        Location at=broker.get();if(at==null||!p.getWorld().equals(at.getWorld())||p.getLocation().distanceSquared(at)>100){p.closeInventory();return;}
        int col=event.getRawSlot()%9;if(col<2||col>7)return;
        Stock s=Stock.values()[event.getRawSlot()/9];int[] qs={1,10,50};int q=qs[(col-2)%3];int held=holdings(p.getUniqueId(),s);
        String key="stocks.holdings."+p.getUniqueId()+"."+s.name();double gross=V3Rules.money(price(s)*q);
        if(col<=4){
            if(!V3Rules.canBuy(held,q,200)||!churu.withdraw(p,gross)){p.sendMessage(TextUtil.msg("<red>잔액 또는 보유 한도를 확인하세요."));return;}
            data.set(key,held+q);
        }else{
            if(held<q){p.sendMessage(TextUtil.msg("<red>매도할 주식이 부족합니다."));return;}
            double tax=V3Rules.sellTax(gross);
            if(!churu.tryDeposit(p,gross-tax)){p.sendMessage(TextUtil.msg("<red>입금에 실패하여 거래를 취소했습니다."));return;}
            data.set(key,held-q);casino.depositJackpot(tax);
        }
        save.run();p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,0.7f,1.3f);openBroker(p);
    }
    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    public void refreshBoard(){
        Location at=broker.get();if(at==null||at.getWorld()==null)return;at.getChunk().load();
        List<TextDisplay> boards=at.getWorld().getEntitiesByClass(TextDisplay.class).stream().filter(e->e.getPersistentDataContainer().has(boardKey,PersistentDataType.BYTE)).toList();
        TextDisplay display=boards.isEmpty()?at.getWorld().spawn(at.clone().add(0,3.0,-1),TextDisplay.class):boards.getFirst();
        for(int i=1;i<boards.size();i++)boards.get(i).remove();
        display.teleport(at.clone().add(0,3.0,-1));
        display.getPersistentDataContainer().set(boardKey,PersistentDataType.BYTE,(byte)1);display.setBillboard(Display.Billboard.CENTER);display.setPersistent(true);
        StringBuilder text=new StringBuilder("<gold><bold>📈 츄르 증권 거래소</bold></gold>\n");
        for(Stock s:Stock.values())text.append("<white>").append(s.title).append(" <yellow>").append(TextUtil.fmt(price(s))).append(" 츄르</yellow></white>\n");
        display.text(TextUtil.mm(text+"<gray>10분 갱신 · 매도세 2% · 종목당 200주</gray>"));
    }
    private void guide(Player p){Location at=broker.get();if(at!=null&&p.getWorld().equals(at.getWorld()))p.setCompassTarget(at);p.sendMessage(TextUtil.msg("🧭 증권 거래는 동쪽 카지노 2층 서북 라운지의 증권 중개냥을 방문하세요."));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(sender instanceof Player p)guide(p);return true;}
    private ItemStack icon(Material m,String name,String...lore){ItemStack item=new ItemStack(m);ItemMeta meta=item.getItemMeta();meta.displayName(TextUtil.mm(name));meta.lore(TextUtil.lore(lore));item.setItemMeta(meta);return item;}
}
