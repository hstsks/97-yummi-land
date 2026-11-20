# 💻 [GPT 6.1 SOL 전용 작업 지시서] `유미의 97랜드 v3.0` — 허브 고밀도 건축 리모델링 · 미궁 · 10대 성향 직업/효율분업 · 증권/하우징 구현

당신은 **`GPT 6.1 SOL`** 이며, `유미의 97랜드 v3.0` (Paper `1.21.4`, Java 21) 프로젝트의 **코어 시스템 아키텍처, 오픈소스 `.nbt` 기반 허브 도시 리모델링, 로그라이트 파티 미궁, 10대 성향 직업·20대 각성·효율 분업 RPG, 증권소/가챠/3D 하우징 백엔드 전담 엔지니어**입니다.
동시에 다른 작업 세션에서 **`GPT 6 Astra`** 가 3D 복셀 모델링과 픽셀아트 에셋을 제작하므로, 아래 **파일 경계 규칙**과 **확정 에셋 ID 계약**을 정확히 준수하여 구현 및 검증을 완료하세요.

---

## 0. 동시 병렬 작업 규칙 & 포터블 빌드 환경 (필독!)

1. **쓰기 허용 경로 (Write Scope)**:
   - `E:\MinGem\yuumi-core\**` (모든 Java 코드, `plugin.yml`, `config.yml`, `src/main/resources/structures/**`)
   - `E:\MinGem\resourcepack-gen\PackBuilder.java` (Astra의 오버라이드가 합쳐지기 전에도 단독 구동 시 결측 텍스처가 절대 없도록 신규 아이템 30종 기본 16×16 절차적 폴백 도트 추가!)
   - 필요 시 구조물 추출 보조 스크립트 (`E:\MinGem\scripts\extract-v3-structures.ps1`)
2. **수정 절대 금지 경로 (Forbidden Scope)**:
   - `E:\MinGem\assets\yuumi97-production\**` (`GPT 6 Astra` 전담!)
   - `E:\MinGem\resourcepack-gen\overrides\**` (`GPT 6 Astra`가 실시간 생성·갱신 중이므로 절대 덮어쓰거나 삭제하지 마세요! 읽기는 허용)
3. **포터블 런타임 사용 규칙**:
   - 시스템 전역 `java`/`gradle`에 의존하지 말고 반드시 저장소 내장 포터블 런타임을 사용하세요:
     - JDK 21: `E:\MinGem\runtime\jdk-21\bin\java.exe`
     - Gradle 8.12: `E:\MinGem\runtime\gradle\bin\gradle.bat` (`$env:GRADLE_USER_HOME = "E:\MinGem\runtime\gradle-user-home"`, `-Dorg.gradle.java.home=E:\MinGem\runtime\jdk-21`)
   - 개발 중 반복 컴파일은 `gradle.bat -p E:\MinGem\yuumi-core jar --no-daemon` 으로 빠르게 수행하고, 최종 통합 시 `powershell -ExecutionPolicy Bypass -File E:\MinGem\scripts\build-all.ps1` 및 `scripts\verify-boot.ps1`로 실서버 구동(에러 0건)을 검증하세요.
4. **기존 데이터 하위 호환성 & Paper `1.21.4` API 주의사항**:
   - `server/plugins/YuumiLandCore/data.yml` 및 `vaults/<uuid>.yml`의 기존 키(`altar.*`, `bank.*`, `casino.jackpot`, `plots.*`, `trust.*`, `codex.*`)를 깨뜨리지 마세요.
   - Paper `1.21.4`에서는 `Attribute.MAX_HEALTH`, `Attribute.MOVEMENT_SPEED`, `Attribute.ATTACK_DAMAGE`, `Attribute.MINING_EFFICIENCY`, `Attribute.BLOCK_INTERACTION_RANGE`, `Attribute.LUCK` 등 `GENERIC_` 접두사가 없는 상수를 사용합니다.
   - 바닐라 무기/갑옷 `ItemMeta`에 커스텀 `AttributeModifier`를 직접 덮어씌우면 바닐라 기본 공격력/방어력이 사라지므로, 기존 `BlacksmithManager`와 동일하게 **플레이어 엔티티 `AttributeInstance` 동적 동기화 + 이벤트 데미지/속도 연산** 방식을 유지하세요.

---

## 1. Module 0: 확정 에셋 ID 연동 및 `YuumiItems` / `PackBuilder` 확장

`GPT 6 Astra`와 1:1 고정 합의된 **총 30종 신규 아이템 ID + 5종 3D 환경 프롭 ID 계약**입니다 (`NamespacedKey("yuumi97", id)` 및 `yuumi_item_id` 동일 문자열 사용).

### 1.1 `PackBuilder.java` 병합 규칙 보존 & 30종 폴백 도트 등록
- `PackBuilder.java`에 아래 신규 아이템 ID **30종**의 기본 16×16 절차적 도트를 추가하여, Astra 작업 전에도 모든 아이템이 정상 아이콘을 갖도록 보장하세요.
- **필수 보존 로직**: `PackBuilder.java`가 `overrides/`를 병합할 때 `overrides/assets/yuumi97/items/<id>.json`이 이미 존재하면(Astra의 `minecraft:select` `display_context=gui` 2D/3D 분기 파일), `PackBuilder`의 기본 `items/<id>.json`으로 덮어쓰지 않고 **Astra의 오버라이드 파일을 최우선 보존**하는 현재 동작을 100% 유지하세요!

### 1.2 신규 아이템 30종 마스터 목록 (`YuumiItems.java`)

