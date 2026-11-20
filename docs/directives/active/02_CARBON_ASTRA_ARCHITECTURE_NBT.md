# 🏛️ [Stream 02 — 하이엔드 건축 & `.nbt` 설계 지시서] 3대 심리스 메가던전 & 허브 만능 길드 대공방 제작

- **권장 수행 모델**: `Gemini Carbon` *(또는 `GPT Astra` Architecture Studio)*
- **참조 비주얼 컨셉 보드**: [`../concepts/03_seamless_megadungeons_concept.png`](../concepts/03_seamless_megadungeons_concept.png)
- **연계 코어 지시서**: [`./03_SOL_CORE_SYSTEMS_OVERHAUL.md`](./03_SOL_CORE_SYSTEMS_OVERHAUL.md)

---

## 0. 작업 경계 및 무충돌 병렬 규칙 (Zero-Collision Rules)

1. **독점 쓰기 허용 경로 (Write Scope)**:
   - `E:\MinGem\assets\architecture-blueprints\**` (프로그래매틱 `.nbt` 건축 컴파일러 스크립트, 팔레트 검증기, 평면도·단면도·아이소메트릭 프리뷰 렌더 시트)
   - `E:\MinGem\yuumi-core\src\main\resources\structures\overhaul\**` (최종 납품 `.nbt` 20종 및 `manifest.json`)
2. **수정 절대 금지 경로 (Forbidden Scope)**:
   - `E:\MinGem\assets\yuumi97-production\**`, `E:\MinGem\resourcepack-gen\**`, `E:\MinGem\yuumi-core\src\main\java\**`, `E:\MinGem\scripts\**`, `E:\MinGem\server\**`
   - 병렬 진행 중 Gradle 빌드(`build-all.ps1`)를 실행하지 마세요.

---

## 1. Minecraft `1.21.4` Structure `.nbt` 기술 규격 및 미학 원칙

1. **표준 Gzipped Structure NBT 포맷 (`DataVersion: 4189`)**:
   - 루트 Compound 태그: `DataVersion` (`Int = 4189`), `size` (`List<Int>[W, H, L]`), `palette` (`List<Compound>{Name, Properties}`), `blocks` (`List<Compound>{pos: List<Int>[x,y,z], state: Int}`), `entities` (`List` — 빈 리스트 유지).
   - **금지 블록**: `minecraft:jigsaw`, `minecraft:structure_block`, `minecraft:structure_void`, `minecraft:command_block`, `minecraft:spawner`, `minecraft:trial_spawner` 사용 금지. (빈 내부 공기층은 반드시 `minecraft:air` 블록 엔트리로 명시해 스탬핑 시 이전 잔재 블록을 깨끗하게 덮어쓰도록 보장하세요!)
   - **방향성 블록 Properties 완비**: 계단(`facing`, `half`, `shape`), 반블록(`type`), 기둥(`axis`), 랜턴(`hanging`), 사슬(`axis`), 울타리/담장(`north/south/east/west`, `up`), 책장·작업대(`facing`)의 블록 상태를 정확히 계산하여 아치·몰딩·지붕 처마가 깨지지 않게 조립합니다.
2. **바닐라 호환 치수 준수 (`각 모듈 최대 48×48×48 이하`)**:
   - 221블록 길이의 초대형 심리스 메가던전을 **6개의 정밀 직결 모듈(`Zone 1 ~ Zone 6`, 각 모듈 모든 축 `≤ 45`)** 로 분할 컴파일하여 바닐라 Structure Block 한계와 Paper `StructureManager` 고속 로딩을 동시에 만족시킵니다.
3. **프로그래머 아트(단조로운 평벽 박스) 절대 금지 — 5대 건축 디테일 필수 적용**:
   - **① 외벽·내벽 3겹 깊이감(Depth Layering)**: 평평한 1겹 벽 대신 `[구조 기둥(Pillar, 돌출) ➔ 아치 보/코니스(Stairs/Slabs) ➔ 음각 벽면(책장/구리 격자/스테인드글라스)]` 3단 입체 레이어링을 모든 홀에 적용합니다.
   - **② 바닥 그라데이션 & 패턴 인레이**: 단일 블록 도배를 금지하고 테두리 보더 + 중앙 문양 + 하부 투광층(유리 아래 은하 조명 / 구리 격자 아래 용암 수로)을 설계합니다.
   - **③ 천장 볼트(Ribbed Vault) & 대형 행잉 오브제**: 평천장을 금지하고 고딕 리브 아치, 돔, 사슬에 매달린 거대 혼천의·용암 도가니·샹들리에로 수직 개방감을 극대화합니다.
   - **④ 무셰이더 자체 발광 대비**: 기본 마인크래프트에서도 환상적인 분위기가 나도록 `Sea Lantern`, `Pearlescent/Ochre Froglight`, `Shroomlight`, `End Rod`, `Soul Lantern`, `Lava`, `Amethyst Cluster`를 건축 몰딩 내부에 숨김/간접 조명으로 배치합니다.

