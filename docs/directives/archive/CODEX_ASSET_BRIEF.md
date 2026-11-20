# 🎨 `유미의 97랜드 v2.0` — Codex 전용 3D 모델링 & 고해상도 픽셀아트 에셋 제작 명세서

이 문서는 **OpenAI Codex** (또는 에셋 전담 AI 에이전트)가 **Java 플러그인 코드를 단 한 줄도 건드리지 않고** 즉시 인게임 3D 오브젝트·3D 무기 유물·고디테일 도트 아이콘을 제작 및 반영할 수 있도록 설계된 **전용 작업 지시서(Handoff Brief)** 입니다.

---

## 1. 자동 빌드 & 오버라이드 파이프라인 구조 (Zero-Java-Touch)

`e:\MinGem\resourcepack-gen\PackBuilder.java`와 `HubWorldManager.java`에 이미 **자동 오버라이드 및 3D 프롭 마운트 파이프라인**이 구축되어 있습니다.

1. **작업 디렉토리**: `e:\MinGem\resourcepack-gen\overrides\`
2. **규칙**: Minecraft `1.21.4` (`pack_format: 46`) 리소스팩 경로 구조 그대로 파일을 생성합니다.
   ```text
   e:/MinGem/resourcepack-gen/overrides/
   └── assets/
       └── yuumi97/
           ├── textures/
           │   ├── item/           # 16x16 또는 32x32 아이템 픽셀아트 PNG
           │   └── prop/           # 3D 복셀 오브젝트용 UV 아틀라스 PNG
           ├── models/
           │   └── item/           # 3D JSON 모델 (elements, textures, display 포함)
           └── items/              # (생략 가능!) models/item/<id>.json 생성 시 PackBuilder가 1.21.4 규격 items/<id>.json 자동 생성!
   ```
3. **원클릭 패키징 및 SHA-1 갱신 명령어**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File e:\MinGem\scripts\build-all.ps1
   ```
   - 실행 즉시 `server/resourcepack/Yuumi97Pack.zip` 및 `Yuumi97Pack.sha1`에 커스텀 에셋이 병합됩니다.
   - 인게임 OP 명령어 `/메뉴 props` (또는 서버 재시작) 시 `yuumi_hub` 월드 내 지정 좌표에 3D `ItemDisplay` 프롭들이 **자동 스폰**됩니다!

---

## 2. Codex 우선 제작 과제 리스트 (Priority Backlog)

### 🏆 Priority 1: 허브 도시 랜드마크 3D 오브젝트 4종 (`HubWorldManager` 자동 마운트 연동 완료!)
아래 **정확한 파일명(`id`)** 으로 `overrides/assets/yuumi97/models/item/<id>.json` 과 텍스처 PNG를 생성하면, 허브 도시(`yuumi_hub`) 해당 시설에 자동으로 3D 모델이 배치됩니다.

| 모델 ID (`models/item/<id>.json`) | 배치 위치 (`yuumi_hub` 자동 연동됨) | 3D 복셀 모델링 디자인 가이드 |
| :--- | :--- | :--- |
| **`prop_yuumi_statue`** | 중앙 광장 황금 분수대 정상 `(0.5, 70.6, 0.5)` (`scale 2.0`) | 펼쳐진 마법서 『문턱의 서』 위에 앉아 이마의 황금 방울 더듬이가 빛나는 마법 고양이 유미 황금/파스텔 조각상 |
| **`prop_slot_cabinet`** | 황금 냥냥 카지노 1F(4대) & 2F VIP(3대) 슬롯 부스 (`scale 1.35`) | 상단에 네온 고양이 귀 장식이 달린 아케이드 3릴 슬롯머신 캐비닛 (정면 릴 창 + 우측 당김 레버 디테일) |
| **`prop_roulette_wheel`** | 카지노 1F 동측 룰렛 테이블 중앙 `(79.5, 66.15, -22.5)` (`scale 1.25`) | 마호가니 원목 테두리 + 빨강/검정/황금유미(초록) 포켓과 중앙 황금 십자 스피너가 입체로 올라온 3D 룰렛 휠 |
| **`prop_derby_trophy`** | 카지노 1F 실시간 고양이 경주 결승선 `(72.5, 66.2, -22.0)` (`scale 1.5`) | 대리석 받침대 위에 질주하는 황금 고양이와 월계관이 조각된 『제97회 경묘 챔피언 트로피』 |

---

### ⚔ Priority 2: 손에 드는 유미 테마 3D 입체 유물 & 치장 모자 5종
기존 2D 평면 모델을 **3D 복셀 JSON 모델(`elements` + 1인칭/3인칭/GUI `display` 트랜스폼)** 로 업그레이드하거나 치장 아이템을 추가합니다.

