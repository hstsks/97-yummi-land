package kr.yuumi97.core.trade;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 🤝 1:1 대면 안전 교환 시스템 ([Shift + 우클릭])
 * 무인 상점이나 우체통 없이, 접속 중인 지인끼리 광장에서 직접 만나 음성채팅으로 흥정하고
 * 물품과 츄르를 원자적(Atomic)으로 안전하게 맞교환합니다!
 */
public final class DirectTradeManager implements Listener {

    private static final int[] LEFT_SLOTS = {
            0, 1, 2, 3,
            9, 10, 11, 12,
            18, 19, 20, 21
    };
    private static final int[] RIGHT_SLOTS = {
            5, 6, 7, 8,
            14, 15, 16, 17,
            23, 24, 25, 26
    };

    public static final class TradeHolder implements InventoryHolder {
        public final TradeSession session;
        public TradeHolder(TradeSession session) { this.session = session; }
        @Override public Inventory getInventory() { return session.gui; }
    }

    public static final class TradeSession {
        final Player left;
        final Player right;
        final Inventory gui;
        double leftChuru = 0;
        double rightChuru = 0;
        boolean leftReady = false;
        boolean rightReady = false;
        boolean completedOrCancelled = false;
        BukkitTask countdownTask = null;

        TradeSession(Player left, Player right) {
            this.left = left;
            this.right = right;
            this.gui = Bukkit.createInventory(new TradeHolder(this), 54,
                    TextUtil.mm("<dark_aqua><bold>🤝 직거래: " + left.getName() + " ⇄ " + right.getName() + "</bold></dark_aqua>"));
        }
    }

    private record PendingInvite(UUID sender, long expiresAt) {}

    private final JavaPlugin plugin;
    private final ChuruManager churu;
    private final Map<UUID, PendingInvite> invitesByTarget = new HashMap<>();
    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();

    public DirectTradeManager(JavaPlugin plugin, ChuruManager churu) {
        this.plugin = plugin;
        this.churu = churu;
    }

    @EventHandler
    public void onShiftRightClickPlayer(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player actor = event.getPlayer();
        if (!actor.isSneaking()) return;
        if (actor.getLocation().distanceSquared(target.getLocation()) > 36.0) return;
        if (activeSessions.containsKey(actor.getUniqueId()) || activeSessions.containsKey(target.getUniqueId())) return;

        event.setCancelled(true);
        long now = System.currentTimeMillis();

        // Check if target already invited actor -> accept & open trade!
        PendingInvite reverse = invitesByTarget.get(actor.getUniqueId());
        if (reverse != null && reverse.sender.equals(target.getUniqueId()) && reverse.expiresAt > now) {
            invitesByTarget.remove(actor.getUniqueId());
            startTrade(target, actor);
            return;
        }

        // Send invite to target
        invitesByTarget.put(target.getUniqueId(), new PendingInvite(actor.getUniqueId(), now + 20_000L));
        actor.playSound(actor.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.9f, 1.3f);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
        actor.sendMessage(TextUtil.msg("🤝 <white>" + target.getName() + "</white>님에게 1:1 직거래를 요청했습니다! (20초 유효)"));
        target.sendMessage(TextUtil.msg("🤝 <yellow><bold>" + actor.getName() + "</bold></yellow>님이 1:1 자율 직거래를 요청했습니다! 수락하려면 마주 보고 <aqua><bold>[Shift + 우클릭]</bold></aqua> 하세요!"));
    }