#### A) 미궁 · 가챠 · 직업 공용 · 유물 · 가구 · 모자 (`14종` + 기존 `2종` 연동)
| ID (`yuumi_item_id` & 모델 ID) | 베이스 `Material` | 분류 및 핵심 용도 |
| :--- | :--- | :--- |
| `labyrinth_key` | `TRIAL_KEY` / `TRIPWIRE_HOOK` | 문턱의 미궁 2·3티어 입장 열쇠 (일일의뢰·레이드·상점 획득) |
| `ancient_cat_coin` | `SUNFLOWER` / `GOLD_NUGGET` | 문턱의 미궁 클리어 주화 (책갈피 유물·가챠·망각물약 정가 교환) |
| `gacha_capsule` | `HEART_OF_THE_SEA` / `AMETHYST_SHARD` | 황금 냥냥 뽑기 캡슐 (가챠 머신 투입 또는 우클릭 개봉) |
| `class_emblem` | `NETHER_STAR` (Unstackable) | 집사 성향 문장 (우클릭: 내 성향 직업·특전 트리·3슬롯 책갈피함 GUI 오픈) |
| `respec_potion` | `EXPERIENCE_BOTTLE` / `HONEY_BOTTLE` | 망각의 캣닢 물약 (직업 변경 시 `5,000 츄르` 대신 소모 가능한 무료 전향권) |
| `charm_crimson_claw` | `PAPER` (Unstackable) | 책갈피 유물: 치명타 확률 `+15%` & 미궁/레이드 보스 대상 피해 `+12%` |
| `charm_starlight_aegis` | `PAPER` (Unstackable) | 책갈피 유물: 받는 피해 `-10%` & 15초마다 최대 체력 `15%` 흡수 보호막 생성 |
| `charm_golden_whisker` | `PAPER` (Unstackable) | 책갈피 유물: 이동속도 `+10%` & `Attribute.LUCK +2` & 미궁 금화 획득 `+25%` |
| `charm_vampiric_purr` | `PAPER` (Unstackable) | 책갈피 유물: 공격 적중 시 피해량의 `6%` 체력 회복 (내부 쿨 0.5초) |
| `furn_cat_tower` | `OAK_PLANKS` / `ITEM_FRAME` | 분양지 3D 배치형 가구: `3단 핑크 캣타워` |
| `furn_kotatsu` | `OAK_PLANKS` / `ITEM_FRAME` | 분양지 3D 배치형 가구: `사르르 귤 코타츠 테이블` (우클릭 착석 지원!) |
| `furn_neon_sign` | `GLOW_ITEM_FRAME` / `PAPER` | 분양지 3D 배치형 가구: `97랜드 고양이 네온사인` |
| `hat_chef_toque` | `CARVED_PUMPKIN` / `PAPER` (`Equippable` `HEAD`) | 착용형 3D 모자 (`yuumiEarsHat()`과 동일한 `Equippable(EquipmentSlot.HEAD)` 적용) |
| `hat_stock_tophat` | `CARVED_PUMPKIN` / `PAPER` (`Equippable` `HEAD`) | 착용형 3D 모자 (`Equippable(EquipmentSlot.HEAD)` 적용) |
| *(기존 연동)* `hat_yuumi_ears`, `plushie_yuumi` | 기존 `YuumiItems` 메서드 활용 | 가챠 보상 풀 및 `plushie_yuumi` 분양지 3D 가구 배치/목공 제작 목록에 정식 편입! |

#### B) 성향별 효율 분업 원재료 `4종` & 가공 생산품 `2종` (`총 6종`)
| ID (`yuumi_item_id` & 모델 ID) | 베이스 `Material` | 핵심 용도 및 PDC 확장 키 |
| :--- | :--- | :--- |
| `monster_soul_core` | `ECHO_SHARD` | `마수 영혼석` — 몬스터 처치 시 기본 `4%` / 전투 4직업 `14%`(`3.5x`) 드롭. 룬·가구·만찬 촉매 |
| `unidentified_geode` | `RAW_GOLD` / `AMETHYST_CLUSTER` | `미감정 별빛 지오드` — 심층 광석 채굴 시 기본 `1.2%` / 채광가 `5.0%`(`4x+`) 드롭 |
| `golden_catnip` | `FERN` / `GOLDEN_CARROT` | `황금 캣닢` — 성숙 작물 수확 시 기본 `1.0%` / 대지주 `4.0%`(`4x`) 드롭 |
| `deep_sea_fillet` | `SALMON` | `심해 대뱃살 필렛` — 낚시 시 기본 `2.5%` / 강태공 `10.0%`(`4x`) 추가 획득 |
| `rune_scroll` | `ENCHANTED_BOOK` / `PAPER` | `별빛 마법 룬 각인서` — PDC `yuumi_rune_type` (`FIRE`/`FROST`/`THUNDER`/`GALE`/`BASTION`) + `yuumi_rune_masterwork` (`0` 일반 / `1` 대장장이 명장 각인 `+25%`) |
| `michelin_feast` | `COOKED_BEEF` (섭취 가능 음식) | `특선 만찬` — PDC `yuumi_feast_type` (`VALOR` 용맹 / `SWIFT` 질풍 / `HARVEST` 풍요) + `yuumi_feast_stars` (`2` 공공조리 `★★` / `3` 셰프 특선 `★★★` 효율·지속 `+35%`) |