---

## 2. 허브 도시 신규 랜드마크 건축물 2종 (`yuumi_hub`)

### 2-1. `hub_grand_utility_hall.nbt` — 『🏰 문턱의 만능 길드 대공방 (Grand Expedition & Utility Hall)』
- **치수 (`size`)**: **`[29, 26, 25]`** (`X폭 29 × Y높이 26 × Z깊이 25`)
- **월드 스탬핑 기준점**: **`(X = -36, Y = 64, Z = -44)`** (`local [0,0,0]` = `world [-36, 64, -44]`, `local Y = 0`이 바닥 기단 `world Y = 64`, 실내 보행 바닥은 `local Y = 0` → 플레이어 발 위치 `local Y = 1` / `world Y = 65`)
- **건축 양식 & 외관 디렉팅**:
  - 중앙 분수 광장(남쪽 `+Z`)과 북쪽 대포탈·미궁 성소(북쪽 `-Z`), 동쪽 DPS 수련장(`+X`) 어디서든 문턱 없이 곧장 걸어 들어올 수 있는 **4면 개방형 대형 벚꽃·백금 고딕 아치 회관 + 북서 코너 26m 황금 고양이 천문 시계탑 첨탑** 복합 랜드마크!
  - 팔레트: `Smooth Quartz`, `Quartz Pillar`, `Polished Diorite`, `Deepslate Tiles`(지붕 기와), `Cherry Wood / Stripped Cherry Log / Cherry Stairs`(따뜻한 목조 보와 아치), `Gold Block` & `Ochre Froglight`(시계탑 크라운 및 금장 포인트), 지붕 위 만개한 벚꽃 잎(`Cherry Leaves`, `persistent=true`) 장식.
  - **시계탑 다이얼 마운트 면 확보**: 북서 상부 시계탑(`local X = 3..9, local Z = 3..9, local Y = 12..25`)의 동·서·남·북 4면 `local Y = 15..17` 외벽을 평탄한 남색·금장 액자면으로 마감하여 `SOL`이 기존 3D `prop_plaza_clock_face`를 오차 없이 부착할 수 있게 합니다.
- **실내 4대 기능 베이(Bay) 고정 좌표 계약 (`SOL` 상호작용 바인딩 연동)**:
  1. **중앙 별빛 원정 성수대 (`local X = 13..15, local Z = 11..13`)**:
     - `Sea Lantern`과 `Waterlogged Quartz Stairs`로 감싼 맑은 성수 분수대 (클릭/접근 시 체력·허기 회복 이펙트 연동).
  2. **서측 제련 · 무한 모루 · 대장 공방 베이 (`local X = 2..7, local Z = 5..19`)**:
     - 파손 방지 무한 모루 `ANVIL` 3기 (`local [3, 1, 10..12]`), `SMITHING_TABLE` 2기, `GRINDSTONE` 2기, `STONECUTTER` 1기, 벽난로 화덕에 매립된 `BLAST_FURNACE` 3기.
     - **`공방 대장 보조냥` NPC 스폰 패드**: `local [5, 1, 7]` (`world [-30.5, 65.0, -36.5]`, `Chiseled Quartz` 바닥 마커, 상부 `local Y = 1..4` 완전 개방!).
  3. **북측 30Lv 비전 인챈트 & 연금 서재 베이 (`local X = 10..18, local Z = 2..7`)**:
     - **`ENCHANTING_TABLE`** (`local [14, 1, 4]`, `world [-21.5, 65.0, -39.5]`): 주위 1칸 공기 간격을 두고 **정확히 15개 이상의 `BOOKSHELF`** 를 U자 서가로 둘러 **100% 확정 30레벨 마법부여**가 작동하도록 보장!
     - 좌우 사이드에 `BREWING_STAND` 2기, `CARTOGRAPHY_TABLE` 1기, `LOOM` 1기 배치.
  4. **동측 개인금고 · 엔더상자 · 목공/만찬 베이 (`local X = 21..26, local Z = 5..19`)**:
     - `ENDER_CHEST` 2기 (`local [25, 1, 10..11]`), `CRAFTING_TABLE` 3기, 훈연 화덕 `SMOKER` 3기, 남동 코너 안전 폐기 소각로 `LAVA_CAULDRON` (`local [25, 1, 19]`, 주변 안전 난간 마감).
     - **`금고지기 보조냥 (4탭 개인금고)` NPC 패드**: `local [23, 1, 7]` (`world [-12.5, 65.0, -36.5]`, 상부 `Y=1..4` 개방).
     - **`목공·만찬 공방 보조냥` NPC 패드**: `local [23, 1, 15]` (`world [-12.5, 65.0, -28.5]`, 상부 `Y=1..4` 개방).

