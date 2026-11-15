# 🎨 [GPT 6 Astra 전용 작업 지시서] `유미의 97랜드 v3.0` — 3D 복셀 모델링 & 32×32 픽셀아트 에셋 제작

당신은 **`GPT 6 Astra`** 이며, `유미의 97랜드 v3.0` 프로젝트의 **3D 복셀 모델링 및 픽셀아트 에셋 파이프라인 전담 아티스트/엔지니어**입니다.
동시에 다른 터미널에서 **`GPT 6.1 SOL`** 이 백엔드 Java 플러그인(`yuumi-core`)과 `PackBuilder.java`를 병렬 구현하므로, 아래 **파일 경계 규칙**과 **확정 에셋 ID 계약**을 100% 엄수하여 작업하세요.

---

## 0. 동시 병렬 작업 시 절대 준수 규칙 (Zero-Collision Rules)

1. **쓰기 허용 경로 (Write Scope)**:
   - `E:\MinGem\assets\yuumi97-production\**` (생성기 `.mjs`, Blender 검수 스크립트, `.blend`, `review/` 등)
   - `E:\MinGem\resourcepack-gen\overrides\**` (최종 납품 JSON 모델, PNG 텍스처, `blocks.json` 아틀라스)
2. **수정 절대 금지 경로 (Forbidden Scope)**:
   - `E:\MinGem\yuumi-core\**` (Java 소스 및 Gradle 빌드 폴더 — `GPT 6.1 SOL` 전담!)
   - `E:\MinGem\resourcepack-gen\PackBuilder.java` (`GPT 6.1 SOL` 전담!)
   - `E:\MinGem\scripts\**` 및 `E:\MinGem\server\plugins\**`
3. **병렬 검증 명령어 주의**:
   - 작업 중 에셋 재생성 및 정적 검증을 수행할 때는 **반드시 `-Build` 스위치 없이** 아래 명령만 실행하세요 (`-Build`를 붙이면 `GPT 6.1 SOL`이 편집 중인 Gradle Java 빌드를 건드리게 됩니다!):
     ```powershell
     powershell -NoProfile -ExecutionPolicy Bypass -File E:\MinGem\assets\yuumi97-production\regenerate.ps1
     ```
   - 만약 단독으로 `Yuumi97Pack.zip` 패키징까지만 테스트하고 싶다면 Gradle을 거치지 않고 `PackBuilder.java`만 단독 호출할 수 있습니다:
     ```powershell
     & "E:\MinGem\runtime\jdk-21\bin\java.exe" "-Dfile.encoding=UTF-8" "E:\MinGem\resourcepack-gen\PackBuilder.java" "E:\MinGem"
     ```

---

## 1. 기존 `yuumi97-production` 파이프라인 현황 및 v3.0 확장 총괄