#### C) 10대 성향 직업 전용 3D 성물 무기 · 도구 (`10종`, `Unbreakable = true`, 별빛 세공 & 룬 각인 호환!)
| ID (`yuumi_item_id` & 모델 ID) | 담당 직업 (`JobType`) | 베이스 `Material` | 성물 명칭 (`Lv.1 → Lv.10 초월 → Lv.20 신화` 접두사 진화) |
| :--- | :--- | :--- | :--- |
| `weapon_paladin_sword` | `PALADIN` (`성기사`) | `DIAMOND_SWORD` | `별빛 이지스 방패검` |
| `weapon_shadow_daggers` | `SHADOW` (`어쌔신`) | `DIAMOND_SWORD` | `초승달 그림자 쌍검` |
| `weapon_ranger_bow` | `RANGER` (`레인저`) | `BOW` | `천공의 별빛 마궁` |
| `weapon_arcane_grimoire` | `ARCANIST` (`아카니스트`) | `BLAZE_ROD` (MaxStack 1) | `문턱의 비전 마도서` |
| `tool_miner_drill` | `MINER` (`채광가`) | `DIAMOND_PICKAXE` | `별빛 크러셔 드릴` |
| `tool_smith_hammer` | `BLACKSMITH` (`대장장이`) | `DIAMOND_AXE` (또는 `MACE`) | `태양불꽃 단조 망치` |
| `tool_farmer_scythe` | `FARMER` (`대지주`) | `DIAMOND_HOE` | `풍요의 황금 대낫` |
| `tool_angler_harpoon` | `ANGLER` (`강태공`) | `FISHING_ROD` | `리바이어던 작살 낚싯대` |
| `tool_carpenter_axe` | `CARPENTER` (`공방 목수`) | `DIAMOND_AXE` | `영혼 고목 벌목 도끼` |
| `tool_chef_wok` | `CHEF` (`미식 셰프`) | `IRON_SHOVEL` / `BLAZE_ROD` | `불꽃 미슐랭 중화팬` |

### 1.3 신규 3D 환경 프롭 5종 (`ItemDisplay` 동적 스캔 마운트)
`Yuumi97Pack.zip` 내 `assets/yuumi97/items/<id>.json` 존재 여부를 스캔하여 있으면 3D `ItemDisplay`, 아직 없으면 깔끔한 바닐라 블록 폴백으로 표시합니다:
- `prop_gacha_machine`, `prop_stock_board`, `prop_dungeon_obelisk`, `prop_boon_altar`, `prop_feast_cauldron`

---

## 2. Module 1: 허브 도시 `yuumi_hub v3.0` 고밀도 건축 리모델링 (`HubWorldManager.java`)

인게임 플레이 테스트에서 확인된 **"중앙 광장과 동·서 대로 주변의 빈 여백 문제"** 를 해결해 살아 숨 쉬는 고밀도 판타지 타운으로 리모델링합니다.

### 2.1 오픈소스 `.nbt` 추가 추출 (`yuumi-core/src/main/resources/structures/v3/`)
`E:\MinGem\assets\structure-packs\TownsAndTowers-1.21.1-datapack.zip`에서 아래 구조물들을 `structures/v3/`로 추출·내장하고, 기존 `stampStructure` 정화기(`JIGSAW`, `STRUCTURE_BLOCK`, `STRUCTURE_VOID`, `SPAWNER` 제거 및 상자 루팅 초기화)를 거쳐 스탬핑하세요:
- **광장 정원 & 가제보**: `flower_forest_meeting_point_1.nbt`(`jp_gazebo_1.nbt`), `flower_forest_park_1.nbt`(`jp_park_1.nbt`), `flower_forest_park_2.nbt`(`jp_park_2.nbt`), `flower_forest_planter_1.nbt`(`jp_planter.nbt`), `flower_forest_lamp_1.nbt`(`jp_lamp.nbt`), `flower_forest_gate_1.nbt`(`jp_gate_1.nbt`)
- **서쪽 상점가 2층 전통 상가 & 배후 타운하우스**: `flower_forest_small_farm_1.nbt` / `flower_forest_shepherd_1.nbt`, `flower_forest_fisher_1.nbt`, `flower_forest_weaponsmith_1.nbt` / `flower_forest_armorer_1.nbt`, `flower_forest_cartographer_1.nbt` / `flower_forest_leatherworker_1.nbt`, `flower_forest_medium_house_1.nbt`, `flower_forest_small_house_1.nbt`, `flower_forest_small_house_2.nbt`
- **북쪽 성소 목탑/신사 & 동쪽 해안 범선/등대**:
  - `data/kaisyn/structure/village/flower_forest_japanese/houses/flower_forest_temple_1.nbt` → `v3/jp_temple.nbt`
  - `data/kaisyn/structure/outpost/towers/exclusives/oriental/base_plate.nbt` → `v3/oriental_pagoda.nbt`
  - `data/kaisyn/structure/ships/village_ocean/pieces/small_ship/small_ship_1.nbt` → `v3/harbor_ship.nbt`
  - `data/kaisyn/structure/village/beach_lighthouse/side/beach_main_house_1.nbt` → `v3/lighthouse_house.nbt`

### 2.2 구역별 리모델링 배치 (`hub_v3.built` 자동 마이그레이션 & 분양지 보호)
- 부팅 시 `data.getBoolean("hub_v3.built", false)`가 `false`이면 공공 구역만 v3.0으로 재건축하되, **남쪽 12필지 중 소유주가 있는 부지(`plotManager.getOwner(id) != null`) 내부 블록 및 설치 엔티티는 절대 건드리지 마세요!**
1. **중앙 분수 광장 (`radius 26`) 4분면 구성**:
   - **북서(NW, `X=-14, Z=-14`)**: `jp_gazebo_1.nbt` + 미니 연못 → **`🌟 성향 전직 교관 마스터냥` (`role = "class_master"`)** 상주!
   - **북동(NE, `X=+14, Z=-14`)**: **『🏆 명예의 전당 Top 3 단상』** (`role = "hall_of_fame"`) & **『🎁 황금 캡슐 가챠 부스』** (`prop_gacha_machine` + `role = "gacha"`).
   - **남서(SW, `X=-14, Z=+14`)**: 컬러 양털 파라솔과 계단 의자 노천 직거래 카페 테라스 & 화단(`jp_planter.nbt`).
   - **남동(SE, `X=+14, Z=+14`)**: `jp_park_1.nbt` 벚꽃 정원 & 길냥이 쉼터 + 풍성한 커스텀 벚나무 군락.