### 2-2. `hub_guidebook_kiosk.nbt` — 『📖 중앙 광장 만능 가이드북 키오스크』
- **치수 (`size`)**: **`[7, 9, 7]`**
- **월드 스탬핑 기준점**: **`(X = 5, Y = 64, Z = 5)`** (중앙 스폰 `(0.5, 65.0, 12.5)`에서 고개를 들면 분수대 우측 전방에 한눈에 들어오며 십자 중앙 대로 `X = -4..+4`를 전혀 가리지 않는 황금 입지!)
- **조형 디테일**:
  - 4면 계단으로 올라설 수 있는 백금 석영 & 금장 기단(`local Y = 0..1`) 중앙 **`local [3, 2, 3]` (`world [8.5, 66.0, 8.5]`)** 에 남쪽 스폰 방향(`facing=south`)을 바라보는 **`LECTERN`(독서대)** 설치.
  - 네 모서리 날렵한 벚꽃·석영 기둥 위로 고양이 귀 실루엣의 소형 돔 캐노피(`local Y = 6..8`)와 `Lantern` 조명을 얹고, 독서대 바로 위 `local Y = 3..5` 공간을 비워 `SOL`이 회전하는 대형 3D `guidebook` 홀로그램을 띄울 수 있게 설계합니다.

---

## 3. 3대 심리스 연결형 메가던전 모듈 18종 (`yuumi_dungeon`)

### 3-1. 심리스 직결 척추(Spine) & 관문(Gate Seam) 절대 규약 (`SOL` 연동 필수 계약!)
각 파티 던전 인스턴스는 기준선 `cx = slot * 1024`, 바닥 기준 `BASE_Y = 60`에서 **남쪽(`Z = -1`) 입구에서 출발해 북쪽(`Z = -221`) 대보스 대성당까지 6개 모듈이 1블록의 틈도 없이 일렬 직결**됩니다!

```text
[남쪽 입구 Z=-1] ➔ Zone 1 (28m) ➔ [성문1 Z=-28] ➔ Zone 2 (38m) ➔ [성문2 Z=-66] ➔ Zone 3 (42m)
  ➔ [성문3 Z=-108] ➔ Zone 4 (36m) ➔ [성문4 Z=-144] ➔ Zone 5 (32m) ➔ [성문5 Z=-176] ➔ Zone 6 대보스 대성당 (45m, Z=-221)
```

- **보행 주 바닥 높이 통일 (`local Y = 6` / `world Y = 66`)**:
  - 모든 모듈의 `local Y = 0..5` 하부 6칸은 **투명 은하수 심연(1장) / 용암 수로 및 도가니 분지(2장) / 별빛 공허 협곡(3장)** 연출을 위한 하부 입체 공간입니다! 최하단 `local Y = 0`에는 추락 이탈 방지용 `minecraft:bedrock` 또는 테마 바닥을 깔아주세요.
