# 🎨 [GPT 6 Astra 전용 작업 지시서 — v3.1] 광장 & 초호화 카지노 3D 랜드마크 조형물 6종 제작

당신은 **`GPT 6 Astra`** 이며, `유미의 97랜드 v3.1` 비주얼 업그레이드의 **초대형 3D 건축 랜드마크 & 카지노 인테리어 복셀 조형물 전담 아티스트/엔지니어**입니다.
동시에 다른 세션에서 **`GPT 6.1 SOL`** 이 허브 도시 스카이라인 리모델링(`HubWorldManager.java`), 카지노 딜러 단상 개선, 서버 실행 스크립트 인코딩 패치를 병렬 진행하므로 아래 **파일 경계 규칙**과 **확정 에셋 ID 계약**을 100% 준수하세요.

---

## 0. 동시 병렬 작업 절대 규칙 (Zero-Collision Rules)

1. **쓰기 허용 경로 (Write Scope)**:
   - `E:\MinGem\assets\yuumi97-production\**` (신규 생성 모듈 예: `sources/landmarks-v31.mjs`, `item-definitions.mjs`, `validate-assets.mjs`, `regenerate.ps1`, `review/` 등)
   - `E:\MinGem\resourcepack-gen\overrides\**` (최종 납품 3D JSON 모델, `textures/prop/*.png`, `atlases/blocks.json`)
2. **수정 절대 금지 경로 (Forbidden Scope)**:
   - `E:\MinGem\yuumi-core\**`, `E:\MinGem\resourcepack-gen\PackBuilder.java`, `E:\MinGem\START_SERVER.bat`, `E:\MinGem\scripts\**`, `E:\MinGem\server\**` (`GPT 6.1 SOL` 전담!)
3. **병렬 검증 명령어 주의**:
   - 재생성 및 정적 검증 시 **반드시 `-Build` 플래그 없이** 실행하여 Gradle Java 빌드를 건드리지 마세요:
     ```powershell
     powershell -NoProfile -ExecutionPolicy Bypass -File E:\MinGem\assets\yuumi97-production\regenerate.ps1
     ```

---

## 1. v3.1 목표 및 기존 에셋 보존 원칙

- 기존 v2.0 + v3.0의 **29종 3D 모델**과 **57종 32×32 아이콘**은 단 하나도 누락·파손되지 않도록 100% 보존합니다.
- 여기에 바닐라 1m 블록만으로는 표현하기 어려운 **초호화 라스베이거스 카지노 간판·샹들리에·딜러 왕좌 및 중앙 광장 시계탑·대문·수련 허수아비 3D 복셀 조형물 `6종`** 을 추가합니다 (`blocks.json` 총 **`29 + 6 = 35종`**).
- 6종 모두 월드 내 `ItemDisplay` (`Display.Billboard.FIXED`, `scale = 1.35f ~ 2.8f`)로 마운트되는 환경/건축 조형물이므로 별도 `gui` 2D 아이콘은 불필요하며, 각 `<id>`에 대해:
  1. `overrides/assets/yuumi97/models/item/<id>.json`
  2. `overrides/assets/yuumi97/textures/prop/<id>.png` (`128×128` RGBA8 고밀도 아틀라스 권장, `prop_training_dummy`는 `64×64` 또는 `128×128`)
  3. `overrides/assets/yuumi97/items/<id>.json` (`{"model":{"type":"minecraft:model","model":"yuumi97:item/<id>"}}`)
  4. `overrides/assets/minecraft/atlases/blocks.json` 내 `"yuumi97:prop/<id>"` 등록
  을 생성합니다.

---

## 2. v3.1 확정 3D 랜드마크 모델 6종 명세 (Locked Contract with `GPT 6.1 SOL`)

> ⚠️ 아래 6개 `<id>` 문자열과 **원점/정면 방향(`-Z` 북쪽 또는 `+Z` 남쪽 일관성 — 기존 `prop_slot_cabinet` / `prop_yuumi_statue`와 동일한 정면 기준 유지!)** 은 `GPT 6.1 SOL`의 `HubWorldManager` 마운트 코드와 직결됩니다. 특히 벽면 부착형(`prop_casino_marquee`, `prop_plaza_clock_face`)은 중심이 `[8, 8, 8]` 근처에 오고 정면 디테일이 선명하게 돌출되도록 설계하세요!