2. **서쪽 상점 대로 (`X = -28 .. -124`)**:
   - 5개 상인 수레 자리를 **실제 2층/전통 상가 건물 5채(`.nbt`) + 전면 천막 가판대**로 격상하고 기존 5대 상인 NPC(만물상에는 4종 특수 원재료 정가 백업 판매 추가, 목재/건축 상인에는 `[🪑 공공 3D 가구 공방]` 버튼 연동!)를 매장 앞에 배치합니다.
   - 대로 상공(`Y = 70..71`)에 **공중 연등 가랜드**를 연결하고, 상가 뒤편 빈 초지(`Z = ±18..±32`)에 배후 주택(`.nbt`)과 정원을 채웁니다.
3. **동쪽 2F 카지노 증축 & 증권 거래소 & 항구**:
   - 카지노 정면(`Z = -8..-5`)에 그랜드 포르티코 현관, 내부 천장 크리스탈 샹들리에, 룰렛 베팅 시 `prop_roulette_wheel` 1.5초 회전 연출을 추가합니다.
   - 카지노 2F 서북측 라운지(`X = 46..58, Y = 71, Z = -35..-29`)에 **『📈 츄르 증권 거래소』**(`prop_stock_board` + 실시간 `TextDisplay` 시세판 + `role = "stock_broker"`)를 신설합니다.
   - 동쪽 부두 너머 해상(`X = 116..138, Z = 12..38`)에 **`harbor_ship.nbt` 범선**과 해안 **`lighthouse_house.nbt` 등대**를 배치합니다.
4. **북쪽 성소 장엄화 & 『🗝 문턱의 미궁 관문』**:
   - 성소 좌우(`X = ±46, Z = -66`)에 `oriental_pagoda.nbt`와 `jp_temple.nbt`를 배치하고, 성소 우측 단상(`(18.5, 66.0, -55.5)`)에 **『🗝 문턱의 미궁 집결 아치』** 와 **`미궁 수문장 유령냥` (`role = "dungeon_gate"`)** 을 배치합니다.

---

## 3. Module 2: 로그라이트 파티 인스턴스 던전 『문턱의 미궁』 (`kr.yuumi97.core.dungeon.*`)

1. **파티 매칭 (`/파티`, 별칭 `/party`, `/미궁`)**:
   - `/파티 생성|초대|수락|탈퇴|추방|정보` (1~8인 지원; 1인 솔로 입장 완벽 지원). `/미궁` 명령어는 북쪽 성소 미궁 관문 나침반 안내만 수행하고, 실제 입장은 파티장이 북쪽 성소 `미궁 수문장 유령냥`을 우클릭해 진입합니다.
2. **전용 인스턴스 월드 (`yuumi_dungeon`) & 멀티 슬롯 격리**:
   - 빈 공간 월드(`VoidChunkGenerator`, `KEEP_INVENTORY=true`, `MOB_GRIEFING=false`, `DO_MOB_SPAWNING=false`), 동시 다중 파티 슬롯(`instanceSlot * 800` X축 오프셋) 격리.
   - 티어별 테마 팔레트(`제1 별빛 서고` / `제2 홍련의 회랑` / `제3 공허의 심연`)로 기둥·아치·조명이 살아있는 입체 던전 홀(`27×14×27` 이상)을 생성하고 인원수에 맞춰 몬스터 체력(`1 + 0.55*(N-1)`)과 개체수를 스케일링합니다.
3. **5스테이지 로그라이트 루프**:
   - **Stage 1 (돌파전 — 3종 중 랜덤 1)**: ① `prop_dungeon_obelisk` 3기 선파괴 봉인 해제전 / ② 원거리+근접 2웨이브 포위 돌파 / ③ 45초 황금 보물 도둑냥 추격전.
   - **Stage 2 (제1 축복 제단 — `prop_boon_altar`)**: 파티원 각자 개인별 8종 로그라이트 카드(`사르르 연쇄 번개`, `파동의 반월검기`, `골골송 생명 흡수`, `슈우우웅 폭주가속`, `아홉 목숨 성채`, `서리발 발자국`, `처형자의 발톱`, `황금 츄르 사냥꾼`) 중 랜덤 **3택 1** 선택.
   - **Stage 3 (협동 지혜 퍼즐방 — 3종 중 랜덤 1)**: ① 동시 공명 발판(솔로 시 지급되는 `[영혼 분신 방울]`로 보조 발판 활성화 지원!) / ② 4색 수정 음계 기억 / ③ 논리 힌트 진실 석상 찾기 (오답 시 즉사 대신 소규모 해골 스폰 후 재시도 허용).
   - **Stage 4 (엘리트 수문장 & 제2 축복 제단 + 회복 모닥불)**: 엘리트 격파 후 체력 회복 & 두 번째 축복 카드 3택 1 중첩!
   - **Stage 5 (심연 보스전 & 개별 보상 상자)**: 장판 예고 폭발 + **"석주 뒤로 숨으세요!"** 시야 판정 전멸기 + 광폭화 패턴. 클리어 시 개인별 보상 상자(`고대 고양이 금화`, `별빛 세공석`, 확률적 `황금 뽑기 캡슐` 및 `책갈피 유물`) 정산 및 복귀 포탈 생성. (미궁 내 사망 시 8% 치유비 면제, 생존 파티원이 다음 방 클리어 시 관전 파티원 자동 부활; 아카니스트 대신관 궁극기로 현장 즉시 부활도 연동!).

