package kr.yuumi97.core.plot;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 🏡 『고양이 주거 · 농장 분양 단지』 (Plot Real-Estate System)
 * 야생 무분별 청크 보호를 폐기하고, 평화로운 허브 도시(yuumi_hub) 남쪽 벚꽃 대교 너머의
 * 지정된 12필지(소형 6 · 중형 4 · 대형 별장 2)를 츄르로 분양받아 이웃과 마을을 이루는 시스템입니다.
 */
public final class PlotManager implements Listener, CommandExecutor {

    public enum PlotTier {
        SMALL("아기냥 입주 부지 (15×15)", 4000.0, 56, 84),
        MEDIUM("치즈냥 정원 부지 (23×23)", 18000.0, 52, 92),
        LARGE("황실 유미 별장 부지 (33×33)", 55000.0, 48, 102);

        public final String display;
        public final double defaultPrice;
        public final int minY;
        public final int maxY;

        PlotTier(String display, double defaultPrice, int minY, int maxY) {
            this.display = display;
            this.defaultPrice = defaultPrice;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    public record PlotSpec(
            String id,
            PlotTier tier,
            int minX, int maxX,
            int minZ, int maxZ,
            int signX, int signZ
    ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= tier.minY && y <= tier.maxY;
        }
        public int centerX() { return (minX + maxX) / 2; }
        public int centerZ() { return (minZ + maxZ) / 2; }
        public int sizeX() { return maxX - minX + 1; }
        public int sizeZ() { return maxZ - minZ + 1; }
    }

    public enum GuiMode { REGISTRY, PLOT_DETAIL, TRUST_LIST, CONFIRM_SELL }

    public static final class PlotGuiHolder implements InventoryHolder {
        public final GuiMode mode;
        public final String plotId;
        public final List<UUID> displayedFriends = new ArrayList<>();
        public PlotGuiHolder(GuiMode mode, String plotId) {
            this.mode = mode;
            this.plotId = plotId;
        }
        @Override public Inventory getInventory() { return null; }
    }

    public static final int GROUND_Y = 64;

    private final JavaPlugin plugin;
    private final ChuruManager churu;
    private final FileConfiguration data;
    private final Runnable saveCallback;
    private final Supplier<World> hubWorldSupplier;
    private BiConsumer<World, PlotSpec> starterCottageBuilder;
    private Consumer<PlotSpec> beforePlotReset = spec -> {};

    private final Map<String, PlotSpec> plots = new LinkedHashMap<>();

    public PlotManager(JavaPlugin plugin, ChuruManager churu, FileConfiguration data,
                       Runnable saveCallback, Supplier<World> hubWorldSupplier) {
        this.plugin = plugin;
        this.churu = churu;
        this.data = data;
        this.saveCallback = saveCallback;
        this.hubWorldSupplier = hubWorldSupplier;
        initPlotCatalog();
    }

    public void setStarterCottageBuilder(BiConsumer<World, PlotSpec> builder) {
        this.starterCottageBuilder = builder;
    }

    /** Runs while ownership still exists, before blocks or furniture are removed. */
    public void setBeforePlotReset(Consumer<PlotSpec> callback) {
        this.beforePlotReset = callback == null ? spec -> {} : callback;
    }

    /** Migration protection deliberately covers the complete vertical column. */
    public boolean isOwnedColumn(int x, int z) {
        for (PlotSpec spec : plots.values()) {
            if (x >= spec.minX() && x <= spec.maxX()
                    && z >= spec.minZ() && z <= spec.maxZ() && getOwner(spec.id()) != null) return true;
        }
        return false;
    }

