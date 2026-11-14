package kr.yuumi97.core.facility;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 🏦 허브 서쪽 『츄르 은행 & 개인 금고 보관소』
 * 1) 지갑 ↔ 은행 안전 예금 계좌 (야생 사망 시 8% 구조비 차감 완벽 보호!)
 * 2) 실물 츄르 스틱 수표 (100 / 1,000 / 10,000) 발행 창구
 * 3) 플레이어별 최대 4페이지(27칸 × 4탭 = 108칸) 확장형 개인 금고
 */
public final class BankAndVaultManager implements Listener {

    public enum Mode { BANK_COUNTER, PERSONAL_VAULT }

    public static final class BankHolder implements InventoryHolder {
        public final Mode mode;
        public final int vaultPage; // 1..4
        public BankHolder(Mode mode, int vaultPage) {
            this.mode = mode;
            this.vaultPage = vaultPage;
        }
        @Override public Inventory getInventory() { return null; }
    }

    private static final double[] VAULT_UNLOCK_COSTS = {0.0, 0.0, 5000.0, 15000.0, 35000.0};

    private final JavaPlugin plugin;
    private final ChuruManager churu;
    private final FileConfiguration data;
    private final Runnable saveCallback;
    private final File vaultsDir;

    public BankAndVaultManager(JavaPlugin plugin, ChuruManager churu, FileConfiguration data, Runnable saveCallback) {
        this.plugin = plugin;
        this.churu = churu;
        this.data = data;
        this.saveCallback = saveCallback;
        this.vaultsDir = new File(plugin.getDataFolder(), "vaults");
        if (!vaultsDir.exists()) vaultsDir.mkdirs();
    }

    // ==================== 1. 츄르 은행 예금 & 수표 창구 ====================

    public void openBankCounter(Player player) {
        Inventory inv = Bukkit.createInventory(new BankHolder(Mode.BANK_COUNTER, 1), 45,
                TextUtil.mm("<gold><bold>🏦 츄르 중앙 은행 창구</bold></gold>"));

        ItemStack bg = icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        double wallet = churu.getBalance(player);
        double bank = churu.getBankBalance(player);

        inv.setItem(4, icon(Material.RAW_GOLD_BLOCK,
                "<gradient:#ffe14d:#ff85c0><bold>📒 " + player.getName() + "님의 통장 현황</bold></gradient>",
                "<white>현재 지갑 잔액: " + TextUtil.churu(wallet),
                "<aqua>은행 안전 예금: <bold>" + TextUtil.fmt(bank) + " 츄르</bold></aqua>",
                "",
                "<green>✦ 예금 혜택: 야생·던전 사망 시 부과되는",
                "<green>  긴급 구조비(지갑의 8%)로부터 100% 보호됩니다!"
        ));

        // Row 2: Deposit (Wallet -> Bank)
        inv.setItem(19, icon(Material.EMERALD, "<green><bold>[예금] 1,000 츄르 입금</bold>",
                "<gray>지갑에서 은행 계좌로 1,000 츄르를 안전하게 맡깁니다.", "<yellow>▶ 클릭하여 예금"));
        inv.setItem(20, icon(Material.EMERALD_BLOCK, "<green><bold>[예금] 10,000 츄르 입금</bold>",
                "<gray>지갑에서 은행 계좌로 10,000 츄르를 맡깁니다.", "<yellow>▶ 클릭하여 예금"));
        inv.setItem(21, icon(Material.CHEST_MINECART, "<aqua><bold>[예금] 지갑 전액 예금하기!</bold>",
                "<gray>원정 출발 전 지갑의 모든 츄르를 은행에 안전 보관합니다!", "<yellow>▶ 클릭하여 전액 예금"));

        // Row 2 Right: Withdraw (Bank -> Wallet)
        inv.setItem(23, icon(Material.GOLD_NUGGET, "<yellow><bold>[출금] 1,000 츄르 찾기</bold>",
                "<gray>은행 계좌에서 지갑으로 1,000 츄르를 찾습니다.", "<yellow>▶ 클릭하여 출금"));
        inv.setItem(24, icon(Material.GOLD_INGOT, "<yellow><bold>[출금] 10,000 츄르 찾기</bold>",
                "<gray>은행 계좌에서 지갑으로 10,000 츄르를 찾습니다.", "<yellow>▶ 클릭하여 출금"));
        inv.setItem(25, icon(Material.HOPPER_MINECART, "<gold><bold>[출금] 예금 전액 찾기</bold>",
                "<gray>은행 예금을 모두 지갑으로 인출합니다.", "<yellow>▶ 클릭하여 전액 출금"));

        // Row 4: Physical Churu Stick Voucher Minting
        inv.setItem(30, icon(Material.PAPER, "<#ff85a2><bold>[수표 발행] 연어맛 츄르 스틱 (100)</bold>",
                "<gray>지갑 츄르를 실물 수표 스틱으로 인출합니다.",
                "<yellow>▶ 좌클릭: 1개 │ 쉬프트+클릭: 10개"));
        inv.setItem(31, icon(Material.PAPER, "<aqua><bold>[수표 발행] 참치맛 츄르 스틱 (1,000)</bold>",
                "<gray>지갑 츄르를 중액권 수표 스틱으로 인출합니다.",
                "<yellow>▶ 좌클릭: 1개 │ 쉬프트+클릭: 10개"));
        inv.setItem(32, icon(Material.PAPER, "<gold><bold>[수표 발행] 전설의 황금 츄르 (10,000)</bold>",
                "<gray>고액 수표이자 전설 유물 재료인 황금 츄르를 발행합니다.",
                "<yellow>▶ 좌클릭: 1개 │ 쉬프트+클릭: 5개"));

        player.openInventory(inv);
    }

