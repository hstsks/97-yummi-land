package kr.yuumi97.core.rpg;
public enum LifeProfession {
 MINER("채광가","tool_miner_drill","심연 지질학자","다이너마이트 굴착왕"), FARMER("대농장주","tool_farmer_scythe","황금 대농장주","풍년 과수원지기"), ANGLER("강태공","tool_angler_harpoon","칠대양 보물 사냥꾼","심해 월척 명인"), CARPENTER("공방 목수","tool_carpenter_axe","황실 건축 거장","산림 벌목 거장"), BLACKSMITH("대장장이","tool_smith_hammer","별빛 신화 명장","전장 병기공"), CHEF("미식 셰프","tool_chef_wok","삼성 미슐랭 마스터","비약 연금 브루어");
 public final String display,itemId,specA,specB;LifeProfession(String d,String i,String a,String b){display=d;itemId=i;specA=a;specB=b;}
 public static LifeProfession parse(String s){try{return valueOf(s);}catch(IllegalArgumentException|NullPointerException e){return null;}}
 public static LifeProfession fromItem(String id){for(var c:values())if(c.itemId.equals(id))return c;return null;}
}
