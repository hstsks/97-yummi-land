package kr.yuumi97.core.rpg;
public enum CombatClass {
 PALADIN("성기사","weapon_paladin_sword","이지스 가디언","발키리 크루세이더"), BERSERKER("버서커","weapon_berserker_greatsword","크림슨 워로드","타이탄 브레이커"), SHADOW("어쌔신","weapon_shadow_daggers","나이트 팬텀","질풍 검성"), RANGER("레인저","weapon_ranger_bow","아스트랄 스나이퍼","트릭스터 거너"), ARCANIST("아크메이지","weapon_arcane_orb_tome","보이드 아크메이지","엘리멘탈 세이지"), PRIEST("루미너스 프리스트","weapon_priest_staff","아홉 목숨 대신관","천벌의 오라클");
 public final String display,itemId,awakeningA,awakeningB;CombatClass(String d,String i,String a,String b){display=d;itemId=i;awakeningA=a;awakeningB=b;}
 public static CombatClass parse(String s){try{return valueOf(s);}catch(IllegalArgumentException|NullPointerException e){return null;}}
 public static CombatClass fromItem(String id){for(var c:values())if(c.itemId.equals(id))return c;return null;}
}
