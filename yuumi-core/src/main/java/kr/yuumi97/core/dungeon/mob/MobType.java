package kr.yuumi97.core.dungeon.mob;

/** All eighteen authored rigs are gameplay definitions, never vanilla model aliases. */
public enum MobType {
    MIMIC("mob_grimoire_mimic","금서 아귀",1,0,1.15), SENTINEL("mob_clockwork_sentinel","별모래 태엽 파수병",1,1,1.35),
    LIBRARIAN("mob_ink_librarian","잉크 그림자 사서",1,2,1.30), GOLEM("mob_nebula_golem","성운 수정 골렘",1,3,1.75),
    ASTRO("boss_archivist_astro","수석 기록관 아스트로",1,4,1.65), NOVARION("boss_novarion","별을 삼킨 사서장 노바리온",1,5,2.40),
    IMP("mob_cinder_imp","불씨 임프 대장공",2,0,1.05), HOUND("mob_slag_hound","용광로 강철 사냥개",2,1,1.30),
    BOMBARDIER("mob_obsidian_bombardier","흑요석 화포병",2,2,1.45), JUGGERNAUT("mob_chained_juggernaut","사슬 집행자",2,3,1.80),
    VULCANTUS("boss_warden_vulcantus","도가니 감독관 불칸투스",2,4,1.85), IGNION("boss_ignion","홍련의 강철 거신 이그니온",2,5,2.65),
    STALKER("mob_mirror_stalker","거울 파편 추적자",3,0,1.30), CHOIR("mob_void_choir","공허 성가대원",3,1,1.35),
    OCULUS("mob_abyssal_oculus","차원의 눈",3,2,1.50), TEMPLAR("mob_eclipse_templar","일식의 성당 기사",3,3,1.55),
    ARCHONS("boss_twin_archons","균열의 쌍둥이 집정관",3,4,1.80), ECLIPSIA("boss_eclipsia","문턱의 일식 군주 이클립시아",3,5,2.55);
    public final String id,title;public final int chapter,role;public final double scale;
    MobType(String id,String title,int chapter,int role,double scale){this.id=id;this.title=title;this.chapter=chapter;this.role=role;this.scale=scale;}
    public boolean boss(){return role>=4;}
    public boolean ranged(){return role==2||this==CHOIR;}
    public static MobType of(int chapter,int role){for(MobType t:values())if(t.chapter==chapter&&t.role==role)return t;throw new IllegalArgumentException("mob chapter/role");}
    public double health(){int i=chapter-1;double normal=new double[]{24,36,50}[i];return switch(role){case 0->normal;case 1->this==CHOIR?45:Math.round(normal*1.25);case 2->Math.round(normal*.85);case 3->new double[]{140,240,360}[i];case 4->new double[]{210,360,540}[i];default->new double[]{300,500,800}[i];};}
    public double damage(){return switch(role){case 0,1,2->this==CHOIR?6:new double[]{4,6,8}[chapter-1];case 3->new double[]{7,10,13}[chapter-1];case 4->new double[]{8,11,14}[chapter-1];default->new double[]{9,13,17}[chapter-1];};}
    public double poise(){return switch(role){case 0->new double[]{20,25,30}[chapter-1];case 1->this==CHOIR?23:new double[]{30,38,45}[chapter-1];case 2->new double[]{15,19,23}[chapter-1];case 3->new double[]{80,100,120}[chapter-1];case 4->new double[]{120,160,200}[chapter-1];default->new double[]{160,220,280}[chapter-1];};}
}