    private void initPlotCatalog() {
        // 6 Small Starter Plots (15x15)
        reg(new PlotSpec("S-1", PlotTier.SMALL, -24, -10, 60, 74, -8, 67));
        reg(new PlotSpec("S-2", PlotTier.SMALL,  10,  24, 60, 74,  8, 67));
        reg(new PlotSpec("S-3", PlotTier.SMALL, -24, -10, 82, 96, -8, 89));
        reg(new PlotSpec("S-4", PlotTier.SMALL,  10,  24, 82, 96,  8, 89));
        reg(new PlotSpec("S-5", PlotTier.SMALL, -46, -32, 60, 74, -30, 67));
        reg(new PlotSpec("S-6", PlotTier.SMALL,  32,  46, 60, 74,  30, 67));

        // 4 Medium Garden Plots (23x23)
        reg(new PlotSpec("M-1", PlotTier.MEDIUM, -32, -10, 106, 128, -8, 117));
        reg(new PlotSpec("M-2", PlotTier.MEDIUM,  10,  32, 106, 128,  8, 117));
        reg(new PlotSpec("M-3", PlotTier.MEDIUM, -32, -10, 136, 158, -8, 147));
        reg(new PlotSpec("M-4", PlotTier.MEDIUM,  10,  32, 136, 158,  8, 147));

        // 2 Large Villa & Farm Estates (33x33)
        reg(new PlotSpec("L-1", PlotTier.LARGE, -42, -10, 168, 200, -8, 184));
        reg(new PlotSpec("L-2", PlotTier.LARGE,  10,  42, 168, 200,  8, 184));
    }

    private void reg(PlotSpec spec) {
        plots.put(spec.id(), spec);
    }

    public Map<String, PlotSpec> getAllPlots() {
        return plots;
    }

    public double getPlotPrice(PlotSpec spec) {
        return switch (spec.tier()) {
            case SMALL -> plugin.getConfig().getDouble("plots.small-price", 4000.0);
            case MEDIUM -> plugin.getConfig().getDouble("plots.medium-price", 18000.0);
            case LARGE -> plugin.getConfig().getDouble("plots.large-price", 55000.0);
        };
    }

    public UUID getOwner(String plotId) {
        String raw = data.getString("plots." + plotId + ".owner");
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public PlotSpec getOwnedPlot(UUID playerUuid) {
        for (PlotSpec spec : plots.values()) {
            if (playerUuid.equals(getOwner(spec.id()))) return spec;
        }
        return null;
    }

    public PlotSpec getPlotAt(Location loc) {
        World hub = hubWorldSupplier.get();
        if (loc == null || hub == null || !hub.equals(loc.getWorld())) return null;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        for (PlotSpec spec : plots.values()) {
            if (spec.contains(x, y, z)) return spec;
        }
        return null;
    }

    public PlotSpec getPlotBySignBlock(Block b) {
        World hub = hubWorldSupplier.get();
        if (b == null || hub == null || !hub.equals(b.getWorld())) return null;
        int bx = b.getX(), by = b.getY(), bz = b.getZ();
        if (by < GROUND_Y || by > GROUND_Y + 4) return null;
        for (PlotSpec spec : plots.values()) {
            if (Math.abs(bx - spec.signX()) <= 1 && Math.abs(bz - spec.signZ()) <= 1) {
                return spec;
            }
        }
        return null;
    }

    public Location getHomeEntranceOrNull(UUID playerUuid) {
        World hub = hubWorldSupplier.get();
        if (hub == null) return null;
        PlotSpec myPlot = getOwnedPlot(playerUuid);
        if (myPlot == null) {
            // Check if co-resident on a friend's plot!
            for (PlotSpec spec : plots.values()) {
                UUID owner = getOwner(spec.id());
                if (owner != null && isTrusted(owner, playerUuid)) {
                    myPlot = spec;
                    break;
                }
            }
        }
        if (myPlot == null) return null;
        float yaw = myPlot.signX() < 0 ? 90f : -90f;
        return new Location(hub, myPlot.signX() + (myPlot.signX() < 0 ? 1.5 : -0.5), GROUND_Y + 1.0, myPlot.signZ() + 0.5, yaw, 0f);
    }

    public boolean isTrusted(UUID owner, UUID actor) {
        if (owner.equals(actor)) return true;
        return data.getStringList("trust." + owner).contains(actor.toString());
    }

    public void toggleTrust(UUID owner, UUID friend) {
        String path = "trust." + owner;
        List<String> list = new ArrayList<>(data.getStringList(path));
        if (list.contains(friend.toString())) list.remove(friend.toString());
        else list.add(friend.toString());
        data.set(path, list);
        saveCallback.run();
    }

    public boolean canBuildInHub(Player player, Location loc) {
        if (player.isOp() && player.getGameMode() == GameMode.CREATIVE) return true;
        PlotSpec spec = getPlotAt(loc);
        if (spec == null) return false; // Public Hub roads/plaza/buildings are 100% protected!
        UUID owner = getOwner(spec.id());
        if (owner == null) return false; // Unsold plot is protected until purchased!
        return isTrusted(owner, player.getUniqueId());
    }

    public void refreshAllPlotSigns() {
        World hub = hubWorldSupplier.get();
        if (hub == null) return;
        for (PlotSpec spec : plots.values()) {
            refreshPlotSign(hub, spec);
        }
    }

    public void refreshPlotSign(World hub, PlotSpec spec) {
        Block post = hub.getBlockAt(spec.signX(), GROUND_Y + 1, spec.signZ());
        post.setType(Material.CHERRY_FENCE, false);
        Block signBlock = hub.getBlockAt(spec.signX(), GROUND_Y + 2, spec.signZ());
        if (signBlock.getType() != Material.CHERRY_SIGN) {
            signBlock.setType(Material.CHERRY_SIGN, false);
        }
        if (signBlock.getState() instanceof Sign sign) {
            UUID owner = getOwner(spec.id());
            var front = sign.getSide(Side.FRONT);
            var back = sign.getSide(Side.BACK);
            String l0 = "<bold>[" + spec.id() + "호 부지]</bold>";
            String l1 = spec.sizeX() + "×" + spec.sizeZ() + " 건축지";
            String l2 = owner == null ? "<green><bold>● 미분양 매물</bold></green>" : "<aqua><bold>" + getName(owner) + "</bold></aqua>";
            String l3 = owner == null ? "<gold>" + TextUtil.fmt(getPlotPrice(spec)) + " 츄르</gold>" : "<gray>[우클릭 정보]</gray>";
            front.line(0, TextUtil.mm(l0)); front.line(1, TextUtil.mm(l1)); front.line(2, TextUtil.mm(l2)); front.line(3, TextUtil.mm(l3));
            back.line(0, TextUtil.mm(l0));  back.line(1, TextUtil.mm(l1));  back.line(2, TextUtil.mm(l2));  back.line(3, TextUtil.mm(l3));
            sign.setWaxed(true);
            sign.update(true, false);
        }
    }

    // ==================== 현장 표지판 우클릭 ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickPlotSign(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block b = event.getClickedBlock();
        if (!b.getType().name().contains("SIGN")) return;
        PlotSpec spec = getPlotBySignBlock(b);
        if (spec == null) return;

        event.setCancelled(true);
        openPlotDetailGui(event.getPlayer(), spec);
    }