| 모델 ID (`models/item/<id>.json`) | 구분 | 3D 복셀 모델링 디자인 가이드 |
| :--- | :--- | :--- |
| **`prowling_wand`** | 기존 유물 3D화 | 『사르르 유성 마법봉』 — 핑크·민트 나선 손잡이 끝에 입체 분홍 젤리 고양이 발바닥과 황금 별 코어가 떠 있는 마법 스태프 |
| **`book_of_thresholds`** | 기존 유물 3D화 | 『유미의 문턱의 서』 — 금장 모서리와 청록 보석 표지, 살짝 벌어진 페이지 사이에 마법 룬이 빛나는 3D 고서 |
| **`you_and_me_bell`** | 기존 유물 3D화 | 『너랑 유미랑! 단짝 방울』 — 분홍 리본 매듭 아래 입체 구형 황금 방울이 달린 부적 |
| **`hat_yuumi_ears`** | 신규 치장 모자 (`/메뉴 give hat_yuumi_ears`) | 머리 슬롯(`head` display 트랜스폼 필수!) 착용 시 플레이어 머리 위로 쫑긋 솟아오르는 유미 귀 + 황금 방울 머리띠 |
| **`plushie_yuumi`** | 신규 하우징 소품 (`/메뉴 give plushie_yuumi`) | 남쪽 분양 부지 내 아이템 액자/진열대에 놓을 수 있는 귀여운 식빵 자세 유미 봉제인형 (`fixed` & `ground` 트랜스폼 최적화) |

---

### 🍱 Priority 3: 27종 커스텀 아이템 픽셀아트 고도화 (16×16 또는 32×32 PNG)
현재 `PackBuilder.java`가 생성하는 기본 16×16 도트 위에 덮어씌울 고퀄리티 명암(Shading)·외곽선(Sel-out)·하이라이트 PNG 텍스처입니다.
`overrides/assets/yuumi97/textures/item/<id>.png` 경로에 저장하면 즉시 교체됩니다.

- **화폐 & 카지노 칩 (6종)**: `churu_salmon` (100츄르), `churu_tuna` (1,000츄르), `churu_golden` (10,000츄르), `chip_100` (레드/화이트 칩), `chip_1000` (사파이어/골드 칩), `chip_10000` (로얄 퍼플/다이아 칩)
- **성장 & 이동 소모품 (4종)**: `torn_page` (찢어진 마법 책장), `recall_bookmark` (차원 귀환 책갈피), `starlight_stone` (별빛 세공석), `starlight_scroll` (유미의 세공 보호권)
- **유미 특선 요리 (4종)**: `zoomies_skewer` (생선 꼬치구이), `kneading_bento` (고양이 어묵 도시락), `catnip_elixir` (별빛 캣닢 영약병), `royal_feast_can` (97산 황실 참치캔)
- **보스 소환 코어 & 유물 (5종)**: `boss_core_rat`, `boss_core_hound`, `boss_core_void`, `final_chapter_tome`, `nine_lives_charm`

---

## 3. Minecraft `1.21.4` 3D JSON 모델 작성 엄수 규격 (Codex 검증 체크리스트)

1. **좌표 및 회전 제약 (Vanilla Block/Item Model Engine)**:
   - 모든 `elements`의 `from` / `to` 좌표는 반드시 **`[-16, -16, -16]` ~ `[32, 32, 32]`** 범위 이내여야 합니다 (초과 시 클라이언트 모델 로더가 보라/검정 체커보드를 출력함!).
   - `rotation.angle`은 바닐라 규격인 **`-45`, `-22.5`, `0`, `22.5`, `45`** 값만 허용되며, 요소당 단일 축(`"axis": "x" | "y" | "z"`) 회전만 가능합니다.
2. **텍스처 참조 및 UV**:
   - JSON 내부 `"textures"` 맵에는 반드시 `"particle"` 키를 포함하고, 네임스페이스는 `"yuumi97:prop/<파일명>"` 또는 `"yuumi97:item/<파일명>"` 형식을 사용합니다.
   - 모든 면(`north`, `south`, `east`, `west`, `up`, `down`)의 `"uv"` 값은 **`[0, 0, 16, 16]`** 범위 안에 있어야 합니다.
   - PNG 생성 시 안티앨리어싱 블러 없이 선명한 픽셀(Nearest-Neighbor, ARGB 8-bit)로 렌더링하세요 (`runtime\jdk-21\bin\java.exe`로 간단한 PNG 생성 스크립트를 실행해 그리는 방식 적극 권장!).
3. **Display 트랜스폼 필수 포함**:
   - `gui`, `ground`, `fixed`, `thirdperson_righthand`, `firstperson_righthand`, `head` 트랜스폼을 지정해 인벤토리 아이콘과 `ItemDisplay` 프롭 양쪽에서 자연스러운 크기·각도로 보이게 설정하세요.
4. **완료 후 검증**:
   - `powershell -ExecutionPolicy Bypass -File e:\MinGem\scripts\build-all.ps1` 실행 시 `[PackBuilder] Built 27 baseline sprites + N custom overrides` 로그가 출력되고 Exit Code `0`인지 확인합니다.