    // ==================== 2. 확장형 개인 금고 (1~4페이지) ====================

    public int getUnlockedVaultPages(UUID uuid) {
        return Math.max(1, data.getInt("vault_pages." + uuid, 1));
    }

    public void openPersonalVault(Player player, int page) {
        int unlocked = getUnlockedVaultPages(player.getUniqueId());
        int targetPage = Math.max(1, Math.min(unlocked, page));

        Inventory inv = Bukkit.createInventory(new BankHolder(Mode.PERSONAL_VAULT, targetPage), 36,
                TextUtil.mm("<dark_aqua><bold>🔐 개인 금고 보관함 (" + targetPage + "/" + unlocked + "페이지)</bold></dark_aqua>"));

        // Load saved 27 items for targetPage
        File vf = new File(vaultsDir, player.getUniqueId() + ".yml");
        if (vf.exists()) {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(vf);
            List<?> rawList = yc.getList("page_" + targetPage);
            if (rawList != null) {
                for (int i = 0; i < Math.min(27, rawList.size()); i++) {
                    Object obj = rawList.get(i);
                    if (obj instanceof ItemStack st) {
                        inv.setItem(i, st);
                    }
                }
            }
        }

        // Bottom navigation bar (slots 27..35)
        ItemStack bar = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) inv.setItem(i, bar);

        for (int p = 1; p <= 4; p++) {
            int slot = 28 + p; // slots 29, 30, 31, 32
            if (p <= unlocked) {
                inv.setItem(slot, icon(p == targetPage ? Material.ENDER_CHEST : Material.CHEST,
                        (p == targetPage ? "<aqua><bold>▶ 현재 열람 중: " : "<green><bold>✔ 이동: ") + p + "번 금고 탭</bold>",
                        "<gray>27칸 안전 보관함 (사망·약탈 걱정 없음!)",
                        p == targetPage ? "<dark_gray>[열려 있음]" : "<yellow>▶ 클릭하여 " + p + "페이지 열기"));
            } else if (p == unlocked + 1) {
                double cost = VAULT_UNLOCK_COSTS[p];
                inv.setItem(slot, icon(Material.GOLD_BLOCK,
                        "<gold><bold>🔓 [" + p + "번 금고 탭 확장하기]</bold></gold>",
                        "<gray>영구적으로 개인 금고 27칸을 추가 개방합니다!",
                        "<white>확장 비용: " + TextUtil.churu(cost),
                        "",
                        "<yellow>▶ 클릭하여 즉시 확장"));
            } else {
                inv.setItem(slot, icon(Material.BARRIER,
                        "<red><bold>🔒 " + p + "번 금고 탭 (잠김)</bold></red>",
                        "<gray>이전 금고 탭을 먼저 확장해 주세요."));
            }
        }