- **모듈 간 직결 관문 개구부 규격 (`9폭 × 8높이` 표준 아치)**:
  - 모듈 중심축 `midX = (W - 1) / 2`를 기준으로, 남쪽 접속면(`local Z = L - 1`)과 북쪽 출구면(`local Z = 0`, 단 Zone 1 남벽과 Zone 6 북벽은 마감벽) 모두 **`local X ∈ [midX - 4 .. midX + 4]` (폭 9칸), `local Y = 6`은 단단한 통로 바닥, `local Y ∈ [7 .. 14]` (높이 8칸)은 완전히 뚫린 `AIR` 개구부**로 규격을 100% 일치시킵니다! (`SOL`이 각 북쪽 경계 `local Z = 0`에 상승 개방되는 거대 룬 성문을 설치합니다.)

| 구역 | 파일명 패턴 (`T=1,2,3`) | 고정 치수 `[W, H, L]` | 스탬핑 월드 좌표 `(X, Y, Z)` | 공간 역할 및 필수 건축 요소 |
| :---: | :--- | :---: | :--- | :--- |
| **Zone 1** | `ch<T>_zone1.nbt` | **`[31, 24, 28]`** | `(cx - 15, 60, -28)` | **진입 성문 & 전초 로비**: 파티 스폰점 `(cx+0.5, 67, -6.5)` · 좌우 정비 모루/성수대 · 북측 제1성문 아치 |
| **Zone 2** | `ch<T>_zone2.nbt` | **`[39, 28, 38]`** | `(cx - 19, 60, -66)` | **복층 제1전투 전당**: 1F 중앙 홀(`local Y=6`) + 양측 계단으로 연결된 **2F 발코니 회랑(`local Y=14`, 원거리 커스텀 몹 저격석)** + 천장 대형 오브제 |
| **Zone 3** | `ch<T>_zone3.nbt` | **`[31, 28, 42]`** | `(cx - 15, 60, -108)` | **기믹 회랑 & 협곡 대교**: 하부(`local Y=1..5`) 심연/용암/공허 위로 가로지르는 폭 7칸 중앙 다리(`local Y=6`) + 좌측 또는 우측 벽면 숨겨진 보물 발코니 |
| **Zone 4** | `ch<T>_zone4.nbt` | **`[37, 28, 36]`** | `(cx - 18, 60, -144)` | **중보스 성소 & 화톳불 쉼터**: 남측 원형 중보스 투기장(`중심 Z=-124`) + 북측 1단 높은 **정비 화톳불 & 축복 제단 성소(`중심 cx, Y=67, Z=-138`)** |
| **Zone 5** | `ch<T>_zone5.nbt` | **`[33, 26, 32]`** | `(cx - 16, 60, -176)` | **고대 퍼즐 아트리움**: 중앙 초점 제단 `(cx, 66, -160)` + 4방위 대칭 퍼즐 기단 `(cx ± 9, 66, -160 ± 7)` (`local Y=7..10` 비움) + 북측 대성당 정문 파사드 |
| **Zone 6** | `ch<T>_zone6.nbt` | **`[45, 32, 45]`** | `(cx - 22, 60, -221)` | **대보스 대성당 (`45×32×45`)**: 천장고 25m 돔/볼트 대공간 + **전멸기 엄폐용 4대 불투명 대석주 `(cx ± 10, Y=67..82, Z=-199 ± 9)`** + 북측 보스 단상 `(cx, 67, -209)` + 최북단 보물고/귀환단 `(cx, 67, -216)` |

---

### 3-2. 3대 차원 테마별 건축 디렉팅 상세 (`concepts/03_seamless_megadungeons_concept.png` 기준)

1. **제1장 `ch1_zone1..6.nbt` — 『🌌 별빛 천문 대서고 (Starlight Astral Archive)』**:
   - **구조 & 팔레트**: `Polished Deepslate` / `Deepslate Tiles` 바닥 프레임과 순백 `Quartz Pillar` 수직 열주, 벽면 가득 채워진 2층 높이 `Chiseled Bookshelf` & `Bookshelf` 고딕 아치 서가, `Spruce / Dark Oak Stairs` 나선·절곡 계단, `Dark Prismarine` 천장 볼트.
   - **시그니처 연출**: 중앙 통로와 대성당 바닥 일부를 `Cyan / Blue Stained Glass`(`local Y = 6`)로 시공하고 그 아래 `local Y = 1..4` 칠흑 공간(`Black Concrete`) 속에 `Sea Lantern`, `End Rod`, `Amethyst Cluster` 별자리를 수놓아 **발밑에 깊은 은하수가 흐르는 환상적 투시감**을 구현! 천장에는 황금 블록·울타리 고리로 빚은 **거대 혼천의(Armillary Sphere)** 와 청록 촛대 샹들리에를 매답니다. Zone 6 북벽 상단에는 거대한 반구형 별빛 천문대 돔과 천체 망원경 실루엣을 구축합니다.

