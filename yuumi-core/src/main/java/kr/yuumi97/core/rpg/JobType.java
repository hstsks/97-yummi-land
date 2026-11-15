package kr.yuumi97.core.rpg;

public enum JobType {
    PALADIN("성기사", "weapon_paladin_sword", "이지스 가디언", "발키리 크루세이더"),
    SHADOW("어쌔신", "weapon_shadow_daggers", "나이트 팬텀", "질풍 검성"),
    RANGER("레인저", "weapon_ranger_bow", "아스트랄 스나이퍼", "트릭스터 거너"),
    ARCANIST("아카니스트", "weapon_arcane_grimoire", "공허 대마도사", "아홉 목숨 대신관"),
    MINER("채광가", "tool_miner_drill", "심연 지질학자", "다이너마이트 굴착왕"),
    BLACKSMITH("대장장이", "tool_smith_hammer", "별빛 신화 명장", "룬 배틀스미스"),
    FARMER("대지주", "tool_farmer_scythe", "황금 대농장주", "가시덩굴 드루이드"),
    ANGLER("강태공", "tool_angler_harpoon", "칠대양 보물 사냥꾼", "해일 조련사"),
    CARPENTER("공방 목수", "tool_carpenter_axe", "황실 가구 건축가", "공성 기관사"),
    CHEF("미식 셰프", "tool_chef_wok", "삼성 미슐랭 마스터", "매드 연금 브루어");

    public final String display, itemId, awakeningA, awakeningB;
    JobType(String display, String itemId, String a, String b) {
        this.display = display; this.itemId = itemId; awakeningA = a; awakeningB = b;
    }
    public boolean combat() { return ordinal() < 4; }
    public static JobType parse(String value) {
        if (value == null) return null;
        try { return valueOf(value); } catch (IllegalArgumentException e) { return null; }
    }
    public static JobType fromItem(String id) {
        for (JobType job : values()) if (job.itemId.equals(id)) return job;
        return null;
    }
}