        player.openInventory(inv);
    }

    private void saveVaultPage(UUID uuid, int page, Inventory inv) {
        File vf = new File(vaultsDir, uuid + ".yml");
        YamlConfiguration yc = vf.exists() ? YamlConfiguration.loadConfiguration(vf) : new YamlConfiguration();
        ItemStack[] arr = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            arr[i] = inv.getItem(i);
        }
        yc.set("page_" + page, arr);
        try {
            yc.save(vf);
        } catch (IOException e) {
            plugin.getLogger().warning("[Vault] Failed to save personal vault for " + uuid + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BankHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();

        if (holder.mode == Mode.BANK_COUNTER) {
            event.setCancelled(true);
            if (raw < 0 || raw >= 45) return;
            handleBankCounterClick(player, raw, event.isShiftClick());
            return;
        }

        // PERSONAL_VAULT mode: slots 0..26 are freely editable storage; slots 27..35 are locked nav buttons!
        if (raw >= 27 && raw < 36) {
            event.setCancelled(true);
            int targetTab = raw - 28; // 29->1, 30->2, 31->3, 32->4
            if (targetTab < 1 || targetTab > 4) return;

            int unlocked = getUnlockedVaultPages(player.getUniqueId());
            if (targetTab <= unlocked) {
                if (targetTab != holder.vaultPage) {
                    saveVaultPage(player.getUniqueId(), holder.vaultPage, event.getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.2f);
                    openPersonalVault(player, targetTab);
                }
            } else if (targetTab == unlocked + 1) {
                double cost = VAULT_UNLOCK_COSTS[targetTab];
                if (!churu.withdraw(player, cost)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
                    player.sendMessage(TextUtil.msg("<red>금고 확장을 위한 지갑 츄르가 부족합니다! (필요: " + TextUtil.fmt(cost) + " 츄르)"));
                    return;
                }
                saveVaultPage(player.getUniqueId(), holder.vaultPage, event.getInventory());
                data.set("vault_pages." + player.getUniqueId(), targetTab);
                saveCallback.run();
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.3f);
                player.sendMessage(TextUtil.msg("🔐 개인 금고 <aqua>" + targetTab + "번 페이지(+27칸)</aqua>가 영구 확장되었습니다!"));
                openPersonalVault(player, targetTab);
            }
        }
    }

    private void handleBankCounterClick(Player player, int slot, boolean shift) {
        switch (slot) {
            case 19 -> doDeposit(player, 1000);
            case 20 -> doDeposit(player, 10000);
            case 21 -> doDeposit(player, Math.floor(churu.getBalance(player)));
            case 23 -> doWithdraw(player, 1000);
            case 24 -> doWithdraw(player, 10000);
            case 25 -> doWithdraw(player, Math.floor(churu.getBankBalance(player)));
            case 30 -> doMint(player, 100, shift ? 10 : 1);
            case 31 -> doMint(player, 1000, shift ? 10 : 1);
            case 32 -> doMint(player, 10000, shift ? 5 : 1);
        }
    }

    private void doDeposit(Player player, double amt) {
        if (amt <= 0 || !churu.depositToBank(player, amt)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>예금할 지갑 츄르가 부족합니다!"));
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        player.sendMessage(TextUtil.msg("🏦 은행 안전 계좌에 " + TextUtil.churu(amt) + "<gray>를 예금했습니다! (예금 잔액: " + TextUtil.fmt(churu.getBankBalance(player)) + ")"));
        openBankCounter(player);
    }

    private void doWithdraw(Player player, double amt) {
        if (amt <= 0 || !churu.withdrawFromBank(player, amt)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>은행 계좌의 예금 잔액이 부족합니다!"));
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 1.2f);
        player.sendMessage(TextUtil.msg("🏦 은행 계좌에서 지갑으로 " + TextUtil.churu(amt) + "<gray>를 찾았습니다!"));
        openBankCounter(player);
    }

    private void doMint(Player player, int unit, int count) {
        if (!churu.mintVoucherAtBank(player, unit, count)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>수표 발행을 위한 지갑 츄르가 부족합니다! (예금 중인 경우 먼저 출금해 주세요)"));
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 1.35f);
        player.sendMessage(TextUtil.msg("🎫 실물 츄르 수표 스틱으로 " + TextUtil.churu((double) unit * count) + "<gray>를 발행했습니다!"));
        openBankCounter(player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BankHolder holder)) return;
        if (holder.mode == Mode.PERSONAL_VAULT && event.getPlayer() instanceof Player player) {
            saveVaultPage(player.getUniqueId(), holder.vaultPage, event.getInventory());
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.7f, 1.1f);
        }
    }

    private ItemStack icon(Material mat, String name, String... lore) {
        ItemStack st = new ItemStack(mat);
        ItemMeta m = st.getItemMeta();
        if (m != null) {
            m.displayName(TextUtil.mm(name));
            if (lore.length > 0) m.lore(TextUtil.lore(lore));
            st.setItemMeta(m);
        }
        return st;
    }
}
