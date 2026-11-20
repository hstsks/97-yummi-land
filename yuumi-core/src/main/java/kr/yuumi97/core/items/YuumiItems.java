package kr.yuumi97.core.items;

import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class YuumiItems {

    public static final String MODEL_NAMESPACE = "yuumi97";
    public static final java.util.List<String> CUSTOM_IDS = java.util.List.of(
            "labyrinth_key", "ancient_cat_coin", "gacha_capsule", "class_emblem", "respec_potion",
            "charm_crimson_claw", "charm_starlight_aegis", "charm_golden_whisker", "charm_vampiric_purr",
            "furn_cat_tower", "furn_kotatsu", "furn_neon_sign", "hat_chef_toque", "hat_stock_tophat",
            "monster_soul_core", "unidentified_geode", "golden_catnip", "deep_sea_fillet", "rune_scroll", "michelin_feast",
            "weapon_paladin_sword", "weapon_berserker_greatsword", "weapon_shadow_daggers", "weapon_ranger_bow", "weapon_arcane_orb_tome", "weapon_priest_staff", "guidebook",
            "tool_miner_drill", "tool_smith_hammer", "tool_farmer_scythe", "tool_angler_harpoon", "tool_carpenter_axe", "tool_chef_wok");
    private final JavaPlugin plugin;
    private final NamespacedKey itemIdKey;
    private java.util.function.Predicate<String> models=id->false;
    public void setModelAvailability(java.util.function.Predicate<String> models){this.models=models;}

    public YuumiItems(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey(plugin, "yuumi_item_id");
    }

    public NamespacedKey getItemIdKey() {
        return itemIdKey;
    }

    public String getCustomId(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public boolean isCustomItem(ItemStack stack, String id) {
        return id.equals(getCustomId(stack));
    }

    private ItemStack createBase(Material baseMat, String id, String modelId, String nameMm, String... loreLines) {
        ItemStack item = new ItemStack(baseMat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(nameMm));
            meta.lore(TextUtil.lore(loreLines));
            meta.setItemModel(new NamespacedKey(MODEL_NAMESPACE, modelId.equals("weapon_arcane_orb_tome")&&!models.test(modelId)?"weapon_arcane_grimoire":modelId));
            meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack yuumiEarsHat() {
        ItemStack item = createBase(Material.PAPER, "hat_yuumi_ears", "hat_yuumi_ears",
                "<#8bb9ef><bold>유미의 황금 방울 귀 머리띠</bold>",
                "<gray>커다란 유미 귀와 작은 황금 방울이 달린 머리띠입니다.",
                "<yellow>▶ 머리에 착용할 수 있습니다.");
        ItemMeta meta = item.getItemMeta();
        var equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.HEAD);
        equippable.setCameraOverlay(null);
        equippable.setModel(null);
        equippable.setDamageOnHurt(false);
        meta.setEquippable(equippable);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack yuumiPlushie() {
        return createBase(Material.PAPER, "plushie_yuumi", "plushie_yuumi",
                "<#8bb9ef><bold>식빵 유미 봉제인형</bold>",
                "<gray>보드라운 파란 털과 금빛 방울을 가진 작은 단짝입니다.",
                "<yellow>▶ 본인·공유 분양지 바닥을 우클릭하여 설치하세요.");
    }

    /** One authoritative factory for rewards, public workshops and operator grants. */
    public ItemStack create(String id) {
        if (!CUSTOM_IDS.contains(id)) return switch (id) {
            case "hat_yuumi_ears" -> yuumiEarsHat();
            case "plushie_yuumi" -> yuumiPlushie();
            case "you_and_me_bell" -> youAndMeBell();
            case "nine_lives_charm" -> nineLivesCharm();
            case "starlight_stone" -> starlightStone(1);
            case "starlight_scroll" -> starlightScroll(1);
            case "churu_golden" -> churuGolden(1);
            case "recall_bookmark" -> recallBookmark(1);
            default -> throw new IllegalArgumentException("Unknown Yuumi item: " + id);
        };
        Material material = switch (id) {
            case "labyrinth_key" -> Material.TRIAL_KEY;
            case "ancient_cat_coin" -> Material.SUNFLOWER;
            case "gacha_capsule" -> Material.HEART_OF_THE_SEA;
            case "class_emblem" -> Material.NETHER_STAR;
            case "respec_potion" -> Material.EXPERIENCE_BOTTLE;
            case "furn_cat_tower", "furn_kotatsu" -> Material.OAK_PLANKS;
            case "furn_neon_sign" -> Material.GLOW_ITEM_FRAME;
            case "monster_soul_core" -> Material.ECHO_SHARD;
            case "unidentified_geode" -> Material.RAW_GOLD;
            case "golden_catnip" -> Material.FERN;
            case "deep_sea_fillet" -> Material.SALMON;
            case "rune_scroll" -> Material.ENCHANTED_BOOK;
            case "michelin_feast" -> Material.COOKED_BEEF;
            case "weapon_paladin_sword", "weapon_shadow_daggers" -> Material.DIAMOND_SWORD;
            case "weapon_berserker_greatsword" -> Material.NETHERITE_SWORD;
            case "weapon_ranger_bow", "weapon_arcane_orb_tome", "weapon_priest_staff", "guidebook" -> Material.PAPER;
            case "tool_miner_drill" -> Material.DIAMOND_PICKAXE;
            case "tool_smith_hammer", "tool_carpenter_axe" -> Material.DIAMOND_AXE;
            case "tool_farmer_scythe" -> Material.DIAMOND_HOE;
            case "tool_angler_harpoon" -> Material.FISHING_ROD;
            case "tool_chef_wok" -> Material.IRON_SHOVEL;
            default -> Material.PAPER;
        };
        String name = switch (id) {
            case "labyrinth_key" -> "문턱의 미궁 열쇠";
            case "ancient_cat_coin" -> "고대 고양이 금화";
            case "gacha_capsule" -> "황금 뽑기 캡슐";
            case "class_emblem" -> "집사 성향 문장";
            case "respec_potion" -> "망각의 캣닢 물약";
            case "charm_crimson_claw" -> "진홍의 발톱 책갈피";
            case "charm_starlight_aegis" -> "별빛 이지스 책갈피";
            case "charm_golden_whisker" -> "황금 수염 책갈피";
            case "charm_vampiric_purr" -> "흡혈 골골송 책갈피";
            case "furn_cat_tower" -> "3단 핑크 캣타워";
            case "furn_kotatsu" -> "사르르 귤 코타츠 테이블";
            case "furn_neon_sign" -> "97랜드 고양이 네온사인";
            case "hat_chef_toque" -> "미슐랭 셰프 토크";
            case "hat_stock_tophat" -> "츄르 증권왕 실크햇";
            case "monster_soul_core" -> "마수 영혼석";
            case "unidentified_geode" -> "미감정 별빛 지오드";
            case "golden_catnip" -> "황금 캣닢";
            case "deep_sea_fillet" -> "심해 대뱃살 필렛";
            case "rune_scroll" -> "별빛 마법 룬 각인서";
            case "michelin_feast" -> "특선 만찬";
            case "weapon_paladin_sword" -> "별빛 이지스 방패검";
            case "weapon_berserker_greatsword" -> "홍련의 대검";
            case "weapon_shadow_daggers" -> "초승달 그림자 쌍검";
            case "weapon_ranger_bow" -> "천공의 별빛 마궁";
            case "weapon_arcane_orb_tome" -> "아스트랄 성운 보주서";
            case "weapon_priest_staff" -> "월광의 성배 지팡이";
            case "guidebook" -> "유미의 97랜드 만능 가이드북";
            case "tool_miner_drill" -> "별빛 크러셔 드릴";
            case "tool_smith_hammer" -> "태양불꽃 단조 망치";
            case "tool_farmer_scythe" -> "풍요의 황금 대낫";
            case "tool_angler_harpoon" -> "리바이어던 작살 낚싯대";
            case "tool_carpenter_axe" -> "영혼 고목 벌목 도끼";
            default -> "불꽃 미슐랭 중화팬";
        };
        boolean signature = id.startsWith("weapon_") || id.startsWith("tool_");
        ItemStack stack = createBase(material, id, id, "<gradient:#ffd666:#ff85c0><bold>" + name + "</bold></gradient>",
                signature ? "<gray>활성 전투·생활 성향과 일치할 때 힘이 깨어납니다." : "<gray>유미의 97랜드 공식 아이템",
                id.startsWith("furn_") ? "<yellow>▶ 분양지 바닥 우클릭 설치 · Shift+좌클릭 회수" :
                id.startsWith("charm_") ? "<yellow>▶ 성향 문장의 3슬롯 책갈피함에 장착하세요." :
                id.equals("class_emblem") ? "<yellow>▶ 우클릭: 성향 프로필 · 특전 · 책갈피함" :
                id.equals("gacha_capsule") ? "<yellow>▶ 우클릭 개봉 또는 광장 가챠 부스에서 사용" : "<dark_gray>교환·제작·모험 재료");
        ItemMeta meta = stack.getItemMeta();
        if (signature || id.startsWith("charm_") || id.equals("class_emblem") || id.equals("guidebook") || id.startsWith("hat_")) meta.setMaxStackSize(1);
        if (signature) meta.setUnbreakable(true);
        if (id.startsWith("hat_")) {
            var equippable = meta.getEquippable();
            equippable.setSlot(EquipmentSlot.HEAD);
            equippable.setCameraOverlay(null);
            equippable.setModel(null);
            equippable.setDamageOnHurt(false);
            meta.setEquippable(equippable);
        }
        if (id.equals("rune_scroll")) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "yuumi_rune_type"), PersistentDataType.STRING, "FIRE");
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "yuumi_rune_masterwork"), PersistentDataType.INTEGER, 0);
        }
        if (id.equals("michelin_feast")) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "yuumi_feast_type"), PersistentDataType.STRING, "VALOR");
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "yuumi_feast_stars"), PersistentDataType.INTEGER, 2);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack create(String id, int amount) {
        ItemStack item = create(id);
        item.setAmount(amount);
        return item;
    }

    public ItemStack labyrinthKey(int amount) { return create("labyrinth_key", amount); }
    public ItemStack ancientCatCoin(int amount) { return create("ancient_cat_coin", amount); }
    public ItemStack gachaCapsule(int amount) { return create("gacha_capsule", amount); }

    public ItemStack runeScroll(String type, boolean masterwork) {
        if (!java.util.Set.of("FIRE", "FROST", "THUNDER", "GALE", "BASTION").contains(type)) throw new IllegalArgumentException(type);
        ItemStack stack = create("rune_scroll");
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "yuumi_rune_type"), PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "yuumi_rune_masterwork"), PersistentDataType.INTEGER, masterwork ? 1 : 0);
        String label=switch(type){case "FIRE"->"홍련";case "FROST"->"빙결";case "THUNDER"->"뇌전";case "GALE"->"질풍";default->"철벽";};
        meta.displayName(TextUtil.mm("<aqua><bold>" + (masterwork ? "[명장 +25%] " : "") + label + " 별빛 룬 각인서</bold></aqua>"));
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack michelinFeast(String type, int stars) {
        if (!java.util.Set.of("VALOR", "SWIFT", "HARVEST").contains(type) || stars < 2 || stars > 3) throw new IllegalArgumentException(type);
        ItemStack stack = create("michelin_feast");
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "yuumi_feast_type"), PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "yuumi_feast_stars"), PersistentDataType.INTEGER, stars);
        String label=switch(type){case "VALOR"->"용맹";case "SWIFT"->"질풍";default->"풍요";};
        meta.displayName(TextUtil.mm("<gold><bold>" + "★".repeat(stars) + " " + label + " 미슐랭 특선 만찬</bold></gold>"));
        meta.lore(TextUtil.lore("<gray>공공 조리대와 미식 셰프의 정성스러운 만찬", "<yellow>" + (stars == 3 ? "효율·지속 +35% · 648초" : "지속 480초")));
        stack.setItemMeta(meta);
        return stack;
    }

    // ==================== 1. 화폐 & 카지노 아이템 ====================

    public ItemStack churuSalmon(int amount) {
        ItemStack item = createBase(Material.PAPER, "churu_salmon", "churu_salmon",
                "<#ff85a2><bold>연어맛 츄르 스틱</bold> <gray>(100 츄르)",
                "<gray>고양이 마을의 기본 화폐 대용 간식입니다.",
                "",
                "<yellow>▶ 우클릭 시 지갑에 <gold><bold>100 츄르</bold></gold> 즉시 입금!",
                "<dark_gray>[화폐 수표 · 선물/거래 가능]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack churuTuna(int amount) {
        ItemStack item = createBase(Material.PAPER, "churu_tuna", "churu_tuna",
                "<aqua><bold>참치맛 고급 츄르 스틱</bold> <gray>(1,000 츄르)",
                "<gray>신선한 참치 살코기가 듬뿍 담긴 중액권 츄르입니다.",
                "",
                "<yellow>▶ 우클릭 시 지갑에 <gold><bold>1,000 츄르</bold></gold> 즉시 입금!",
                "<dark_gray>[화폐 수표 · 선물/거래 가능]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack churuGolden(int amount) {
        ItemStack item = createBase(Material.PAPER, "churu_golden", "churu_golden",
                "<gradient:#ffd700:#ff85ff><bold>전설의 황금 츄르</bold></gradient> <gray>(10,000 츄르)",
                "<gray>유미가 목숨 걸고 아껴두는 최고급 마법 츄르입니다.",
                "<gray>고액 거래 수표이자 전설 유물 조합 재료로 쓰입니다.",
                "",
                "<yellow>▶ 우클릭 시 지갑에 <gold><bold>10,000 츄르</bold></gold> 즉시 입금!",
                "<dark_gray>[고액 수표 · 유물 재료]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack jackpotCrown() {
        ItemStack crown = createBase(Material.GOLDEN_HELMET, "jackpot_crown", "jackpot_crown",
                "<gradient:#ffe14d:#ff5992><bold>★ 97랜드 잭팟 제왕의 왕관 ★</bold></gradient>",
                "<gray>황금 냥냥 카지노 777 잭팟을 터뜨린 행운아의 증표!",
                "",
                "<green>+ 방어력 +4 / 행운 +2 / 영구 내구도",
                "<dark_gray>[한정판 전설 기념 투구]"
        );
        ItemMeta meta = crown.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                    new NamespacedKey(plugin, "crown_armor"), 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
            meta.addAttributeModifier(Attribute.LUCK, new AttributeModifier(
                    new NamespacedKey(plugin, "crown_luck"), 2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
            crown.setItemMeta(meta);
        }
        return crown;
    }

    // ==================== 2. 문턱의 서 & 귀환/세공/카지노 칩 ====================

    public ItemStack chip100(int amount) {
        ItemStack item = createBase(Material.PAPER, "chip_100", "chip_100",
                "<#ff5c7c><bold>코랄 카지노 칩</bold> <gray>(100 칩)",
                "<gray>황금 냥냥 카지노 공식 100 츄르 상당 전용 칩입니다.",
                "",
                "<yellow>▶ 카지노 입구 [환전소]에서 츄르로 정산 가능!",
                "<dark_gray>[카지노 전용 실물 칩 · 지인 양도 가능]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack chip1000(int amount) {
        ItemStack item = createBase(Material.PAPER, "chip_1000", "chip_1000",
                "<#2ed99b><bold>에메랄드 카지노 칩</bold> <gray>(1,000 칩)",
                "<gray>황금 냥냥 카지노 공식 1,000 츄르 상당 중액 칩입니다.",
                "",
                "<yellow>▶ 카지노 입구 [환전소]에서 츄르로 정산 가능!",
                "<dark_gray>[카지노 전용 실물 칩 · 지인 양도 가능]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack chip10000(int amount) {
        ItemStack item = createBase(Material.PAPER, "chip_10000", "chip_10000",
                "<gradient:#ffe14d:#ff5992><bold>VIP 로얄 골드 칩</bold></gradient> <gray>(10,000 칩)",
                "<gray>2층 VIP 하이롤러 라운지에서 통용되는 최고액 칩입니다.",
                "",
                "<yellow>▶ 카지노 입구 [환전소]에서 츄르로 정산 가능!",
                "<dark_gray>[VIP 카지노 실물 칩 · 지인 양도 가능]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack recallBookmark(int amount) {
        ItemStack item = createBase(Material.PAPER, "recall_bookmark", "recall_bookmark",
                "<light_purple><bold>🔖 문턱의 서: 고속 귀환 책갈피</bold>",
                "<gray>마법 페이지 사이에 끼워두는 위급 탈출용 책갈피입니다.",
                "",
                "<aqua>✦ 우클릭 사용 <gray>(1회용 소모품)",
                "<white>  단 <yellow>1.0초</yellow> 정신집중 후 평화로운 <green>고양이 허브 광장</green>",
                "<white>  (집 보유 시 내 분양 부지 앞마당!)으로 즉시 귀환합니다.",
                "<dark_gray>[조합: 종이 + 자수정 조각 + 청금석]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack starlightStone(int amount) {
        ItemStack item = createBase(Material.AMETHYST_SHARD, "starlight_stone", "starlight_stone",
                "<aqua><bold>✦ 찬란한 별빛 세공석</bold>",
                "<gray>고양이 대장간에서 무기·곡괭이·방어구에 별빛 마력을",
                "<gray>불어넣을 때 사용하는 핵심 세공 결정입니다.",
                "",
                "<yellow>▶ 허브 서쪽 <white>[고양이 대장간]</white>에서 ★1~★7 세공에 사용!",
                "<dark_gray>[획득: 광맥 채굴 · 던전 토벌 · 일일 의뢰 · 보부상]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack starlightScroll(int amount) {
        ItemStack item = createBase(Material.PAPER, "starlight_scroll", "starlight_scroll",
                "<gradient:#ffd666:#ff85c0><bold>📜 유미의 발도장 세공 보호권</bold></gradient>",
                "<gray>고단계(★6~★7) 별빛 세공 실패 시 등급 하락을",
                "<gray>100% 막아주는 유미의 축복 주문서입니다.",
                "",
                "<yellow>▶ 대장간 세공 시 보조 슬롯에 등록하여 사용!",
                "<dark_gray>[획득: 레이드 보스 · 주간 의뢰 · 만물 잡화상]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack guidebook() { return create("guidebook"); }

    public ItemStack tornPage(int amount) {
        ItemStack item = createBase(Material.PAPER, "torn_page", "torn_page",
                "<aqua><bold>문턱의 서: 찢어진 마법 책장</bold>",
                "<gray>야생 곳곳에 흩어진 문턱의 서 페이지 조각입니다.",
                "<gray>농사·낚시·채광·던전 토벌·일일 의뢰에서 발견됩니다.",
                "",
                "<yellow>▶ <white>/제단</white>에 납품하여 네더·엔드 차원문을 복원하세요!",
                "<dark_gray>[공동 번영 핵심 재료]"
        );
        item.setAmount(amount);
        return item;
    }

    // ==================== 3. 유미 특제 요리 (생활 × 레이드 연결) ====================

    public ItemStack zoomiesSkewer(int amount) {
        ItemStack item = createBase(Material.COOKED_COD, "zoomies_skewer", "zoomies_skewer",
                "<#5cdbd3><bold>슈우우웅~ 생선 꼬치구이</bold>",
                "<gray>유미의 마법 가루를 뿌려 노릇하게 구운 특제 꼬치!",
                "",
                "<green>✦ 섭취 효과: 즉시 체력 +6 회복",
                "<green>✦ 신속 II (3분) + 재생 II (20초)",
                "<dark_gray>[조합: 구운 대구 + 설탕 + 켈프 + 막대기]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack kneadingBento(int amount) {
        ItemStack item = createBase(Material.COOKED_SALMON, "kneading_bento", "kneading_bento",
                "<#ff9c6e><bold>유미의 꾹꾹이 특선 도시락</bold>",
                "<gray>정성스러운 꾹꾹이로 빚은 고양이 주먹밥과 연어 도시락!",
                "",
                "<green>✦ 섭취 효과: 생명력 강화 II (+4 하트, 8분)",
                "<green>✦ 흡수 보호막 II (3분)",
                "<dark_gray>[조합: 구운 연어 + 달걀 + 구운 감자 + 말린 켈프]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack catnipElixir(int amount) {
        ItemStack item = createBase(Material.HONEY_BOTTLE, "catnip_elixir", "catnip_elixir",
                "<#73d13d><bold>사르르 별빛 캣닢 영약</bold>",
                "<gray>은은한 별빛 캣닢을 우려낸 광부·어부 필수 비약!",
                "",
                "<green>✦ 섭취 효과: 성급함 II (6분) + 야간 투시 (8분) + 행운 I (6분)",
                "<dark_gray>[조합: 유리병 + 발광 열매 + 달콤한 열매 + 자수정 조각]"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack royalFeastCan(int amount) {
        ItemStack item = createBase(Material.GOLDEN_CARROT, "royal_feast_can", "royal_feast_can",
                "<gold><bold>97산 황실 참치 대뱃살 캔</bold>",
                "<gray>보스 레이드 출격 전 먹는 최고급 전투 통조림!",
                "",
                "<green>✦ 섭취 효과: 화염 저항 (8분) + 저항 I (5분) + 힘 I (5분)",
                "<dark_gray>[조합: 철 주괴 + 구운 연어 + 구운 대구 + 황금 당근]"
        );
        item.setAmount(amount);
        return item;
    }

    // ==================== 4. 월드보스 소환 코어 3종 ====================

    public ItemStack bossCoreRat(int amount) {
        ItemStack item = createBase(Material.AMETHYST_SHARD, "boss_core_rat", "boss_core_rat",
                "<light_purple><bold>[1티어 소환석] 지하수로 쥐대왕의 치즈 핵</bold>",
                "<gray>마을의 츄르 창고를 털어간 거대 쥐대왕을 유인하는 미끼 핵.",
                "",
                "<yellow>▶ <white>/레이드</white> 또는 스폰 레이드 투기장에서 소환!",
                "<gray>권장 인원: 2~8인 협동 (기여도별 전원 개별 보상)"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack bossCoreHound(int amount) {
        ItemStack item = createBase(Material.BLAZE_POWDER, "boss_core_hound", "boss_core_hound",
                "<red><bold>[2티어 소환석] 지옥불 사냥개의 목줄 핵</bold>",
                "<gray>네더 성채의 맹렬한 삼두견 케르베로스를 깨우는 불꽃 핵.",
                "<red>※ 문턱의 서 제2장(네더 해금) 달성 필요",
                "",
                "<yellow>▶ <white>/레이드</white> 투기장에서 소환!"
        );
        item.setAmount(amount);
        return item;
    }

    public ItemStack bossCoreVoid(int amount) {
        ItemStack item = createBase(Material.ECHO_SHARD, "boss_core_void", "boss_core_void",
                "<dark_purple><bold>[3티어 소환석] 공허 침식자의 수정 핵</bold>",
                "<gray>문턱의 서 너머 차원을 삼키려는 최종 군주를 불러내는 핵.",
                "<dark_purple>※ 문턱의 서 제3장(엔드 해금) 달성 필요",
                "",
                "<yellow>▶ <white>/레이드</white> 투기장에서 최종 결전 소환!"
        );
        item.setAmount(amount);
        return item;
    }

    // ==================== 5. 유미 모티브 보스 전리품 유물 ====================

    public ItemStack youAndMeBell() {
        return createBase(Material.BELL, "you_and_me_bell", "you_and_me_bell",
                "<gradient:#ff85c0:#ffd666><bold>🔔 『너랑 유미랑!』 우정의 황금 방울</bold></gradient>",
                "<gray>\"너랑 유미랑! 우리가 제일 잘 나가~\"",
                "",
                "<aqua>✦ 패시브 [밀착 응원]: <white>인벤토리에 보유 중일 때",
                "<white>  반경 12블록 내에 다른 지인이 있으면 <yellow>서로 함께</yellow>",
                "<green>  힘 I + 저항 I + 지속 체력 재생</green> 효과를 받습니다!",
                "<dark_gray>[전설 협동 장신구]"
        );
    }

    public ItemStack prowlingWand() {
        return createBase(Material.BLAZE_ROD, "prowling_wand", "prowling_wand",
                "<gradient:#5cdbd3:#ff85c0><bold>🪄 『사르르 탄환』 유도 마법봉</bold></gradient>",
                "<gray>유미의 사르르 탄환 마법이 깃든 귀여운 지팡이입니다.",
                "",
                "<aqua>✦ 우클릭 스킬 [사르르 유도탄] <gray>(재사용 대기시간 5초)",
                "<white>  전방 22블록 내 적을 자동 추적하는 마법 탄환을 날려",
                "<white>  <red>마법 피해 14</red> + <aqua>둔화 II & 발광</aqua>을 부여합니다!",
                "<dark_gray>[영웅 마법 무기]"
        );
    }

    public ItemStack finalChapterTome() {
        return createBase(Material.ENCHANTED_BOOK, "final_chapter_tome", "final_chapter_tome",
                "<gradient:#b37feb:#ffd666><bold>📘 『대단원』 파동의 비전서</bold></gradient>",
                "<gray>문턱의 서 최종장의 대마법을 해방하는 궁극의 마도서!",
                "",
                "<light_purple>✦ 우클릭 궁극기 [대단원 3연속 파동] <gray>(재사용 대기시간 14초)",
                "<white>  전방 부채꼴 범위로 3번의 마법 파동을 연속 발사하여",
                "<white>  광역 피해와 함께 적들을 <yellow>강력하게 속박</yellow>시키고",
                "<white>  주변 아군 지인들의 체력을 회복시킵니다!",
                "<dark_gray>[신화 레이드 유물]"
        );
    }

    public ItemStack nineLivesCharm() {
        return createBase(Material.TOTEM_OF_UNDYING, "nine_lives_charm", "nine_lives_charm",
                "<gradient:#5cdbd3:#bae637><bold>🐱 『아홉 개의 목숨』 수호 부적</bold></gradient>",
                "<gray>고양이에게는 아홉 번의 기회가 있다는 고대 수호 부적.",
                "",
                "<green>✦ 치명상 발동 시 부적을 소모하여 죽음을 면하고,",
                "<green>  체력 60% 회복 + 신속 III + 투명화 (6초) 부여!",
                "<dark_gray>[특수 생존 유물]"
        );
    }

    // ==================== 6. 카지노 슬롯 디스플레이 전용 아이콘 ====================

    public ItemStack slotIcon(String modelId, String displayName) {
        return createBase(Material.PAPER, "ui_" + modelId, modelId, displayName);
    }

    private static Material Material_HONEY_bottle_Safe() {
        return Material.HONEY_BOTTLE;
    }

    // ==================== 레시피 등록 ====================

    public void registerRecipes() {
        // 1. 슈우우웅~ 생선 꼬치구이
        NamespacedKey skewerKey = new NamespacedKey(plugin, "recipe_zoomies_skewer");
        Bukkit.removeRecipe(skewerKey);
        ShapelessRecipe skewer = new ShapelessRecipe(skewerKey, zoomiesSkewer(2));
        skewer.addIngredient(new RecipeChoice.MaterialChoice(Material.COOKED_COD, Material.COOKED_SALMON));
        skewer.addIngredient(Material.SUGAR);
        skewer.addIngredient(new RecipeChoice.MaterialChoice(Material.KELP, Material.DRIED_KELP));
        skewer.addIngredient(Material.STICK);
        Bukkit.addRecipe(skewer);

        // 2. 유미의 꾹꾹이 특선 도시락
        NamespacedKey bentoKey = new NamespacedKey(plugin, "recipe_kneading_bento");
        Bukkit.removeRecipe(bentoKey);
        ShapelessRecipe bento = new ShapelessRecipe(bentoKey, kneadingBento(1));
        bento.addIngredient(Material.COOKED_SALMON);
        bento.addIngredient(Material.EGG);
        bento.addIngredient(Material.BAKED_POTATO);
        bento.addIngredient(Material.DRIED_KELP);
        Bukkit.addRecipe(bento);

        // 3. 사르르 별빛 캣닢 영약
        NamespacedKey elixirKey = new NamespacedKey(plugin, "recipe_catnip_elixir");
        Bukkit.removeRecipe(elixirKey);
        ShapelessRecipe elixir = new ShapelessRecipe(elixirKey, catnipElixir(1));
        elixir.addIngredient(Material.GLASS_BOTTLE);
        elixir.addIngredient(Material.GLOW_BERRIES);
        elixir.addIngredient(Material.SWEET_BERRIES);
        elixir.addIngredient(Material.AMETHYST_SHARD);
        Bukkit.addRecipe(elixir);

        // 4. 97산 황실 참치 대뱃살 캔
        NamespacedKey canKey = new NamespacedKey(plugin, "recipe_royal_feast_can");
        Bukkit.removeRecipe(canKey);
        ShapelessRecipe can = new ShapelessRecipe(canKey, royalFeastCan(1));
        can.addIngredient(Material.IRON_INGOT);
        can.addIngredient(Material.COOKED_SALMON);
        can.addIngredient(Material.COOKED_COD);
        can.addIngredient(Material.GOLDEN_CARROT);
        Bukkit.addRecipe(can);

        // 5. 1티어 보스 소환석 조합법 (자율 소환 가능)
        NamespacedKey ratCoreKey = new NamespacedKey(plugin, "recipe_boss_core_rat");
        Bukkit.removeRecipe(ratCoreKey);
        ShapedRecipe ratCore = new ShapedRecipe(ratCoreKey, bossCoreRat(1));
        ratCore.shape("GGG", "EAE", "GGG");
        ratCore.setIngredient('G', Material.GOLD_INGOT);
        ratCore.setIngredient('E', Material.ROTTEN_FLESH);
        ratCore.setIngredient('A', Material.AMETHYST_SHARD);
        Bukkit.addRecipe(ratCore);

        // 6. 2티어 보스 소환석 조합법
        NamespacedKey houndCoreKey = new NamespacedKey(plugin, "recipe_boss_core_hound");
        Bukkit.removeRecipe(houndCoreKey);
        ShapedRecipe houndCore = new ShapedRecipe(houndCoreKey, bossCoreHound(1));
        houndCore.shape("BBB", "MOM", "BBB");
        houndCore.setIngredient('B', Material.BLAZE_ROD);
        houndCore.setIngredient('M', Material.MAGMA_CREAM);
        houndCore.setIngredient('O', Material.CRYING_OBSIDIAN);
        Bukkit.addRecipe(houndCore);

        // 7. 3티어 보스 소환석 조합법
        NamespacedKey voidCoreKey = new NamespacedKey(plugin, "recipe_boss_core_void");
        Bukkit.removeRecipe(voidCoreKey);
        ShapedRecipe voidCore = new ShapedRecipe(voidCoreKey, bossCoreVoid(1));
        voidCore.shape("EEE", "PCP", "EEE");
        voidCore.setIngredient('E', Material.ENDER_EYE);
        voidCore.setIngredient('P', Material.POPPED_CHORUS_FRUIT);
        voidCore.setIngredient('C', Material.END_CRYSTAL);
        Bukkit.addRecipe(voidCore);

        // 8. 고속 귀환 책갈피 조합법
        NamespacedKey bookmarkKey = new NamespacedKey(plugin, "recipe_recall_bookmark");
        Bukkit.removeRecipe(bookmarkKey);
        ShapelessRecipe bookmark = new ShapelessRecipe(bookmarkKey, recallBookmark(2));
        bookmark.addIngredient(Material.PAPER);
        bookmark.addIngredient(Material.AMETHYST_SHARD);
        bookmark.addIngredient(Material.LAPIS_LAZULI);
        Bukkit.addRecipe(bookmark);
    }
}