    private void startTrade(Player p1, Player p2) {
        TradeSession session = new TradeSession(p1, p2);
        activeSessions.put(p1.getUniqueId(), session);
        activeSessions.put(p2.getUniqueId(), session);
        setupDecorations(session);
        refreshControls(session);
        p1.openInventory(session.gui);
        p2.openInventory(session.gui);
        p1.playSound(p1.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        p2.playSound(p2.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    private void setupDecorations(TradeSession s) {
        ItemStack sep = icon(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>│");
        for (int r = 0; r < 6; r++) {
            s.gui.setItem(r * 9 + 4, sep);
        }
        for (int slot = 27; slot < 54; slot++) {
            if (slot % 9 != 4) {
                s.gui.setItem(slot, icon(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
        // Churu adjustment buttons (Left player: 36, 37, 38, 39 | Right player: 41, 42, 43, 44)
        s.gui.setItem(36, icon(Material.PINK_DYE, "<#ff85a2><bold>+100 츄르 추가</bold>", "<gray>좌클릭: +100 │ 우클릭: -100"));
        s.gui.setItem(37, icon(Material.CYAN_DYE, "<aqua><bold>+1,000 츄르 추가</bold>", "<gray>좌클릭: +1,000 │ 우클릭: -1,000"));
        s.gui.setItem(38, icon(Material.GOLD_INGOT, "<gold><bold>+10,000 츄르 추가</bold>", "<gray>좌클릭: +10,000 │ 우클릭: -10,000"));

        s.gui.setItem(41, icon(Material.PINK_DYE, "<#ff85a2><bold>+100 츄르 추가</bold>", "<gray>좌클릭: +100 │ 우클릭: -100"));
        s.gui.setItem(42, icon(Material.CYAN_DYE, "<aqua><bold>+1,000 츄르 추가</bold>", "<gray>좌클릭: +1,000 │ 우클릭: -1,000"));
        s.gui.setItem(43, icon(Material.GOLD_INGOT, "<gold><bold>+10,000 츄르 추가</bold>", "<gray>좌클릭: +10,000 │ 우클릭: -10,000"));
    }

    private void refreshControls(TradeSession s) {
        s.gui.setItem(29, icon(Material.SUNFLOWER,
                "<gold><bold>" + s.left.getName() + "님의 제시 츄르</bold></gold>",
                "<white>제시 금액: " + TextUtil.churu(s.leftChuru),
                "<gray>아래 버튼으로 금액을 조절하세요."));
        s.gui.setItem(33, icon(Material.SUNFLOWER,
                "<gold><bold>" + s.right.getName() + "님의 제시 츄르</bold></gold>",
                "<white>제시 금액: " + TextUtil.churu(s.rightChuru),
                "<gray>아래 버튼으로 금액을 조절하세요."));

        s.gui.setItem(47, icon(s.leftReady ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                s.leftReady ? "<green><bold>✔ [" + s.left.getName() + "] 거래 확정 완료!</bold>"
                        : "<yellow><bold>▶ [" + s.left.getName() + "] 클릭하여 거래 확정</bold>",
                "<gray>물품과 츄르를 꼼꼼히 확인 후 눌러주세요!",
                "<dark_gray>※ 내용 변경 시 확정이 자동으로 풀립니다."));

        s.gui.setItem(51, icon(s.rightReady ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                s.rightReady ? "<green><bold>✔ [" + s.right.getName() + "] 거래 확정 완료!</bold>"
                        : "<yellow><bold>▶ [" + s.right.getName() + "] 클릭하여 거래 확정</bold>",
                "<gray>물품과 츄르를 꼼꼼히 확인 후 눌러주세요!",
                "<dark_gray>※ 내용 변경 시 확정이 자동으로 풀립니다."));

        s.gui.setItem(49, icon(
                (s.leftReady && s.rightReady) ? Material.NETHER_STAR : Material.CLOCK,
                (s.leftReady && s.rightReady) ? "<aqua><bold>⏳ 3초 후 안전 교환이 체결됩니다!</bold>"
                        : "<white><bold>양측 모두 [거래 확정]을 누르면 완료됩니다</bold>"
        ));
    }

    private void resetReady(TradeSession s) {
        if (s.countdownTask != null) {
            s.countdownTask.cancel();
            s.countdownTask = null;
        }
        s.leftReady = false;
        s.rightReady = false;
        refreshControls(s);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TradeHolder) {
            for (int raw : event.getRawSlots()) {
                if (raw < 54) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        TradeSession s = holder.session;
        if (s.completedOrCancelled) {
            event.setCancelled(true);
            return;
        }

        boolean isLeft = clicker.equals(s.left);
        boolean isRight = clicker.equals(s.right);
        if (!isLeft && !isRight) {
            event.setCancelled(true);
            return;
        }

        // Prevent shift-clicking straight into arbitrary slots; handle cleanly!
        if (event.isShiftClick()) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            if (raw >= 54) {
                // Move from player's own inventory into their trade side
                ItemStack cur = event.getCurrentItem();
                if (cur == null || cur.getType().isAir()) return;
                int[] mySlots = isLeft ? LEFT_SLOTS : RIGHT_SLOTS;
                for (int slot : mySlots) {
                    ItemStack existing = s.gui.getItem(slot);
                    if (existing == null || existing.getType().isAir()) {
                        s.gui.setItem(slot, cur.clone());
                        event.setCurrentItem(null);
                        resetReady(s);
                        clicker.playSound(clicker.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
                        return;
                    }
                }
            } else if (isOwnItemSlot(raw, isLeft)) {
                ItemStack inTrade = s.gui.getItem(raw);
                if (inTrade != null && !inTrade.getType().isAir()) {
                    Map<Integer, ItemStack> left = clicker.getInventory().addItem(inTrade);
                    if (left.isEmpty()) {
                        s.gui.setItem(raw, null);
                    } else {
                        s.gui.setItem(raw, left.values().iterator().next());
                    }
                    resetReady(s);
                    clicker.playSound(clicker.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 0.9f);
                }
            }
            return;
        }

        int raw = event.getRawSlot();
        if (raw >= 54) {
            // Clicking inside player's own bottom inventory (normal pickup/place)
            return;
        }

        // Inside top 54-slot trade GUI
        if (isOwnItemSlot(raw, isLeft)) {
            // Allowed to place/pickup items in own 12 slots -> reset ready on next tick
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!s.completedOrCancelled) resetReady(s);
            });
            return;
        }

        event.setCancelled(true);

        // Churu adjustment buttons
        if (isLeft && (raw == 36 || raw == 37 || raw == 38)) {
            double delta = (raw == 36 ? 100 : (raw == 37 ? 1000 : 10000)) * (event.isRightClick() ? -1 : 1);
            double next = Math.max(0.0, s.leftChuru + delta);
            if (next > churu.getBalance(s.left)) {
                s.left.sendMessage(TextUtil.msg("<red>지갑에 보유한 츄르보다 많이 올릴 수 없습니다!"));
                return;
            }
            s.leftChuru = next;
            s.left.playSound(s.left.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            resetReady(s);
            return;
        }
        if (isRight && (raw == 41 || raw == 42 || raw == 43)) {
            double delta = (raw == 41 ? 100 : (raw == 42 ? 1000 : 10000)) * (event.isRightClick() ? -1 : 1);
            double next = Math.max(0.0, s.rightChuru + delta);
            if (next > churu.getBalance(s.right)) {
                s.right.sendMessage(TextUtil.msg("<red>지갑에 보유한 츄르보다 많이 올릴 수 없습니다!"));
                return;
            }
            s.rightChuru = next;
            s.right.playSound(s.right.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            resetReady(s);
            return;
        }

        // Ready Toggle Buttons (47 for Left, 51 for Right)
        if (isLeft && raw == 47) {
            s.leftReady = !s.leftReady;
            s.left.playSound(s.left.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, s.leftReady ? 1.5f : 0.8f);
            checkStartCountdown(s);
        } else if (isRight && raw == 51) {
            s.rightReady = !s.rightReady;
            s.right.playSound(s.right.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, s.rightReady ? 1.5f : 0.8f);
            checkStartCountdown(s);
        }
    }

    private void checkStartCountdown(TradeSession s) {
        if (s.countdownTask != null) {
            s.countdownTask.cancel();
            s.countdownTask = null;
        }
        refreshControls(s);
        if (s.leftReady && s.rightReady) {
            s.left.playSound(s.left.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
            s.right.playSound(s.right.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
            s.countdownTask = Bukkit.getScheduler().runTaskLater(plugin, () -> finalizeTrade(s), 60L);
        }
    }

    private void finalizeTrade(TradeSession s) {
        if (s.completedOrCancelled || !s.leftReady || !s.rightReady) return;
        if (!s.left.isOnline() || !s.right.isOnline()) {
            cancelAndRefund(s, "상대방의 접속이 종료되어 거래가 취소되었습니다.");
            return;
        }
        if (!churu.has(s.left, s.leftChuru) || !churu.has(s.right, s.rightChuru)) {
            cancelAndRefund(s, "보유 츄르 잔액이 변동되어 거래가 취소되었습니다.");
            return;
        }

        s.completedOrCancelled = true;
        activeSessions.remove(s.left.getUniqueId());
        activeSessions.remove(s.right.getUniqueId());

        List<ItemStack> fromLeft = collectItems(s.gui, LEFT_SLOTS);
        List<ItemStack> fromRight = collectItems(s.gui, RIGHT_SLOTS);

        if (s.leftChuru > 0) {
            churu.withdraw(s.left, s.leftChuru);
            churu.deposit(s.right, s.leftChuru);
        }
        if (s.rightChuru > 0) {
            churu.withdraw(s.right, s.rightChuru);
            churu.deposit(s.left, s.rightChuru);
        }

        giveItemsSafely(s.right, fromLeft);
        giveItemsSafely(s.left, fromRight);

        s.left.closeInventory();
        s.right.closeInventory();
        s.left.playSound(s.left.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.4f);
        s.right.playSound(s.right.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.4f);
        s.left.sendMessage(TextUtil.msg("🤝 <white>" + s.right.getName() + "</white>님과의 1:1 자율 직거래가 성공적으로 완료되었습니다!"));
        s.right.sendMessage(TextUtil.msg("🤝 <white>" + s.left.getName() + "</white>님과의 1:1 자율 직거래가 성공적으로 완료되었습니다!"));
    }

    private void cancelAndRefund(TradeSession s, String reason) {
        if (s.completedOrCancelled) return;
        s.completedOrCancelled = true;
        if (s.countdownTask != null) s.countdownTask.cancel();
        activeSessions.remove(s.left.getUniqueId());
        activeSessions.remove(s.right.getUniqueId());

        giveItemsSafely(s.left, collectItems(s.gui, LEFT_SLOTS));
        giveItemsSafely(s.right, collectItems(s.gui, RIGHT_SLOTS));

        if (s.left.isOnline()) {
            s.left.closeInventory();
            s.left.sendMessage(TextUtil.msg("<yellow>" + reason + " (등록 물품 반환됨)"));
        }
        if (s.right.isOnline()) {
            s.right.closeInventory();
            s.right.sendMessage(TextUtil.msg("<yellow>" + reason + " (등록 물품 반환됨)"));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder holder)) return;
        TradeSession s = holder.session;
        if (!s.completedOrCancelled) {
            cancelAndRefund(s, "직거래 창이 닫혀 교환이 취소되었습니다.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invitesByTarget.remove(event.getPlayer().getUniqueId());
        TradeSession s = activeSessions.get(event.getPlayer().getUniqueId());
        if (s != null) {
            cancelAndRefund(s, "상대방이 퇴장하여 거래가 취소되었습니다.");
        }
    }

    private boolean isOwnItemSlot(int raw, boolean isLeft) {
        int[] arr = isLeft ? LEFT_SLOTS : RIGHT_SLOTS;
        for (int s : arr) if (s == raw) return true;
        return false;
    }

    private List<ItemStack> collectItems(Inventory inv, int[] slots) {
        List<ItemStack> list = new ArrayList<>();
        for (int slot : slots) {
            ItemStack st = inv.getItem(slot);
            if (st != null && !st.getType().isAir()) {
                list.add(st.clone());
                inv.setItem(slot, null);
            }
        }
        return list;
    }

    private void giveItemsSafely(Player p, List<ItemStack> items) {
        for (ItemStack st : items) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(st);
            for (ItemStack drop : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
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