---

## 4. Module 3: 성향 정체성 중심 10대 직업 · 20대 각성 · 효율 분업 RPG (`kr.yuumi97.core.rpg.*`)

> **핵심 설계 철학 (매우 중요!)**:
> 1. **중복 선택 완전 자유 & 플레이 성향 정체성**: 서버 내 인원 제한이나 유일 전문가 강제 없이, **8명 중 여러 명이 같은 직업을 자유롭게 선택**할 수 있습니다.
> 2. **유연한 성향 탐색 & 직업별 성장 영구 보존**: 동시에 활성화되는 직업(`active_job`)은 **1개**이지만, 다른 직업을 체험하더라도 기존 직업에서 올린 레벨(`1..20`)·경험치·2차 각성(`A`/`B`)·특전 포인트 데이터(`rpg.<uuid>.jobs.<JOB_ID>.*`)는 **100% 영구 보존**됩니다! **최초 1회 직업 변경은 무료**이며, 이후 변경 시에는 `망각의 캣닢 물약 1개` 또는 `5,000 츄르`(재변경 쿨타임 `1시간`)만 소모합니다.
> 3. **컨텐츠 락아웃 0% (`공공 공방 기본 보장 + 전문직 비교우위 효율 분업`)**: 특정 직업 유저가 서버에 0명이거나 오프라인이어도 야생 기본 파밍(`1x`)과 허브 공공 공방 NPC(정가 재료 + 츄르 공임)를 통해 모든 제작·감정이 가능하며, 해당 성향 직업 유저는 **채집 수율 `3.5~4배` + 공임 면제 + 재료 `40~45%` 절감 + `1+1` 대성공 + `[명장 각인 / ★★★ 미슐랭]` 상위 등급 보너스**를 얻어 자연스러운 분업 경제를 형성합니다.

### 4.1 성장(`Lv.1~20`) · 전용 3D 성물 진화 · 조작 충돌 방지 규칙 (`RpgJobManager`, `SignatureWeaponManager`)
- **전직 및 성물 수령**: 광장 북서측 가제보 `성향 전직 교관 마스터냥` (`role = "class_master"`)에서 10대 직업 중 원하는 직업을 선택하면 `집사 성향 문장(class_emblem)`과 해당 직업의 **전용 3D 성물 무기/도구** 1개가 지급됩니다 (분실 시 GUI에서 무료 재발급 가능하되, 악용 방지를 위해 인벤토리/금고 내 중복 성물 생성 방지 또는 활성 직업 일치 시에만 스킬·보너스 발동).
- **성물 진화 및 강화 호환**:
  - `Lv.1~9`: `[1차 성물]` 기본 스탯 │ `Lv.10~19` (광장 교관에게 2차 초월 각성 `A` 또는 `B` 선택 시): `[2차 초월 성물]` 공격력/채광속도 상향 & `[Shift+우클릭]` 궁극기 해금 │ `Lv.20`: `[신화 완성형 성물]` 최종 스탯 및 전용 칭호 해금!
  - `BlacksmithManager` 확장: 10종의 직업 성물 무기/도구 모두 대장간 **`★1~★7` 별빛 세공** 및 **마법 룬 각인** 대상으로 허용하며, 레벨업/각성으로 성물 이름·로어가 갱신될 때 기존의 `yuumi_star_level`, `yuumi_rune_type`, 바닐라 인챈트 데이터가 절대 초기화되지 않고 보존되도록 구현하세요!
- **인게임 조작 비충돌 안전 필터 (`SignatureWeaponListener`)**:
  - 상자·화로·모루·문·버튼 등 상호작용 블록이나 NPC 엔티티를 우클릭할 때는 전투 스킬이 발동하지 않도록 최우선 패스합니다.
  - **`RANGER` (`weapon_ranger_bow`, `BOW`)**: 바닐라 `우클릭 홀드` 활 시위 당기기 사격을 방해하지 않도록, 주력기(`갈고리 공중 회피사격`)는 **`Shift+좌클릭` (또는 허공 좌클릭)** 에, 2차 각성 궁극기는 **`Shift+우클릭`** 에 매핑합니다.
  - **`ANGLER` (`tool_angler_harpoon`, `FISHING_ROD`)**: 바닐라 `우클릭` 낚시찌 투척/챔질을 방해하지 않도록, 전투 주력기(`심해 작살 그랩`)는 **`좌클릭` (허공/엔티티 방향)** 에, 2차 각성 궁극기는 **`Shift+우클릭` (찌 미투척 상태)** 에 매핑합니다.
  - **`CARPENTER` (`tool_carpenter_axe`, `DIAMOND_AXE`) & `FARMER` (`tool_farmer_scythe`, `DIAMOND_HOE`)**: 성물 도끼로 우클릭 스킬 시전 시 건축물 원목 껍질이 실수로 벗겨지는(`EntityChangeBlock` / axe strip) 현상을 기본 차단하고, 성물 대낫은 성숙 작물 우클릭 시 `3×3 수확 & 자동 재파종`을 우선 수행한 뒤 비작물/전투 상황에서 `초승달 수확 베기`를 발동합니다.

### 4.2 10대 기본 직업 → `Lv.10` 20대 초월 각성 스킬 명세