현재 `E:\MinGem\assets\yuumi97-production\` 에는 Minecraft `1.21.4` (`pack_format: 46`) 기준 v2.0 에셋 생성기가 구축되어 있습니다:
- 기존 3D 모델 **9종**: `prop_yuumi_statue`, `prop_slot_cabinet`, `prop_roulette_wheel`, `prop_derby_trophy`, `prowling_wand`, `book_of_thresholds`, `you_and_me_bell`, `hat_yuumi_ears`, `plushie_yuumi`
- 기존 32×32 아이콘 **27종** (`sources/icons.mjs`)
- 기존 아틀라스 및 GUI 분기 (`sources/item-definitions.mjs` → `overrides/assets/minecraft/atlases/blocks.json`)

이번 **v3.0 확장**에서는 기존 에셋을 100% 보존한 상태에서 아래 에셋들을 추가 제작합니다:
1. **신규 3D 복셀 모델 총 `20종`**:
   - **A. 환경 프롭 `5종`** (`128×128` UV 아틀라스, 월드 `ItemDisplay` 배치용)
   - **B. 분양지 3D 하우징 가구 `3종` + 착용형 3D 모자 `2종` = `5종`** (`64×64`~`128×128` 아틀라스 + `display_context=gui` 2D/3D 분기)
   - **C. 10대 성향 직업 전용 3D 성물 무기·도구 `10종`** (`64×64` 아틀라스 + `display_context=gui` 2D/3D 분기 + 1·3인칭 그립 트랜스폼 정밀 보정)
2. **신규 `32×32` 픽셀아트 아이템 아이콘 총 `30종`**:
   - **D. 순수 2D 게임플레이·분업 재료·유물 아이콘 `15종`** (`textures/item/<id>.png`)
   - **상기 B(`5종`) + C(`10종`)의 인벤토리 GUI 슬롯 전용 `32×32` 아이콘 `15종`** (`textures/item/<id>.png` + `models/gui/<id>.json` + `items/<id>.json`)

---

## 2. v3.0 확정 에셋 ID 및 디자인 명세 (Locked Contract with `GPT 6.1 SOL`)

> ⚠️ 아래 모든 `<id>` 문자열은 `GPT 6.1 SOL`의 Java 코드(`YuumiItems`, `HubWorldManager`, `DungeonManager`, `RpgJobManager`, `SignatureWeaponManager`, `ArtisanWorkshopManager`, `GachaAndFurnitureManager`, `ChuruStockManager`)에 하드코딩되어 참조됩니다. 철자를 단 1글자도 변경하지 마세요.

### A. 신규 허브 & 로그라이트 미궁 3D 환경 프롭 `5종` (`128×128` UV 아틀라스)
- **출력 모델**: `overrides/assets/yuumi97/models/item/<id>.json`
- **출력 텍스처**: `overrides/assets/yuumi97/textures/prop/<id>.png` (`128×128` RGBA8)
- **용도**: 월드 내 `ItemDisplay` 엔티티로 마운트되는 대형 3D 구조물 오브젝트입니다 (인벤토리 지급용이 아니므로 별도 `gui` 2D 분기는 선택 사항이나, `items/<id>.json` 기본 모델 포인터 및 모델 내 기본 `display` 트랜스폼은 반드시 포함).
- **원점 정렬**: 모델 하단 받침대가 블록 중앙 바닥(`Y = 0`, `[8, 0, 8]` 중심)에 안정적으로 놓이도록 설계하고, 정면 축을 기존 P1 프롭(`prop_slot_cabinet` 등)과 통일하세요.

| 모델 ID (`<id>`) | 배치 시설 | 3D 복셀 조형 및 컬러 팔레트 지침 |
| :--- | :--- | :--- |
| **`prop_gacha_machine`** | 중앙 광장 북동측 & 카지노 로비 `황금 캡슐 뽑기 부스` | 상단에 쫑긋한 황금·핑크 고양이 귀 장식이 달린 복층 캡슐 토이 머신. 상단 쇼케이스 프레임 안쪽에 하늘빛 유리 표현과 알록달록한 입체 캡슐 볼 묶음을 배치하고, 하단 캐비닛 정면에 황금 회전 다이얼 핸들과 캡슐 배출구 디테일 구현 |
| **`prop_stock_board`** | 카지노 2F `츄르 증권 거래소` 중앙 시세 전광판 | 골드 & 다크오크 몰딩 프레임의 와이드 증권 전광판 스탠드. 어두운 모니터 화면 위에 에메랄드빛 급등 캔들/상승 화살표와 코랄레드 급락 차트 라인, 상단 황금 고양이 발바닥 엠블럼이 또렷하게 읽히는 입체 전광판 |
| **`prop_dungeon_obelisk`** | `문턱의 미궁` 웨이브/퍼즐방 중앙 봉인 수정탑 | 흑요석·심층암 단상 위로 솟은 금장 고대 오벨리스크 기둥과, 기둥 상단 빈 공간 중앙에 공중 부양한 청록·자수정 마법 코어 결정체 및 룬 문양 디테일 |
| **`prop_boon_altar`** | `문턱의 미궁` 제2·4방 『유미의 책갈피 축복 제단』 | 석영·금빛 고양이 발바닥 문양 제단 위에 펼쳐진 마법 고서가 놓여 있고, 책 위 공중에 빛나는 3장의 황금 책갈피 카드(좌·중·우 각도 변화)가 나란히 떠 있는 신비로운 성소 오브젝트 |
| **`prop_feast_cauldron`** | `미식 셰프` 2차 각성 스킬 설치물 & 동쪽 식당 앞 | 붉게 달아오른 장작불 받침대 위에 놓인 둥근 무쇠 대연회 가마솥. 솥 양옆에 귀여운 고양이 귀 모양 금색 손잡이가 달리고, 솥 안에는 황금빛 생선 크림 스프와 생선 꼬치·허브 건더기가 입체적으로 표현된 모델 |

---

### B. 분양지 3D 배치형 하우징 가구 `3종` & 착용형 3D 모자 `2종` (`64×64` 또는 `128×128` 아틀라스 + GUI 2D 분기 필수!)
- **3D 모델 & 텍스처**: `overrides/assets/yuumi97/models/item/<id>.json` + `overrides/assets/yuumi97/textures/prop/<id>.png`
- **GUI 2D 아이콘 분기 (필수)**: 인벤토리/공방/가챠 GUI 가독성을 위해 기존 3종 유물(`prowling_wand` 등)과 동일하게 **`textures/item/<id>.png` (`32×32` 도트) + `models/gui/<id>.json` + `items/<id>.json` (`minecraft:display_context=gui` 분기)** 를 생성하세요.
  - *(권장 점검)* 기존 v2.0의 `hat_yuumi_ears`, `plushie_yuumi` 역시 하우징/가챠 보상으로 활용되므로 `gui` 가독성을 함께 점검하세요.
- **트랜스폼 규격**:
  - 가구 3종(`furn_*`): 분양지 바닥 `ItemDisplay` 배치 및 45도 회전용. 모델 밑면이 정확히 `Y = 0`에 밀착되도록 설계.
  - 모자 2종(`hat_*`): `Equippable(HEAD)` 슬롯 착용용. 기존 `hat_yuumi_ears.json`의 `display.head` 스케일·높이 오프셋을 기준 삼아 플레이어 머리에 자연스럽게 밀착되도록 보정.

| 모델 ID (`<id>`) | 분류 | 3D 복셀 조형 및 컬러 팔레트 지침 |
| :--- | :--- | :--- |
| **`furn_cat_tower`** | 3D 하우징 가구 | 파스텔 핑크 쿠션과 원목·마끈 스크래처 기둥으로 구성된 3단 캣타워. 1층 고양이 얼굴 입구 숨숨집, 2층 라운드 발판, 3층 왕관 전망대와 아래로 매달린 황금 방울 장난감 |
| **`furn_kotatsu`** | 3D 하우징 가구 | 벚꽃 핑크·크림 체크무늬 누빔 이불이 네 면으로 덮인 정사각형 원목 코타츠 테이블. 상판 위에 주황색 귤 바구니와 녹차 찻잔 2개 배치 |
| **`furn_neon_sign`** | 3D 하우징 가구 | 다크 슬레이트 프레임 보드 전면에 핫핑크·시안·골드 발광 복셀 라인으로 윙크하는 고양이 실루엣과 `97` 네온 튜브가 입체 돌출된 사인보드 |
| **`hat_chef_toque`** | 착용형 3D 모자 | 볼륨감 있는 순백색 주름 셰프 모자(Toque). 모자 양옆으로 분홍 속귀가 보이는 고양이 귀가 솟아 있고, 정면 붉은 리본 밴드 중앙에 황금 생선 엠블럼 장식 |
| **`hat_stock_tophat`** | 착용형 3D 모자 | 카지노 & 츄르 증권 거래소 거물용 칠흑색 실크 탑햇. 골드 리본 밴드에 황금 코인과 에메랄드 상승 화살표 장식이 꽂혀 있고 금테 모노클 체인 포인트가 달린 모자 |

---

### C. 10대 성향 직업 전용 3D 입체 성물 무기 · 도구 `10종` (`64×64` UV 아틀라스 + GUI 2D 분기 필수!)
- **신규 핵심 모듈 (`sources/class-weapons.mjs` 권장)**: 플레이어가 선택한 직업 성향의 정체성을 인게임 1인칭·3인칭 손에서 즉시 체감할 수 있도록 제작하는 **10종의 입체 복셀 성물 무기/도구**입니다.
- **출력 구성 (각 `<id>` 마다 4파일 세트)**:
  1. `overrides/assets/yuumi97/models/item/<id>.json` (입체 3D 모델)
  2. `overrides/assets/yuumi97/textures/prop/<id>.png` (`64×64` RGBA8 UV 아틀라스 — `blocks.json` 등록 필수!)
  3. `overrides/assets/yuumi97/textures/item/<id>.png` (`32×32` 인벤토리 슬롯용 또렷한 2D 픽셀 도트 아이콘)
  4. `overrides/assets/yuumi97/models/gui/<id>.json` + `overrides/assets/yuumi97/items/<id>.json` (`display_context=gui` 일 때는 2D 도트 아이콘, 손에 들거나 바닥/전시대에 둘 때는 3D 복셀 모델 출력!)
- **1인칭/3인칭 손 파지(`display`) 트랜스폼 필수 지침**:
  - **근접 무기·도구 7종 (`weapon_paladin_sword`, `weapon_shadow_daggers`, `tool_smith_hammer`, `tool_farmer_scythe`, `tool_angler_harpoon`, `tool_carpenter_axe`, `tool_chef_wok`)**: 기존 `prowling_wand.json`처럼 손잡이(그립) 위치가 캐릭터 주먹에 정확히 쥐어지고 날/헤드 부분이 전방 위쪽을 향하도록 `thirdperson_righthand`, `thirdperson_lefthand`, `firstperson_righthand`, `firstperson_lefthand` 회전·이동을 정밀 세팅하세요.
  - **마궁 (`weapon_ranger_bow`)**: 바닐라 `BOW` 아이템에 마운트되므로, 3인칭 손에서 활대 중앙 그립을 수직으로 쥐고 현(시위)이 몸 쪽을 향하며 1인칭 우측 화면에서 활의 곡선 실루엣이 멋지게 보이도록 활 전용 그립 각도로 세팅하세요.
  - **마도서 (`weapon_arcane_grimoire`)**: 기존 `book_of_thresholds.json`을 참고하되 더 화려한 마도서 실루엣과 떠다니는 작은 마법 룬/크리스탈 포인트를 주어 손 위에서 신비롭게 펼쳐진/들려진 형태로 세팅하세요.
  - **크러셔 드릴 (`tool_miner_drill`)**: 손목을 감싸는 황금·청록 건틀릿 하우징 앞으로 나선형 별빛 드릴 콘(Cone)이 뻗어 나가는 형태로 전방을 향하게 세팅하세요.

| 모델 ID (`<id>`) | 담당 성향 직업 | 3D 복셀 조형 및 컬러 팔레트 지침 |
| :--- | :--- | :--- |
| **`weapon_paladin_sword`** | `🛡 성기사` | 로열 블루 & 황금 고양이 날개 코등이(Crossguard)와 사파이어 코어가 박힌 묵직하고 품격 있는 성기사 브로드소드 |
| **`weapon_shadow_daggers`** | `🗡 어쌔신` | 보랏빛·자홍빛 초승달 곡선 칼날과 칠흑색 붕대 그립, 끝단에 금빛 방울 고리가 달린 날렵한 암살 단검 |
| **`weapon_ranger_bow`** | `🏹 레인저` | 백은색·황금 날개 모양 상하 림(Limb)과 청록빛 마력 시위, 중앙 별빛 조준 보석이 빛나는 하이판타지 마궁 |
| **`weapon_arcane_grimoire`** | `🪄 아카니스트` | 짙은 남보라 가죽 표지에 황금 고양이 성좌 장식과 책장 위로 떠오른 청록·자수정 비전 수정이 어우러진 대마도서 |
| **`tool_miner_drill`** | `⛏ 채광가` | 황동·다크스틸 기어 하우징과 빛나는 청록 별빛 나선형 드릴 헤드가 결합된 마공학 광맥 파쇄 드릴 |
| **`tool_smith_hammer`** | `⚒ 대장장이` | 흑철 손잡이 위에 용암빛 오렌지·골드 룬이 타오르는 양면 사각 단조 헤드와 고양이 모루 문양이 새겨진 대장장이 해머 |
| **`tool_farmer_scythe`** | `🌾 대지주` | 따뜻한 원목 장대 끝에 황금빛 반달 대낫 칼날과 초록 새싹·분홍 꽃잎 장식이 감겨 있는 풍요의 수확 대낫 |
| **`tool_angler_harpoon`** | `🎣 강태공` | 산호빛·황금 릴(Reel)이 달린 심해 청록 작살 낚싯대와 끝부분 삼지창/작살촉 및 진주 장식 |
| **`tool_carpenter_axe`** | `🪓 공방 목수` | 정교한 금장 결속 밴드와 에메랄드 영혼 나뭇잎 보석이 박힌 묵직한 양날/광폭 공방 벌목 도끼 |
| **`tool_chef_wok`** | `🍳 미식 셰프` | 붉은 매듭 손잡이가 달린 흑무쇠 중화팬(Wok) 안쪽에 노릇한 황금 볶음요리 불꽃·생선 토핑 포인트가 살아있는 셰프 성물 |

---

### D. 순수 2D `32×32` 게임플레이 · 분업 경제 · 유물 도트 아이콘 `15종` (`overrides/assets/yuumi97/textures/item/<id>.png`)
기존 `sources/icons.mjs` 카탈로그에 아래 **15종**(및 상기 B `5종` + C `10종`의 GUI용 32×32 도트 = 총 30종 신규 도트)을 추가하여 선명한 실루엣·외곽선·명암 대비를 갖춘 `32×32` RGBA8 PNG를 생성하세요.

#### [미궁 · 가챠 · 직업 공용 9종]
1. **`labyrinth_key`** — `문턱의 미궁 열쇠`: 고양이 귀 모양 손잡이와 청록 마법 수정이 박힌 황금 차원 열쇠
2. **`ancient_cat_coin`** — `고대 고양이 금화`: 미궁 유적에서 발굴된 고대 황금 주화 (중앙 고양이 왕관 양각)
3. **`gacha_capsule`** — `황금 냥냥 뽑기 캡슐`: 상단 투명 별빛 돔 + 하단 핑크·골드 반구 캡슐 볼
4. **`class_emblem`** — `집사 성향 문장`: 검·활·곡괭이·요리팬 상징과 별빛 보석이 조화된 황금 고양이 성향 엠블럼
5. **`respec_potion`** — `망각의 캣닢 물약`: 은하수 보랏빛·민트빛 영약이 담긴 고양이 귀 코르크 마개 유리병
6. **`charm_crimson_claw`** — `진홍 발톱 책갈피`: 루비 고양이 발톱 보석과 금실 태슬이 달린 공격형 마법 책갈피
7. **`charm_starlight_aegis`** — `별빛 방패 책갈피`: 사파이어 성기사 방패와 작은 날개가 조각된 수호형 마법 책갈피
8. **`charm_golden_whisker`** — `황금 수염 책갈피`: 황금 고양이 수염과 네잎클로버 에메랄드가 장식된 행운형 마법 책갈피
9. **`charm_vampiric_purr`** — `흡혈의 골골송 책갈피`: 자홍빛 박쥐 날개와 붉은 하트 크리스탈이 박힌 흡혈형 마법 책갈피

#### [성향별 효율 분업 원재료 · 명품 생산품 6종]
10. **`monster_soul_core`** — `마수 영혼석`: 검푸른 결정 핵 속에서 청록·보랏빛 마수 영혼 불꽃이 일렁이는 마력 보석
11. **`unidentified_geode`** — `미감정 별빛 지오드`: 거친 현무암 외피의 틈새로 황금·자수정 별빛 결정이 은은하게 새어 나오는 원석 괴
12. **`golden_catnip`** — `황금 캣닢`: 반짝이는 이슬이 맺힌 황금빛·연두빛 세 갈래 신비의 캣닢 허브 잎사귀 다발
13. **`deep_sea_fillet`** — `심해 대뱃살 필렛`: 영롱한 마블링 결이 살아있는 선홍빛·은빛 최상급 심해 참치 대뱃살 필렛
14. **`rune_scroll`** — `별빛 마법 룬 각인서`: 황금 고양이 인장으로 봉인된 고대 양피지 두루마리 위에 푸른 마법 룬 문자가 발광하는 주문서
15. **`michelin_feast`** — `특선 미슐랭 만찬`: 황금 돔 클로슈가 살짝 열린 은쟁반 위에 육즙 가득한 스테이크·해산물·별빛 가니시가 담긴 최고급 요리 접시

---

## 3. 아틀라스 등록 및 품질 검수 체크리스트

1. **`overrides/assets/minecraft/atlases/blocks.json` 필수 갱신 (`sources/item-definitions.mjs`)**:
   - `textures/prop/` 경로는 바닐라가 자동 스캔하지 않으므로, 기존 **9종** + 신규 환경 프롭 **5종**(`prop_gacha_machine`, `prop_stock_board`, `prop_dungeon_obelisk`, `prop_boon_altar`, `prop_feast_cauldron`) + 신규 가구/모자 **5종**(`furn_cat_tower`, `furn_kotatsu`, `furn_neon_sign`, `hat_chef_toque`, `hat_stock_tophat`) + 신규 직업 성물 무기/도구 **10종**(`weapon_paladin_sword`, `weapon_shadow_daggers`, `weapon_ranger_bow`, `weapon_arcane_grimoire`, `tool_miner_drill`, `tool_smith_hammer`, `tool_farmer_scythe`, `tool_angler_harpoon`, `tool_carpenter_axe`, `tool_chef_wok`) = **총 `29종` `yuumi97:prop/<id>`** 가 빠짐없이 `minecraft:single` 소스로 등록되어야 합니다!
2. **바닐라 `1.21.4` 모델 제약 자동 검증 (`tools/validate-assets.mjs` 확장)**:
   - 모든 cuboid `from`/`to` 좌표 `[-16, -16, -16]` ~ `[32, 32, 32]` 범위 준수.
   - `rotation.angle` ∈ `{-45, -22.5, 0, 22.5, 45}`, 단일 축 회전 준수.
   - 모든 면 UV `[0, 0, 16, 16]` 범위 및 비퇴화(non-degenerate) 면적 검증, `"particle"` 텍스처 지정 확인.
   - GUI 2D 분기가 필요한 신규 `15종`(가구 3 + 모자 2 + 직업 성물 10) 모두 `items/<id>.json` (`minecraft:display_context=gui` → `yuumi97:gui/<id>`, fallback → `yuumi97:item/<id>`) 및 `models/gui/<id>.json`이 정상 생성되는지 자동 검증.
   - `regenerate.ps1`에 신규 생성 모듈이 모두 연결되어 단일 명령(`regenerate.ps1` — `-Build` 제외!)으로 재생성 및 검증이 통과되는지 확인.
3. **시각 검수 (`review/`)**:
   - 설치된 Blender를 활용해 `tools/render-models.py`로 신규 20종 3D 모델의 다각도 렌더 시트를 생성하고(GPU 렌더는 직렬 실행), `review/icons` 보드에서 신규 30종 도트의 32px/16px 축소 가독성을 검수한 뒤 작업을 완료하세요.
