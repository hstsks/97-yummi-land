# 📐 유미의 97랜드 — 프론티어 AI 디렉팅 & 작업 지시서 허브 (`docs/directives/`)

본 디렉터리는 **총괄 기획 및 아트·아키텍처 디렉터(`Gemini Carbon`)** 가 설계한 심층 아트·건축·시스템 명세서를 관리하며, **`GPT Astra`**, **`Gemini Carbon`**, **`GPT SOL`** 등 프론티어 모델이 **파일 충돌 0%(`Zero-Collision`)** 로 최고 품질의 결과물을 병렬 제작할 수 있도록 구성되어 있습니다.

---

## 1. 개발 철학 및 공통 준수 원칙

1. **프리 알파 클린 아키텍처 (No Legacy Bloat)**:
   - 본 서버는 정식 배포 및 베타 테스트 이전의 순수 개발 단계입니다.
   - `v2`, `v3`, `v3.1` 같은 버전 접미사 파일/클래스나 하위 호환용 임시 플래그(`starter_kit_v2`, `hub_v3_1.built` 등)를 만들지 마세요. 불필요해진 구버전 코드는 과감히 교체·정리합니다.
2. **작업 경계 엄수 (Zero-Collision Parallel Scopes)**:
   - 각 지시서 상단에 명시된 **`쓰기 허용 경로(Write Scope)`** 만 수정하세요.
   - 병렬 작업 중에는 통합 Gradle/리소스팩 빌드(`scripts/build-all.ps1`)를 임의로 실행해 타 세션의 산출물을 덮어쓰지 마세요. 통합 빌드는 양측 납품 완료 후 총괄 검수 단계에서 1회 수행합니다.

---

## 2. 현행 활성 작업 지시서 (`active/`)

| 스트림 | 지시서 파일 | 권장 담당 모델 | 독점 쓰기 범위 (Write Scope) | 핵심 납품 범위 |
| :--- | :--- | :--- | :--- | :--- |
| **Stream 01**<br>*(3D 모델링 · VFX · 몬스터)* | **[01_ASTRA_WEAPONS_VFX_AND_MONSTERS.md](./active/01_ASTRA_WEAPONS_VFX_AND_MONSTERS.md)** | `GPT Astra`<br>*(또는 `Gemini Carbon`)* | `assets/yuumi97-production/**`<br>`resourcepack-gen/overrides/**` | • 신규 성물 3종 + 만능 가이드북 + 3D 길잡이 화살표 (`5종`)<br>• 커스텀 3D 스킬 이펙트 메쉬 (`14종`)<br>• 18종 자체 제작 던전 몬스터·보스 다관절 3D 파츠 (`42종` 본 모델)<br>• Blender 5.2 다각도 시각 검수 시트 |
| **Stream 02**<br>*(심리스 메가던전 & 허브 건축)* | **[02_CARBON_ASTRA_ARCHITECTURE_NBT.md](./active/02_CARBON_ASTRA_ARCHITECTURE_NBT.md)** | `Gemini Carbon`<br>*(또는 `GPT Astra`)* | `assets/architecture-blueprints/**`<br>`yuumi-core/src/main/resources/structures/overhaul/**` | • 허브 『🏰 문턱의 만능 길드 대공방』 (`29×26×25`) & 『📖 광장 가이드북 키오스크』 `.nbt`<br>• 3대 심리스 연결형 중·대형 테마 던전 (`별빛 천문 대서고` · `홍련의 거신 용광로` · `공허의 수정 대성당`) 18개 직결 모듈 `.nbt` & 단면 검증 시트 |
| **Stream 03**<br>*(코어 엔진 · 듀얼 직업 · AI · QoL)* | **[03_SOL_CORE_SYSTEMS_OVERHAUL.md](./active/03_SOL_CORE_SYSTEMS_OVERHAUL.md)** | `GPT SOL` | `yuumi-core/src/main/java/**`<br>`yuumi-core/src/main/resources/*.yml`<br>`resourcepack-gen/PackBuilder.java`<br>`scripts/**`, `START_SERVER.bat` | • `[전투 6 + 생활 6]` 듀얼 직업군, 표식 3박자 연계, 마일스톤 진화, 특전 각인, 성물·도구 별빛 세공<br>• 숏보우/원터치 스킬(`좌/우/F`) & 3D VFX 키프레임 렌더러<br>• 다관절 커스텀 몬스터 AI & 심리스 던전 오케스트레이터<br>• 허브 전 시설/내 부지 0초 워프, 3D 길잡이, 만능 가이드북 & 레거시 정화 |

---

## 3. 비주얼 디렉팅 컨셉 보드 (`concepts/`)

- **[01_relics_and_3d_vfx_concept.png](./concepts/01_relics_and_3d_vfx_concept.png)**: 신규 영웅 성물 3종(`홍련의 대검`, `월광의 성배 지팡이`, `아스트랄 성운 보주서`) 및 3D 복셀 스킬 이펙트 메쉬 컬러/조형 기준표
- **[03_seamless_megadungeons_concept.png](./concepts/03_seamless_megadungeons_concept.png)**: 3대 심리스 중·대형 테마 던전(`별빛 천문 대서고`, `홍련의 거신 용광로 성채`, `공허의 수정 대성당`) 아이소메트릭 건축 단면 및 조명 무드 보드

---

## 4. 완료 아카이브 (`archive/`)
이전 세대 지시서(`CODEX_ASSET_BRIEF.md`, `DIRECTIVE_GPT6_*`, `DIRECTIVE_GPT61_*`)는 히스토리 참고용으로만 `archive/`에 보관되어 있으며, 현행 구현 시에는 반드시 `active/` 문서를 단일 진실 공급원(Single Source of Truth)으로 삼으세요.