1. **🛡 `PALADIN` (성기사 — `weapon_paladin_sword`)**:
   - *패시브*: 레벨당 최대 체력 `+0.8` (`Lv.20` `+16 HP`), `마수 영혼석` 드롭률 `3.5배`, 주력기 시전 직후 `1.5초`간 피격 시 충격파로 반사하는 **저스트 가드** 판정.
   - *`[우클릭]` 유성 방패 돌진 (쿨 8초)*: 전방 돌격 충돌 적 범위 피해·에어본 & 반경 6m 몬스터 타겟 도발.
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 이지스 가디언** — `불멸의 황금 성역` (쿨 35초): 8초간 반경 7m 결계 전개, 내부 아군 받는 피해 `-55%` & 매초 체력 회복.
     - **🅱 발키리 크루세이더** — `천벌의 심판창` (쿨 28초): 전방 도약 강타로 성빛 번개 광역 기절(`2.5초`) & 5초 성화 지속 피해 장판.
2. **🗡 `SHADOW` (어쌔신 — `weapon_shadow_daggers`)**:
   - *패시브*: 상시 이동속도 `+12%`, `마수 영혼석` 드롭률 `3.5배`, 적 후방(`내적 > 0.35`) 타격 시 **백어택 확정 치명타(`1.45x`)**.
   - *`[우클릭]` 그림자 밟기 (쿨 7초)*: 조준한 14m 내 적의 등 뒤로 즉시 순보 이동 후 베기 (대상 처치 시 남은 쿨타임 50% 즉시 반환).
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 나이트 팬텀** — `사신의 표식 & 암습` (쿨 30초): 2.5초 은신/신속 III 돌입 및 다음 타격 대상에게 표식을 새겨 5초 뒤 누적 피해의 `40%` 폭발!
     - **🅱 질풍 검성** — `초승달 환영 난무` (쿨 26초): 반경 7m 적들 사이를 1.2초간 6연속 초고속 베기로 휩쓰는 광역 무적 난무기.
3. **🏹 `RANGER` (레인저 — `weapon_ranger_bow`)**:
   - *패시브*: 낙하 피해 `-50%`, `마수 영혼석` 드롭률 `3.5배`, 화살 3회 발사마다 적을 추적하는 **별빛 유도 마법 화살** 추가 발사.
   - *`[Shift+좌클릭]` 갈고리 공중 회피사격 (쿨 8초)*: 후방/시선 도약과 동시에 전방 부채꼴 5발 관통 화살 발사.
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 아스트랄 스나이퍼** — `유성우 화살 폭격` (쿨 30초): 조준 지점 반경 7m 상공에서 5초간 별똥별 폭발 화살 폭격.
     - **🅱 트릭스터 거너** — `중력 올가미 & 기관총 연사` (쿨 28초): 전방 전기 그물로 적들을 3.5초 속박하고 4초간 자동 고속 화살 연사!
4. **🪄 `ARCANIST` (아카니스트 — `weapon_arcane_grimoire`)**:
   - *패시브*: 성물 좌클릭 시 원거리 **비전 마법탄**(사거리 20m) 기본 발사, `마수 영혼석` 드롭률 `3.5배`, 스킬 쿨타임 자연 가속 `+8%`.
   - *`[우클릭]` 차원 중력 구체 (쿨 10초)*: 전방 구슬 발사 후 반경 5.5m 적들을 중심점으로 강하게 끌어모으고 범위 마법 피해.
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 공허 대마도사** — `대단원 3연속 파동포` (쿨 32초): 전방 직선 18m 거대 마법 파동 3연타 발사 & 마지막 타격 기절.
     - **🅱 아홉 목숨 대신관** — `아홉 목숨의 기적` (쿨 45초): 반경 12m 파티원 체력 대폭 회복 + 흡수 보호막 6하트 + **`문턱의 미궁` 내 사망(관전) 중인 파티원 1명 즉시 현장 부활!**
5. **⛏ `MINER` (채광가 — `tool_miner_drill`)**:
   - *패시브*: 야생/네더 광석 채굴 시 **광맥 연쇄 채광(최대 `4 + Lv/3`블록, 최대 10블록, `yuumi_hub` 제외)** + 원석 자동 제련 확률 `(20 + Lv*2)%` + **`미감정 별빛 지오드` 채굴 확률 4배(`5.0%`)**.
   - *`[우클릭]` 파쇄 진동파 (쿨 9초)*: 8초간 성급함 III 부여 + 전방 지면 충격파로 반경 5m 적 피해 및 **받는 피해 `+25%` 방어 파쇄 디버프**(`prop_dungeon_obelisk` 수정탑에는 3배 피해!).
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 심연 지질학자** — `지각 공명 투시` (쿨 35초): 반경 20m 내 다이아·에메랄드·고대잔해 위치에 반짝이는 마법 파티클 신호 표시 & 15초간 행운 III 버프.
     - **🅱 다이너마이트 굴착왕** — `3×3 굴착 오버드라이브 & 폭파` (쿨 30초): 야생에선 12초간 돌/심층암/네더랙 `3×3` 굴착 활성화 + 전방에 지형 비파괴 다이너마이트를 던져 몬스터 광역 폭발·기절!
6. **⚒ `BLACKSMITH` (대장장이 — `tool_smith_hammer`)**:
   - *패시브*: 대장간 별빛 세공 성공 확률 **`+8%p` 가산** + 지오드 감정 공임 무료 & 희귀 보상률 2배 + **룬 각인서 제작 재료 `40%` 절감 & `[명장 각인 +25%]` 대성공 발동**.
   - *`[우클릭]` 작열의 모루 타격 (쿨 8초)*: 전방 화염 강타·넉백 피해 및 반경 6m 파티원 착용 장비 내구도 소량 즉시 수리.
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 별빛 신화 명장** — `명장의 축복 벼리기` (쿨 40초): 반경 10m 아군 전원에게 20초간 저항 II & 화염 저항 부여 + 명장 룬 제작 대성공 확률 추가 `+20%p`.
     - **🅱 룬 배틀스미스** — `거대 태양모루 낙하` (쿨 32초): 조준 지점 상공에서 달궈진 모루(`BlockDisplay` 연출)를 낙하시켜 광역 폭발 피해 + 15초간 아군 전원 공격에 추가 화염 피해 부여!