    // ==================== 1. 마을 회관 토지 등기소 종합 GUI ====================

    public void openRegistryGui(Player player) {
        Inventory inv = Bukkit.createInventory(new PlotGuiHolder(GuiMode.REGISTRY, null), 45,
                TextUtil.mm("<#ff85c0><bold>🏛 고양이 주거 단지 토지 등기소</bold></#ff85c0>"));

        ItemStack bg = icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        PlotSpec myOwned = getOwnedPlot(player.getUniqueId());
        inv.setItem(4, icon(Material.CHERRY_HANGING_SIGN,
                "<gradient:#ff85c0:#ffd666><bold>🏡 97랜드 남쪽 주거·농장 분양 현황</bold></gradient>",
                "<white>내 보유 지갑: " + TextUtil.churu(churu.getBalance(player)),
                "<white>내 소유 부지: " + (myOwned != null ? "<aqua><bold>" + myOwned.id() + "호 (" + myOwned.tier().display + ")</bold></aqua>" : "<gray>없음 (미분양 부지를 클릭해 보세요!)"),
                "",
                "<gray>· 중앙 광장 남쪽 <yellow>벚꽃 아치 대교</yellow>를 건너면 분양 단지가 펼쳐집니다!",
                "<gray>· 1인당 1필지 분양 원칙 (동거 지인 등록으로 합가·대저택 공동 건축 가능!)"
        ));

        // Place 12 plots neatly across rows 2, 3, 4
        String[] order = {"S-5", "S-1", "S-2", "S-6", "S-3", "S-4", "M-1", "M-2", "M-3", "M-4", "L-1", "L-2"};
        int[] slots =    { 10,    11,    12,    13,    15,    16,    20,    21,    23,    24,    30,    32   };

        for (int i = 0; i < order.length; i++) {
            PlotSpec spec = plots.get(order[i]);
            UUID owner = getOwner(spec.id());
            Material mat = owner == null ? Material.LIME_TERRACOTTA
                    : (owner.equals(player.getUniqueId()) ? Material.LIGHT_BLUE_TERRACOTTA : Material.PINK_TERRACOTTA);
            double price = getPlotPrice(spec);

            inv.setItem(slots[i], icon(mat,
                    (owner == null ? "<green><bold>● [분양 가능] " : (owner.equals(player.getUniqueId()) ? "<aqua><bold>★ [내 집] " : "<#ff85c0><bold>🐾 [입주 완료] "))
                            + spec.id() + "호 — " + spec.tier().display + "</bold>",
                    "<gray>부지 위치: <white>X(" + spec.minX() + "~" + spec.maxX() + "), Z(" + spec.minZ() + "~" + spec.maxZ() + ")",
                    "<gray>건축 허용 높이: <white>Y " + spec.tier().minY + " ~ " + spec.tier().maxY,
                    owner == null
                            ? "<gold>✦ 분양가: <bold>" + TextUtil.fmt(price) + " 츄르</bold></gold>"
                            : "<white>✦ 세대주: <yellow>" + getName(owner) + "</yellow>",
                    "",
                    "<yellow>▶ 클릭하여 상세 메뉴 / 분양 / 위치 안내"
            ));
        }

        inv.setItem(40, icon(Material.PLAYER_HEAD,
                "<aqua><bold>🤝 내 집 동거인(상자·건축 공유) 지인 관리</bold></aqua>",
                "<gray>원하는 지인을 등록해 한집살이 또는 공동 별장을 꾸미세요!",
                "<yellow>▶ 클릭하여 동거인 설정"));

        player.openInventory(inv);
    }