| 번호 | 모델 ID (`<id>`) | 마운트 위치 및 스케일 | 3D 복셀 조형 · 실루엣 · 컬러 팔레트 지침 |
| :--- | :--- | :--- | :--- |
| **1** | **`prop_casino_marquee`** | 카지노 서측(광장 정면) & 남측 포르티코 박공 상단 (`scale ~2.7f`) | **라스베이거스 궁전급 초대형 `777` 황금 고양이 네온 마키 크레스트**. 진홍색 벨벳 & 찬란한 골드 아치형 프레임 상단에 황금 왕관을 쓴 윙크하는 고양이 얼굴 부조가 솟아 있고, 중앙 메인 보드에 발광 시안/옐로우/핫핑크 복셀로 빚은 입체 **`7 7 7`** 숫자가 강렬하게 빛남. 양옆에는 비스듬히 기울어진 3D 황금 주사위, 스페이드/하트 에이스 카드, 반짝이는 골드 코인 다발 장식을 풍성하게 배치 |
| **2** | **`prop_casino_chandelier`** | 카지노 중앙 아트리움 천장 (`scale ~2.3f`, 천장 매달림형) | **3단 임페리얼 크리스탈 & 황금 고양이 샹들리에**. 상단 황금 체인 마운트 아래로 크기가 다른 3겹의 황금 링 프레임(고양이 귀·발바닥 세공 포인트)이 층층이 내려오며, 링 둘레에 영롱한 시안·자수정 크리스탈 드롭과 따뜻한 크림빛 촛대 불꽃이 촘촘히 매달린 호화 샹들리에 (모델 상단이 `[8, 16..24, 8]` 근처 행거, 하단 크리스탈 첨단이 `[8, -4..0, 8]`로 우아하게 늘어지도록 조형) |
| **3** | **`prop_dealer_throne`** | 카지노 1F/2F 모든 딜러·캐셔·중개묘 단상 바로 뒤편 (`scale ~1.5f`) | **VIP 딜러 전용 하이백 크림슨 벨벳 왕좌 & 골드 네온 백드롭 아치**. 단상 위에 앉은 고양이 딜러가 멀리서도 단번에 눈에 띄도록 뒤를 감싸주는 화려한 등받이 구조물. 진홍색 퀼팅 벨벳 백레스트, 좌우 황금 기둥 위 고양이 발바닥 황금 보주, 상단 아치의 에메랄드·골드 포인트 조명이 어우러져 딜러 고양이를 주인공처럼 부각시킴 (바닥 `Y=0` 안착) |
| **4** | **`prop_plaza_clock_face`** | 중앙 광장 북서측 신규 `24m 황금 시계탑` 외벽 4면 (`scale ~2.0f`) | **천문 마법풍 황금 고양이 대형 탑시계 다이얼**. 짙은 남색·화이트 자개 문자판 테두리를 정교한 골드 베젤과 상단 고양이 귀 왕관 장식이 감싸고, 12방향 에메랄드/골드 시각 보석과 중앙 별빛 기어 코어에서 뻗어 나온 황금 시침·분침이 입체적으로 돌출된 벽걸이형 원형/팔각 시계판 |
| **5** | **`prop_training_dummy`** | 중앙 광장 북서측 전직 성소 옆 `실시간 DPS 수련장` (`scale ~1.35f`) | **별빛 밀짚 고양이 전투 수련 허수아비**. 튼튼한 원목 십자 받침대와 스프링 기둥 위에 세워진 귀여운 고양이 귀 밀짚 인형. 이마에는 붉은 수련 머리띠를 매고, 몸통 정면에는 선명한 적백 과녁판(Bullseye)이 그려진 나무 갑옷, 한쪽 팔에는 작은 연습용 목검이나 둥근 방패가 달린 생동감 넘치는 RPG 수련 더미 (바닥 `Y=0` 안착) |
| **6** | **`prop_sakura_lantern_arch`** | 중앙 광장 동·서·남·북 4대 환영 대문 상단 중앙 (`scale ~2.0f`) | **97랜드 벚꽃 축제 대문 현판 & 대형 황금 제등(초롱) 크레스트**. 붉은 주칠 목조·황금 장식 현판 중앙에 포근한 주황빛이 감도는 둥근 대형 축제 제등(고양이 발도장 문양)이 걸려 있고, 좌우로 만개한 파스텔 핑크 벚꽃 가지와 금빛 리본 태슬이 우아하게 뻗어 나온 대문 장식물 |

---

## 3. 정적 검증 및 Blender 시각 검수 체크리스트

1. **바닐라 `1.21.4` 모델 규격 준수 (`tools/validate-assets.mjs`)**:
   - 모든 cuboid 좌표 `[-16, -16, -16]` ~ `[32, 32, 32]` 준수 (대형 스케일은 Java `ItemDisplay` `Transformation` 스케일로 확대되므로 모델 자체는 `[-16..32]` 한계 내에서 최대한 꽉 차고 밀도 높게 조형하세요!).
   - 허용 회전각 `{-45, -22.5, 0, 22.5, 45}` 단일 축 회전, 비퇴화 UV, `"particle"` 지정 준수.
   - `overrides/assets/minecraft/atlases/blocks.json`에 총 **35개** `minecraft:single` 엔트리가 빠짐없이 들어갔는지 자동 검증.
2. **Blender 다각도 렌더 검수 (`review/landmarks-v31.png` 생성)**:
   - `tools/render-models.py`를 통해 신규 6종 모델의 정면·측면·3/4 쿼터뷰 컨택트 시트(`review/landmarks-v31.png`)를 생성하고, 특히 `prop_casino_marquee`의 **`777` 숫자 가독성**과 `prop_plaza_clock_face`의 시계 바늘 대비가 멀리서도 또렷한지 직접 확인한 뒤 작업을 마쳐주세요.
