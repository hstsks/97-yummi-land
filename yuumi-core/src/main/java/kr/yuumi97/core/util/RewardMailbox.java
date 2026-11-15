package kr.yuumi97.core.util;

import kr.yuumi97.core.items.YuumiItems;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.UUID;

/** Persistent overflow queue, shared by dungeon, furniture and capsule rewards. Main-thread only. */
public final class RewardMailbox implements Listener {
    private final JavaPlugin plugin;
    private final FileConfiguration data;
    private final Runnable save;

    public RewardMailbox(JavaPlugin plugin, YuumiItems items, FileConfiguration data, Runnable save) {
        this.plugin=plugin; this.data=data; this.save=save;
    }

    public void enqueue(UUID owner, ItemStack stack) { enqueueOnce(owner, UUID.randomUUID().toString(), stack); }

    public void enqueueOnce(UUID owner, String transaction, ItemStack stack) {
        if(stack==null || stack.getType().isAir() || stack.getAmount()<=0) return;
        String key="rewards."+owner+"."+transaction;
        if(data.contains("reward_receipts."+owner+"."+transaction) || data.contains(key)) return;
        data.set(key,stack.clone()); save.run();
    }

    public void give(Player player, ItemStack stack) {
        enqueue(player.getUniqueId(),stack);
        deliver(player);
    }

    public void deliver(Player player) {
        String prefix="rewards."+player.getUniqueId();
        ConfigurationSection section=data.getConfigurationSection(prefix);
        if(section==null) return;
        for(String id:new ArrayList<>(section.getKeys(false))) {
            ItemStack stack=section.getItemStack(id);
            if(stack==null) {data.set(prefix+"."+id,null);continue;}
            var remaining=player.getInventory().addItem(stack.clone());
            if(remaining.isEmpty()) {
                data.set(prefix+"."+id,null);
                data.set("reward_receipts."+player.getUniqueId()+"."+id,true);
            } else {
                int left=remaining.values().stream().mapToInt(ItemStack::getAmount).sum();
                ItemStack rest=stack.clone();rest.setAmount(left);data.set(prefix+"."+id,rest);
            }
            save.run();
            if(!remaining.isEmpty()) break;
        }
        if(data.getConfigurationSection(prefix)!=null && !data.getConfigurationSection(prefix).getKeys(false).isEmpty())
            player.sendMessage(TextUtil.msg("<yellow>가방에 들어가지 않은 보상이 보관되어 있습니다. 공간을 비우고 /메뉴 보상 으로 수령하세요."));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline()) deliver(event.getPlayer());},60L);
    }
}