    // ==================== 2. 개별 부지 상세 계약서 GUI ====================

    public void openPlotDetailGui(Player player, PlotSpec spec) {
        Inventory inv = Bukkit.createInventory(new PlotGuiHolder(GuiMode.PLOT_DETAIL, spec.id()), 27,
                TextUtil.mm("<#ff85c0><bold>🏡 [" + spec.id() + "호] 부지 등기 정보</bold></#ff85c0>"));

        ItemStack bg = icon(Material.PINK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        UUID owner = getOwner(spec.id());
        double price = getPlotPrice(spec);
        PlotSpec myExisting = getOwnedPlot(player.getUniqueId());

        if (owner == null) {
            String moveNote = myExisting != null
                    ? "<yellow>※ 현재 소유 중인 [" + myExisting.id() + "호]를 먼저 반납(70% 환급)해야 새 부지를 분양받을 수 있습니다."
                    : "<green>✦ 즉시 분양 계약 가능! (구매 즉시 건축 권한 활성화)";
            inv.setItem(11, icon(Material.EMERALD_BLOCK,
                    "<green><bold>📝 [클릭] " + spec.id() + "호 부지 분양 구매하기!</bold></green>",
                    "<white>등급: <aqua>" + spec.tier().display,
                    "<white>분양가: " + TextUtil.churu(price),
                    moveNote,
                    "",
                    "<yellow>▶ 클릭하여 내 집터로 등기 완료!"));
        } else if (owner.equals(player.getUniqueId())) {
            boolean cottageBuilt = data.getBoolean("plots." + spec.id() + ".cottage_built", false);
            inv.setItem(10, icon(Material.CHERRY_DOOR,
                    cottageBuilt ? "<gray><bold>✔ 기본 아기냥 오두막 시공됨</bold>"
                                 : "<#ff9c6e><bold>🏠 [무료 혜택] 기본 벚꽃 오두막 모델하우스 시공!</bold></#ff9c6e>",
                    "<gray>건축이 막막한 분들을 위해 부지 중앙에 아담한",
                    "<gray>오두막집(.nbt)을 무료로 즉시 지어드립니다!",
                    "<red>※ 주의: 부지 중앙의 기존 블록 위에 덮어씌워지므로 입주 직후에만 사용하세요!",
                    cottageBuilt ? "<dark_gray>[이미 시공 완료]" : "<yellow>▶ 클릭하여 오두막 즉시 건축"));

            inv.setItem(12, icon(Material.PLAYER_HEAD,
                    "<aqua><bold>🤝 동거 지인(건축·상자 공유) 관리</bold></aqua>",
                    "<gray>함께 살거나 농장을 가꿀 지인을 추가/해제합니다.",
                    "<yellow>▶ 클릭하여 설정"));

            double refund = Math.round(price * 0.70);
            inv.setItem(16, icon(Material.RED_TERRACOTTA,
                    "<red><bold>📤 부지 반납 및 초기화 (70% 환급)</bold></red>",
                    "<gray>더 넓은 중형·대형 부지로 이사 갈 때 사용하세요!",
                    "<gold>✦ 환급액: <bold>" + TextUtil.fmt(refund) + " 츄르</bold></gold>",
                    "<red>⚠ 반납 시 부지가 깨끗한 잔디밭으로 초기화됩니다! 귀중품을 먼저 챙기세요!"));
        } else {
            inv.setItem(11, icon(Material.FLOWER_POT,
                    "<#ff85c0><bold>🐾 " + getName(owner) + "님의 소중한 보금자리입니다</bold></#ff85c0>",
                    "<gray>등급: <white>" + spec.tier().display,
                    isTrusted(owner, player.getUniqueId())
                            ? "<green>✔ 나는 이 집의 동거인으로 등록되어 있습니다!"
                            : "<gray>구경은 자유지만 블록·상자는 주인만 다룰 수 있습니다."));
        }

        inv.setItem(14, icon(Material.LANTERN,
                "<yellow><bold>🌸 부지 경계선 파티클 & 나침반 안내</bold></yellow>",
                "<gray>나침반 바늘을 이 부지 입구로 맞추고,",
                "<gray>경계선 테두리에 8초간 벚꽃 파티클을 피웁니다!",
                "<yellow>▶ 클릭하여 표시"));

        inv.setItem(22, icon(Material.ARROW, "<gray>◀ 전체 토지 등기소 목록 보기"));
        player.openInventory(inv);
    }

    public void openTrustGui(Player player) {
        PlotGuiHolder holder = new PlotGuiHolder(GuiMode.TRUST_LIST, null);
        Inventory inv = Bukkit.createInventory(holder, 36, TextUtil.mm("<aqua><bold>🤝 내 분양 부지 동거인 관리</bold></aqua>"));

        Set<UUID> candidates = new HashSet<>();
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (!on.getUniqueId().equals(player.getUniqueId())) candidates.add(on.getUniqueId());
        }
        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            if (off.getUniqueId() != null && !off.getUniqueId().equals(player.getUniqueId())) {
                candidates.add(off.getUniqueId());
            }
        }