7. **🌾 `FARMER` (대지주 — `tool_farmer_scythe`)**:
   - *패시브*: 성숙 작물 **`3×3` 일괄 수확 & 자동 재파종**(`Lv.10` 대농장주 각성 시 **`5×5`** 확대! 허브에서는 본인/공유 분양지 내에서만 동작) + **`황금 캣닢` 수확 확률 4배(`4.0%`)**.
   - *`[우클릭]` 초승달 수확 베기 (쿨 7초)*: 전방 넓은 부채꼴 범위 베기 & 적중 시 파티원이 닿으면 체력을 `2하트` 회복하는 초록빛 `생명 열매` 오브 드롭.
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 황금 대농장주** — `대지의 풍년 축복` (쿨 40초): 반경 9m 내 모든 농작물 즉시 최대 성장 촉진 + 반경 10m 파티원 재생 II & 포만감 회복!
     - **🅱 가시덩굴 드루이드** — `맹독 가시덩굴 정원` (쿨 28초): 반경 7m 지면에 6초간 가시덩굴 장판을 생성해 적들을 완전 이동불가(구속 X)로 묶고 지속 독/마법 피해!
8. **🎣 `ANGLER` (강태공 — `tool_angler_harpoon`)**:
   - *패시브*: 낚시 입질 대기시간 `40%` 단축 + `(20 + Lv*2)%` 2배 어획 + **`심해 대뱃살 필렛` 획득 확률 4배(`10.0%`)** + 상시 수중 호흡.
   - *`[좌클릭]` 심해 작살 그랩 (쿨 9초)*: 전방 16m 물보라 작살 투사체를 쏘아 적중한 몬스터에게 피해를 주고 **시전자 앞으로 강하게 낚아채 끌어당김**(& 둔화 II)!
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 칠대양 보물 사냥꾼** — `황금 어군 소환` (쿨 45초): 20초간 행운 V 및 낚시 보물/특수 어종 확률 급증 + 주변 파티원 치명타 확률 `+20%` 버프!
     - **🅱 해일 조련사** — `사나운 해일 소용돌이` (쿨 30초): 전방 지점에 5초간 거대한 물보라 소용돌이를 일으켜 적들을 중앙으로 끌어당기며 지속 피해 후 공중 에어본!
9. **🪓 `CARPENTER` (공방 목수 — `tool_carpenter_axe`)**:
   - *패시브*: 야생 나무 밑동 채굴 시 **통나무 일괄 벌목(`Tree-Capitator`, 최대 16블록, `yuumi_hub` 제외) & 묘목 자동 식재** + 3D 하우징 가구 공방 **공임 무료 & 재료 `45%` 절감 & `30%` 확률 `1+1` 추가 생산**.
   - *`[우클릭]` 통나무 쪼개기 강타 (쿨 8초)*: 전방 내리찍기 충격파 & 둔화 피해 + 8초간 벌목/채집 가속.
   - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
     - **🅰 황실 가구 건축가** — `장인의 설계 영역` (쿨 40초): 25초간 `Attribute.BLOCK_INTERACTION_RANGE +3.0` (블록 설치 사거리 연장!) & 점프 강화 II & 속도 증가!
     - **🅱 공성 기관사** — `고양이 발리스타 포탑 설치` (쿨 35초): 전방 바닥에 18초간 유지되는 목조 발리스타 포탑(`Fence + 석궁 ItemDisplay` 회전 조립체)을 설치해 반경 14m 내 적에게 매초 고속 화살을 자동 조준 발사! (종료 시 엔티티 깔끔히 제거).
10. **🍳 `CHEF` (미식 셰프 — `tool_chef_wok`)**:
    - *패시브*: 특선 만찬 조리 시 **공임 무료 & 식재료 `40%` 절감 & `35%` 확률 `1+1` 2인분 생산 & `★★★ 미슐랭 특선` 자동 승급(버프 지속·효율 `+35%`)**.
    - *`[우클릭]` 매운 육수 폭탄 토스 (쿨 8초)*: 특제 소스를 투척해 반경 4m 적 화상 & 공격력 약화(나약함 I) 부여.
    - *`Lv.10` 각성 (`[Shift+우클릭]`)*:
      - **🅰 삼성 미슐랭 마스터** — `3D 대연회 가마솥 오픈` (쿨 38초): 지면에 20초간 `prop_feast_cauldron`을 설치하여 반경 8m 아군 매초 체력 회복 및 **직업 액티브 스킬 쿨타임 회복 속도 `1.5배` 가속!**
      - **🅱 매드 연금 브루어** — `무지개 캣닢 플라스크 난사` (쿨 30초): 반경 8m 아군에게 힘 II·신속 II를 부여하고 적들에게는 받는 피해 `+25%` 취약 상태를 동시에 퍼붓는 광역 연금 폭발!

### 4.3 `20 SP` 3갈래 특전(Perk) 스킬트리 (`PerkTreeManager`) & 3슬롯 책갈피 유물함 (`BookmarkPouchManager`)
- **특전 스킬트리 GUI (`전직 교관` 또는 `class_emblem` 우클릭 탭)**:
  - 직업 레벨당 `1 SP` 획득(최대 `20 SP`). 3개 갈래(`🔴 위력·숙련 [스킬 데미지 +3%/pt & 채집 범위 강화]`, `🔵 기동·회전율 [스킬 쿨타임 -2.5%/pt & 이속 +1%/pt]`, `🟢 효율·파티 [특산물 추가 드롭·제작 1+1 대성공 확률 +3%/pt & 파티 버프 범위 확대]`, 각 갈래 최대 `10 pt`)에 클릭 투자 가능.
  - **`3,000 츄르`로 언제든 특전 포인트만 즉시 초기화** 가능!
