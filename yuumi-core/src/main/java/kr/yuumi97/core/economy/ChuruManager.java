package kr.yuumi97.core.economy;

import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChuruManager implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final FileConfiguration data;
    private final Runnable saveCallback;
    private Economy vaultEco;

    public ChuruManager(JavaPlugin plugin, YuumiItems items, FileConfiguration data, Runnable saveCallback) {
        this.plugin = plugin;
        this.items = items;
        this.data = data;
        this.saveCallback = saveCallback;
        hookVault();
    }

    public void hookVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.vaultEco = rsp.getProvider();
            plugin.getLogger().info("[Economy] Hooked Vault Economy provider: " + vaultEco.getName());
        }
    }

    private Economy getEco() {
        if (vaultEco == null) hookVault();
        return vaultEco;
    }

    /** One initialization gate shared by currency and the starter kit. */
    public boolean initializePlayer(Player player) {
        String key = "players." + player.getUniqueId() + ".initialized";
        if (!data.getBoolean(key, false)) {
            data.set(key, true);
            double start = plugin.getConfig().getDouble("server-theme.starting-churu", 1500.0);
            if (getBalance(player) < start) {
                deposit(player, start - getBalance(player));
            }
            saveCallback.run();
            return true;
        }
        return false;
    }

    public double getBalance(OfflinePlayer player) {
        Economy eco = getEco();
        if (eco != null) {
            return eco.getBalance(player);
        }
        return data.getDouble("wallets." + player.getUniqueId(), 0.0);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return Double.isFinite(amount) && amount >= 0 && getBalance(player) + 0.001 >= amount;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        if (amount <= 0) return true;
        if (!has(player, amount)) return false;
        Economy eco = getEco();
        if (eco != null) {
            return eco.withdrawPlayer(player, amount).transactionSuccess();
        }
        UUID id = player.getUniqueId();
        double cur = getBalance(player);
        data.set("wallets." + id, Math.max(0.0, cur - amount));
        saveCallback.run();
        return true;
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (!tryDeposit(player, amount)) plugin.getLogger().warning("[Economy] Deposit rejected for " + player.getUniqueId());
    }

    public boolean tryDeposit(OfflinePlayer player, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        if (amount == 0) return true;
        Economy eco = getEco();
        if (eco != null) {
            return eco.depositPlayer(player, amount).transactionSuccess();
        }
        UUID id = player.getUniqueId();
        data.set("wallets." + id, getBalance(player) + amount);
        saveCallback.run();
        return true;
    }

    // ==================== 은행 안전 예금 계좌 (사망 구조비 면제!) ====================

    public double getBankBalance(OfflinePlayer player) {
        return Math.max(0.0, data.getDouble("bank." + player.getUniqueId(), 0.0));
    }

    public void setBankBalance(OfflinePlayer player, double amount) {
        data.set("bank." + player.getUniqueId(), Math.max(0.0, amount));
        saveCallback.run();
    }

    public boolean depositToBank(Player player, double amount) {
        if (amount <= 0) return false;
        if (!withdraw(player, amount)) return false;
        setBankBalance(player, getBankBalance(player) + amount);
        return true;
    }

    public boolean withdrawFromBank(Player player, double amount) {
        if (amount <= 0) return false;
        double cur = getBankBalance(player);
        if (cur + 0.001 < amount) return false;
        setBankBalance(player, Math.max(0.0, cur - amount));
        deposit(player, amount);
        return true;
    }

    public boolean mintVoucherAtBank(Player player, int unit, int count) {
        if (unit != 100 && unit != 1000 && unit != 10000) return false;
        double totalCost = (double) unit * count;
        if (!withdraw(player, totalCost)) return false;
        ItemStack voucher = switch (unit) {
            case 100 -> items.churuSalmon(count);
            case 1000 -> items.churuTuna(count);
            default -> items.churuGolden(count);
        };
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(voucher);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(TextUtil.msg("현재 보유 자산 — 지갑: " + TextUtil.churu(getBalance(player))
                    + " <gray>│ 은행 예금: <aqua><bold>" + TextUtil.fmt(getBankBalance(player)) + " 츄르</bold></aqua>"));
            player.sendMessage(TextUtil.mm("<gray> · 예금/출금 및 실물 수표 발행: <yellow>허브 서쪽 [츄르 은행] 창구 방문</yellow>"));
            player.sendMessage(TextUtil.mm("<gray> · 지인과 물품·츄르 직거래: <yellow>마주 보고 [Shift + 우클릭]</yellow>"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("수표") || sub.equals("인출") || sub.equals("withdraw")) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 1.1f);
            player.sendMessage(TextUtil.msg("🏦 실물 츄르 수표 인출 및 안전 예금은 <yellow>고양이 허브 도시 서쪽 [츄르 은행]</yellow> 창구에서 이용하실 수 있습니다!"));
            return true;
        }

        if (sub.equals("송금") || sub.equals("보내기") || sub.equals("pay")) {
            if (args.length < 3) {
                player.sendMessage(TextUtil.msg("사용법: <yellow>/츄르 송금 <닉네임> <금액></yellow>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || target.equals(player)) {
                player.sendMessage(TextUtil.msg("<red>접속 중인 다른 지인의 닉네임을 정확히 입력해주세요!"));
                return true;
            }
            double amount;
            try {
                amount = Math.floor(Double.parseDouble(args[2]));
            } catch (NumberFormatException e) {
                player.sendMessage(TextUtil.msg("<red>올바른 숫자를 입력해주세요."));
                return true;
            }
            if (amount < 10) {
                player.sendMessage(TextUtil.msg("<red>최소 10 츄르 이상부터 송금할 수 있습니다."));
                return true;
            }
            if (!withdraw(player, amount)) {
                player.sendMessage(TextUtil.msg("<red>보유 츄르가 부족합니다!"));
                return true;
            }
            deposit(target, amount);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            target.playSound(target.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 1.2f);
            player.sendMessage(TextUtil.msg("<white>" + target.getName() + "</white>님에게 " + TextUtil.churu(amount) + "<gray>를 보냈습니다!"));
            target.sendMessage(TextUtil.msg("<white>" + player.getName() + "</white>님으로부터 " + TextUtil.churu(amount) + "<gray>를 선물받았습니다! 🐾"));
            return true;
        }

        if (sub.equals("지급") && player.isOp() && args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                double amt = Double.parseDouble(args[2]);
                deposit(target, amt);
                player.sendMessage(TextUtil.msg("[관리자] " + target.getName() + "님에게 " + TextUtil.churu(amt) + " 지급 완료."));
            }
            return true;
        }

        player.sendMessage(TextUtil.msg("현재 보유 자산: " + TextUtil.churu(getBalance(player))));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("수표", "송금");
        }
        if (args.length == 2 && args[0].equals("수표")) {
            return Arrays.asList("100", "1000", "10000");
        }
        if (args.length == 2 && args[0].equals("송금")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        return List.of();
    }
}