        int slot = 0;
        for (UUID friendId : candidates) {
            if (slot >= 27) break;
            OfflinePlayer fp = Bukkit.getOfflinePlayer(friendId);
            boolean trusted = isTrusted(player.getUniqueId(), friendId);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(fp);
                String fname = fp.getName() != null ? fp.getName() : friendId.toString().substring(0, 8);
                sm.displayName(TextUtil.mm((trusted ? "<green><bold>✔ [동거인] " : "<gray><bold>✖ [외부인] ") + fname + "</bold>"));
                sm.lore(TextUtil.lore(
                        trusted ? "<green>내 분양 부지에서 건축·농사·상자 공유가 허용됩니다."
                                : "<gray>내 부지의 블록과 상자를 건드릴 수 없습니다.",
                        "", "<yellow>▶ 클릭하여 권한 전환"
                ));
                head.setItemMeta(sm);
            }
            holder.displayedFriends.add(friendId);
            inv.setItem(slot++, head);
        }
        inv.setItem(31, icon(Material.ARROW, "<gray>◀ 등기소 메뉴로 돌아가기"));
        player.openInventory(inv);
    }

    private void openConfirmSellGui(Player player, PlotSpec spec) {
        Inventory inv = Bukkit.createInventory(new PlotGuiHolder(GuiMode.CONFIRM_SELL, spec.id()), 27,
                TextUtil.mm("<red><bold>⚠ [" + spec.id() + "호] 부지 반납 최종 확인</bold></red>"));
        double refund = Math.round(getPlotPrice(spec) * 0.70);
        inv.setItem(11, icon(Material.LIME_CONCRETE,
                "<red><bold>✔ 네! 상자 아이템을 모두 뺐으며 부지를 초기화·반납합니다</bold></red>",
                "<gold>환급 수령액: +" + TextUtil.fmt(refund) + " 츄르</gold>"));
        inv.setItem(15, icon(Material.GRAY_CONCRETE, "<green><bold>✖ 취소 (돌아가기)</bold></green>"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PlotGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw < 0) return;

        if (holder.mode == GuiMode.REGISTRY) {
            if (raw == 40) {
                openTrustGui(player);
                return;
            }
            String[] order = {"S-5", "S-1", "S-2", "S-6", "S-3", "S-4", "M-1", "M-2", "M-3", "M-4", "L-1", "L-2"};
            int[] slots =    { 10,    11,    12,    13,    15,    16,    20,    21,    23,    24,    30,    32   };
            for (int i = 0; i < slots.length; i++) {
                if (raw == slots[i]) {
                    openPlotDetailGui(player, plots.get(order[i]));
                    return;
                }
            }
            return;
        }

        if (holder.mode == GuiMode.TRUST_LIST) {
            if (raw == 31) {
                openRegistryGui(player);
                return;
            }
            if (raw < holder.displayedFriends.size()) {
                toggleTrust(player.getUniqueId(), holder.displayedFriends.get(raw));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
                openTrustGui(player);
            }
            return;
        }

        PlotSpec spec = holder.plotId != null ? plots.get(holder.plotId) : null;
        if (spec == null) return;

        if (holder.mode == GuiMode.PLOT_DETAIL) {
            UUID owner = getOwner(spec.id());
            if (raw == 22) {
                openRegistryGui(player);
                return;
            }
            if (raw == 14) {
                player.closeInventory();
                showPlotBorderAndCompass(player, spec);
                return;
            }
            if (owner == null && raw == 11) {
                PlotSpec existing = getOwnedPlot(player.getUniqueId());
                if (existing != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
                    player.sendMessage(TextUtil.msg("<red>이미 <yellow>[" + existing.id() + "호]</yellow> 부지를 소유 중입니다! 이사하려면 기존 부지를 먼저 반납(70% 환급)해 주세요."));
                    return;
                }
                double price = getPlotPrice(spec);
                if (!churu.withdraw(player, price)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
                    player.sendMessage(TextUtil.msg("<red>분양 대금이 부족합니다! (필요: " + TextUtil.fmt(price) + " 츄르 — 은행 예금 중인 경우 출금해 주세요)"));
                    return;
                }
                data.set("plots." + spec.id() + ".owner", player.getUniqueId().toString());
                data.set("plots." + spec.id() + ".cottage_built", false);
                saveCallback.run();
                World hub = hubWorldSupplier.get();
                if (hub != null) refreshPlotSign(hub, spec);

                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                Bukkit.broadcast(TextUtil.msg("🏡 <white><bold>" + player.getName() + "</bold></white>님이 고양이 주거 단지 <aqua><bold>[" + spec.id() + "호 — " + spec.tier().display + "]</bold></aqua>에 입주했습니다! 집들이 가자~!! 🐾"));
                showPlotBorderAndCompass(player, spec);
                openPlotDetailGui(player, spec);
                return;
            }
            if (player.getUniqueId().equals(owner)) {
                if (raw == 10 && !data.getBoolean("plots." + spec.id() + ".cottage_built", false)) {
                    World hub = hubWorldSupplier.get();
                    if (hub != null && starterCottageBuilder != null) {
                        data.set("plots." + spec.id() + ".cottage_built", true);
                        saveCallback.run();
                        starterCottageBuilder.accept(hub, spec);
                        player.closeInventory();
                        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 1.1f);
                        player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 1.2f);
                        player.sendMessage(TextUtil.msg("🏠 <aqua>[" + spec.id() + "호]</aqua> 부지 한가운데에 아늑한 벚꽃 오두막이 완공되었습니다!"));
                    }
                } else if (raw == 12) {
                    openTrustGui(player);
                } else if (raw == 16) {
                    openConfirmSellGui(player, spec);
                }
            }
            return;
        }

        if (holder.mode == GuiMode.CONFIRM_SELL) {
            if (raw == 15) {
                openPlotDetailGui(player, spec);
                return;
            }
            if (raw == 11 && player.getUniqueId().equals(getOwner(spec.id()))) {
                double refund = Math.round(getPlotPrice(spec) * 0.70);
                resetPlotLawn(spec);
                data.set("plots." + spec.id() + ".owner", null);
                data.set("plots." + spec.id() + ".cottage_built", false);
                saveCallback.run();
                World hub = hubWorldSupplier.get();
                if (hub != null) refreshPlotSign(hub, spec);
                churu.deposit(player, refund);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                player.sendMessage(TextUtil.msg("📤 [" + spec.id() + "호] 부지 반납 및 정리가 완료되어 " + TextUtil.churu(refund) + "<gray>를 환급받았습니다!"));
            }
        }
    }

    public void resetPlotLawn(PlotSpec spec) {
        beforePlotReset.accept(spec);
        World hub = hubWorldSupplier.get();
        if (hub == null) return;
        for (int x = spec.minX(); x <= spec.maxX(); x++) {
            for (int z = spec.minZ(); z <= spec.maxZ(); z++) {
                for (int y = spec.tier().minY; y < GROUND_Y; y++) {
                    hub.getBlockAt(x, y, z).setType(y >= GROUND_Y - 3 ? Material.DIRT : Material.STONE, false);
                }
                hub.getBlockAt(x, GROUND_Y, z).setType(Material.GRASS_BLOCK, false);
                for (int y = GROUND_Y + 1; y <= spec.tier().maxY; y++) {
                    Block b = hub.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        if (b.getState() instanceof InventoryHolder ih && ih.getInventory() != null) {
                            ih.getInventory().clear();
                        }
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        refreshPlotSign(hub, spec);
    }

    private void showPlotBorderAndCompass(Player player, PlotSpec spec) {
        World hub = hubWorldSupplier.get();
        if (hub == null) return;
        Location gate = new Location(hub, spec.signX() + 0.5, GROUND_Y + 1.0, spec.signZ() + 0.5);
        if (player.getWorld().equals(hub)) {
            player.setCompassTarget(gate);
        }
        player.sendMessage(TextUtil.msg("🌸 <aqua>[" + spec.id() + "호]</aqua> 부지 입구 위치: <white>X=" + spec.signX() + ", Z=" + spec.signZ() + "</white> (중앙 광장 남쪽 다리 건너편)"));

        for (int rep = 0; rep < 8; rep++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !player.getWorld().equals(hub)) return;
                int y = GROUND_Y + 2;
                for (int x = spec.minX(); x <= spec.maxX(); x += 2) {
                    player.spawnParticle(Particle.CHERRY_LEAVES, x + 0.5, y, spec.minZ() + 0.5, 2, 0.05, 0.3, 0.05, 0.01);
                    player.spawnParticle(Particle.CHERRY_LEAVES, x + 0.5, y, spec.maxZ() + 0.5, 2, 0.05, 0.3, 0.05, 0.01);
                }
                for (int z = spec.minZ(); z <= spec.maxZ(); z += 2) {
                    player.spawnParticle(Particle.CHERRY_LEAVES, spec.minX() + 0.5, y, z + 0.5, 2, 0.05, 0.3, 0.05, 0.01);
                    player.spawnParticle(Particle.CHERRY_LEAVES, spec.maxX() + 0.5, y, z + 0.5, 2, 0.05, 0.3, 0.05, 0.01);
                }
            }, rep * 20L);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        PlotSpec my = getOwnedPlot(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.9f, 1.2f);
        if (my != null) {
            p.sendMessage(TextUtil.msg("🏡 내 소유 부지: <aqua><bold>" + my.id() + "호 (" + my.tier().display + ")</bold></aqua> — 부지 앞 표지판 또는 허브 서쪽 <yellow>[마을 회관 토지 등기소]</yellow>에서 관리할 수 있습니다!"));
        } else {
            p.sendMessage(TextUtil.msg("🏡 건축용 부지 분양은 허브 남쪽 다리 건너 <yellow>[고양이 주거 단지 표지판]</yellow> 또는 서쪽 <yellow>[마을 회관 토지 등기소]</yellow>에서 현장 계약하실 수 있습니다!"));
        }
        return true;
    }

    private String getName(UUID id) {
        if (id == null) return "미분양";
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : "모험가";
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