2. **제2장 `ch2_zone1..6.nbt` — 『🔥 홍련의 거신 용광로 성채 (Crimson Titan Foundry)』**:
   - **구조 & 팔레트**: `Polished Blackstone Bricks`, 금맥이 박힌 `Gilded Blackstone` 몰딩, `Waxed Copper Block / Oxidized Copper / Copper Grate` 산업용 격자 다리와 증기 파이프, `Crimson Hyphae` 아치 보, 굵은 `Chain`과 `Iron Bars`.
   - **시그니처 연출**: Zone 2·3·6 상부 벽면에서 거대한 흑요석 사슬에 매달린 **용암 도가니(Crucible)** 로부터 주황빛 `Lava` 폭포 줄기가 양측 벽면 수로를 타고 하부 분지(`local Y = 1..4`)로 쏟아져 내립니다(보행로 `local Y = 6` 위로 용암이 넘치지 않도록 석재 턱 마감!). 벽면에는 입체적인 황동 톱니바퀴(구리·금 계단/다락문 조합)를 배치하고, Zone 6 북벽 정면에는 불타는 화염 아귀를 벌린 **거신병 용광로 흉상 왕좌**를 세웁니다.

3. **제3장 `ch3_zone1..6.nbt` — 『🔮 공허의 수정 대성당 (Void-Glass Eclipsed Cathedral)』**:
   - **구조 & 팔레트**: 눈부신 백색 `Smooth Quartz` & `Quartz Bricks` 고딕 성당 아치, 연보라 `Purpur Pillar`, 차원 눈물이 흐르는 `Crying Obsidian` 문틀, `Amethyst Block`, `Pearlescent Froglight` & `End Rod`.
   - **시그니처 연출**: 하부 `local Y = 1..5`는 칠흑의 공허(`Black Concrete` 심연 바닥 + 떠다니는 작은 발광 자수정 별 조각들)로 비워 성당 회랑 전체가 우주 허공 위에 떠 있는 듯한 초현실적 경관을 연출합니다. 허공 곳곳에 **산산조각 나 시간이 멈춘 듯 정지해 있는 `Magenta / Purple / Pink Stained Glass Pane` 파편 군집**을 배치하고, Zone 6 대보스 대성당 북벽 상단(`local Y = 14..29`)에는 직경 19블록 규모의 압도적인 **개기일식 장미창(Eclipse Rose Window — 흑요석 테두리 + 보라/마젠타 스테인드글라스 기하학 문양)** 을 새겨 넣습니다!

---

## 4. 자동 검증 및 건축 도면 렌더 검수 체크리스트

1. **`manifest.json` 및 자동 무결성 테스트 (`assets/architecture-blueprints/verify-structures.mjs`)**:
   - 총 **20개 `.nbt` 파일**(`hub_grand_utility_hall.nbt`, `hub_guidebook_kiosk.nbt`, `ch1_zone1..6.nbt`, `ch2_zone1..6.nbt`, `ch3_zone1..6.nbt`)이 모두 유효한 Gzip NBT(`DataVersion: 4189`)인지 검증.
   - 각 던전 모듈의 남·북 관문 개구부(`midX - 4 .. midX + 4`, `Y = 7..14` AIR, `Y = 6` Solid Floor)가 1블록 오차도 없이 관통 연결되는지 자동 어셈블리(Seam Continuity) 테스트 통과 필수!
   - `hub_grand_utility_hall.nbt` 내 30Lv 인챈트 책장 개수(`≥ 15`), NPC 패드 상부 `Y=1..4` 비움, 금지 블록 `0개` 검증.
2. **시각 단면도 & 아이소메트릭 프리뷰 이미지 생성 (`assets/architecture-blueprints/review/`)**:
   - 컴파일된 실제 `.nbt` 블록 데이터를 읽어 2D/아이소메트릭 컬러 렌더 시트(`review/hub-utility-hall-sheet.png`, `review/megadungeon-ch1-ch2-ch3-cutaways.png`)를 출력하고, 복층 계단 동선 끊김 여부·기둥 비례·조명 분포를 직접 확인한 뒤 납품해 주세요.