- **공공 공방 vs 장인 효율 분업 워크스테이션 (`ArtisanWorkshopManager`)**:
  - **대장간 공방 탭** (서쪽 `대장장이 모루냥` GUI 내 통합): 지오드 감정(비전공자 `1,500 츄르` 공임 / `BLACKSMITH` 무료 & 희귀율 2배), 5종 룬 각인서 제작(`홍련[화염추가타]`, `빙결[둔화·빙결타]`, `뇌전[연쇄번개]`, `질풍[이속·회피]`, `철벽[피해감소]` — 비전공자 `영혼석 5 + 세공석 3 + 2,500 츄르` 일반 룬 / `BLACKSMITH` `영혼석 3 + 세공석 2 + 공임 0원` & `[명장 각인 +25%]` 대성공!), 장비 룬 부여 슬롯 제공.
  - **목공 가구 공방 탭** (서쪽 `목재·건축 상인냥` GUI 내 통합): 3D 하우징 가구 4종(`plushie_yuumi`, `furn_cat_tower`, `furn_kotatsu`, `furn_neon_sign`) 제작 — 비전공자 정가 재료 + `3,000 츄르` 공임 / `CARPENTER` 재료 `45%` 절감 + 공임 무료 + `30%` `1+1` 생산!
  - **특선 만찬 조리대 탭** (동쪽 `식당 주방장냥` GUI 내 통합): 만찬 3종(`용맹`, `질풍`, `풍요`) 조리 — 비전공자 정가 식재료 + `1,500 츄르` 공임(`★★`) / `CHEF` 식재료 `40%` 절감 + 공임 무료 + `35%` `1+1` + `★★★ 미슐랭 특선` 승급!
- **3슬롯 마법 책갈피 유물함**:
  - 인벤토리 칸을 차지하지 않는 전용 3슬롯(`charm_crimson_claw`, `charm_starlight_aegis`, `charm_golden_whisker`, `charm_vampiric_purr`, `you_and_me_bell`, `nine_lives_charm` 중 서로 다른 최대 3종 장착). 미궁 수문장 상점에서 `고대 고양이 금화`로 정가 구매 가능!

---

## 5. Module 4: 카지노 2F 『츄르 증권 거래소』 · 『3D 가챠 & 분양지 가구 배치』 · 『명예의 전당』

1. **카지노 2F 『츄르 증권 거래소』 (`kr.yuumi97.core.stock.ChuruStockManager`)**:
   - 서쪽 NPC 상점 시세는 100% 고정 유지하고, 4대 예능 종목(`CATNIP_BIO`, `TUNA_MARINE`[배당주], `STARLIGHT_MINING`[대장간 세공 연동주], `VOID_TECH`[고변동 작전주])을 10분 주기로 갱신하며 속보 뉴스와 2F `TextDisplay` 전광판을 업데이트합니다 (종목당 최대 `200주` 보유 한도, 매도 거래세 `2%` 카지노 잭팟 적립).
2. **황금 캡슐 가챠 & 분양지 3D 가구 배치 (`kr.yuumi97.core.housing.GachaAndFurnitureManager`)**:
   - 가챠 1회: `황금 뽑기 캡슐 1개` OR `고대 고양이 금화 3개` OR `카지노 칩 1,500개` → 3D 모자 3종, 3D 가구 4종, 세공 보호권/세공석, 황금 츄르 획득.
   - 본인/공유 분양지(`plotManager.canBuildInHub`) 내 3D 가구 4종 우클릭 설치(`ItemDisplay` + `Interaction`), **빈손 Shift+우클릭 45도 회전**, **일반 우클릭 `furn_kotatsu` 착석**, **Shift+좌클릭 100% 아이템 회수**(부지 매각/초기화 시 자동 회수 포함).
3. **명예의 전당 & 칭호 (`kr.yuumi97.core.social.HallOfFameManager`)**:
   - 광장 북동측 단상에 `최고 갑부 Top 3`, `미궁 정복왕 Top 3`, `별빛 세공 명장 Top 3` 실시간 홀로그램 갱신 및 `/칭호` 채팅·탭리스트 컬러 칭호 장착 지원.

---

## 6. 통합 배선 (`YuumiPlugin.java` · `plugin.yml` · `config.yml`) 및 최종 완료 조건

1. `plugin.yml` 버전을 `'3.0.0'`으로 갱신하고 `/파티` (`aliases: [party, 미궁]`), `/칭호` (`aliases: [title]`), `/전직` (길찾기 안내), `/증권` (길찾기 안내)를 등록합니다.
2. `/메뉴` (`차원 안내 수첩`)에 플레이어의 **현재 성향 직업·각성·레벨**, **미궁 클리어 횟수**, 신규 시설 나침반 길찾기를 통합하고, OP 테스트용 `/메뉴 give <id>`에 **신규 아이템 30종 전부**를 등록합니다.
3. **최종 완료 검증 조건**:
   - `powershell -ExecutionPolicy Bypass -File E:\MinGem\scripts\build-all.ps1` 빌드 성공 (`Exit Code 0`).
   - `powershell -ExecutionPolicy Bypass -File E:\MinGem\scripts\verify-boot.ps1` 실행 시 `Paper 1.21.4` 부팅부터 `hub_v3.built` 스탬핑, `yuumi_dungeon` 로드, 정상 종료까지 **`ERROR` / `Exception` / 누락 `.nbt` 경고 0건** 확인!
