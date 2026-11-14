# 🐱 유미의 97랜드 v2.0 (Yuumi's 97Land) — AI Agent & Developer Architecture Guide

이 문서는 **Gemini, OpenAI Codex, Claude, GPT** 등 프론티어 AI 에이전트가 본 워크스페이스(`e:/MinGem`)에서 추가 시스템 개발·3D 모델링/에셋 확장·차기 시즌 패치를 즉시 수행할 수 있도록 작성된 v2.0 기술 명세서입니다.

> 🎨 **3D 모델링 및 도트 에셋 전담 작업 시**: 루트 디렉토리의 **[`CODEX_ASSET_BRIEF.md`](./CODEX_ASSET_BRIEF.md)** 를 먼저 확인하세요! Java 코드를 수정하지 않고 `resourcepack-gen/overrides/` 폴더에 3D JSON 모델과 PNG만 추가하면 리소스팩 및 허브 `ItemDisplay` 프롭이 자동 반영됩니다.

---

## 1. 프로젝트 개요 및 핵심 설계 철학 (v2.0)
- **서버 명칭 / 세계관**: `유미의 97랜드 v2.0` (LoL 마법 고양이 '유미' & '문턱의 서' 세계관 + 전용 평화 허브 도시 `yuumi_hub`)
- **화폐 및 재화 체계**:
  - `츄르` (Vault 연동 지갑 잔액 + 은행 안전 예금 계좌 + 실물 츄르 스틱 수표 `100 / 1,000 / 10,000`)
  - `카지노 실물 칩` (`chip_100`, `chip_1000`, `chip_10000` — 카지노 입구 환전소에서 1:1 환전)
- **현장 방문 중심 동선 (Physical Visit Philosophy)**:
  - 원격 명령어(`/카지노`, `/제단`, `/의뢰`, `/도감`, `/분양`, `/레이드`)나 `/spawn`, `/home`, `/tpa` 워프를 폐지하고, 모든 명령어는 **나침반 길찾기 안내**만 수행합니다.
  - 상점·은행·대장간·카지노·제단·회관은 `yuumi_hub`의 실제 건축물과 고양이 NPC를 방문해 이용하며, 원정 복귀는 **`문턱의 서 Shift+우클릭` (3초 정신집중)** 또는 **`귀환 책갈피` (1초 고속 귀환)** 로 수행합니다.
- **구동 코어 / 포터블 런타임**: `Paper 1.21.4` (`server/paper.jar`), **포터블 Java 21** (`runtime/jdk-21`), **포터블 Gradle 8.12** (`runtime/gradle`)
  - ⚠️ 시스템 전역 `java`나 `gradle`에 의존하지 말고 반드시 `scripts/build-all.ps1`을 통해 빌드하세요.

---

## 2. 원클릭 빌드 및 배포 명령어
코드, 도트 스프라이트, 또는 `resourcepack-gen/overrides/` 내 3D 모델을 수정한 뒤 아래 명령 한 줄로 **리소스팩 생성(SHA-1 자동 갱신) + 플러그인 컴파일 + `server/plugins/YuumiLandCore.jar` 배포**가 완료됩니다.

```powershell
powershell -ExecutionPolicy Bypass -File e:\MinGem\scripts\build-all.ps1
```

---

## 3. 디렉토리 및 모듈 구조 (v2.0)

```text
e:/MinGem/
├── START_SERVER.bat                  # 호스트 더블클릭 실행기 (UTF-8 콘솔 + 8GB G1GC + 종료 시 자동 백업)
├── CODEX_ASSET_BRIEF.md              # Codex 전담 3D 복셀 모델링 & 픽셀아트 에셋 제작 명세서
├── runtime/                          # 포터블 Temurin JDK 21 & Gradle 8.12
├── resourcepack-gen/
│   ├── PackBuilder.java              # 27종 베이스 도트 + overrides/ 3D 모델 자동 병합 리소스팩 빌더(pack_format 46)
│   └── overrides/                    # 커스텀 3D JSON 모델 & 고디테일 PNG 오버라이드 폴더
├── yuumi-core/                       # 자체 개발 올인원 코어 플러그인 v2.0 (kr.yuumi97.core)
│   ├── build.gradle.kts              # Paper API 1.21.4 + VaultUnlocked / AuraSkills / WorldEdit 참조
│   └── src/main/
│       ├── resources/
│       │   ├── plugin.yml            # 차원 수첩(/메뉴) 및 길찾기 명령어 등록
│       │   ├── config.yml            # 3~4주 시즌 밸런스·분양가·사망 구조비 설정
│       │   └── structures/*.nbt      # 정제 내장된 16종 오픈소스 건축물 템플릿 (은행·대장간·회관·선술집·수레 등)
│       └── java/kr/yuumi97/core/
│           ├── YuumiPlugin.java                  # 메인 엔트리, 21종 허브 NPC 라우터, 차원 안내 수첩 GUI, 액션바 HUD
│           ├── hub/HubWorldManager.java          # 전용 평화 허브 월드(yuumi_hub) 생성기, 5대 구역 & .nbt 스탬퍼, 3D 프롭 마운터
│           ├── plot/PlotManager.java             # 남쪽 벚꽃 대교 너머 12필지(소6·중4·대2) 분양·공동거주·이사 환급 시스템
│           ├── portal/PortalAndRecallManager.java # 북쪽 4대 차원 대포탈 성소 점화, 야생 지옥문 차단, 3초/1초 귀환 채널링
│           ├── economy/ChuruManager.java         # Vault 지갑 브릿지 + 은행 예금 계좌 + 실물 츄르 수표 발행
│           ├── economy/SkillBonusBridge.java     # AuraSkills 숙련 레벨 + 수집 도감 달성률 상점 매입가 보너스 브릿지
│           ├── shop/NpcShopManager.java          # 서쪽 5대 전문 NPC 상점 (1:3.5 매입/판매 스프레드 & 특수 어종 감정)
│           ├── trade/DirectTradeManager.java     # 광장 [Shift+우클릭] 1:1 대면 안전 직거래 GUI (아이템+츄르+3초 잠금)
│           ├── facility/BankAndVaultManager.java # 츄르 중앙 은행 창구 & 최대 4페이지(108칸) 확장형 개인 금고
│           ├── facility/BlacksmithManager.java   # 장비 수리 & 파괴 0% 『★1~★7 별빛 세공』 강화 시스템
│           ├── facility/OnsenAndClinicManager.java # 15초 노천 온천 출격 버프, 특선 식당, 인벤보존 응급 치유소 후송
│           ├── casino/CasinoManager.java         # 동쪽 2층 황금 냥냥 카지노 홀 (환전소, 1F/2F 물리 슬롯·블랙잭·룰렛·주사위)
│           ├── casino/CatRaceManager.java        # 카지노 1층 아트리움 실시간 5레인 멀티플레이 고양이 달리기 경주 (경묘!)
│           ├── altar/ThresholdAltarManager.java  # 문턱의 서 제단 (제2장 네더 / 제3장 엔드 공동 해금)
│           ├── quest/QuestAndCodexManager.java   # 일일 3종 + 주간 2종 납품 의뢰 & 21종 수집 도감 박물관
│           ├── boss/RaidBossManager.java         # 북쪽 결투장 1~3티어 멀티페이즈 레이드 보스 & 세공석/유물 보상
│           ├── items/YuumiItems.java             # 27종 커스텀 아이템 생성(1.21.4 setItemModel) 및 조합법
│           ├── items/RelicAndFoodListener.java   # 특제 요리, 단짝 방울 오라, 마법봉, 비전서, 귀환 트리거
│           └── pack/ResourcePackServer.java      # 내장 HTTP 서버(TCP 25566) & 접속 호스트 자동 분기 전송
├── server/                           # Paper 1.21.4 본체, 5종 월드 데이터팩, 15종 생태계 플러그인, 월드 데이터
├── client-modpack/                   # 지인 배포용 Yuumi97Land-Client-1.21.4.mrpack 및 빌더
└── scripts/                          # bootstrap, download-ecosystem, build-all, verify-boot, start-server, backup-world
```

---

## 4. `yuumi_hub` 허브 도시 좌표 규약 (`GROUND_Y = 64`)
- **중앙 분수 광장 스폰**: `(0.5, 65.0, 12.5)` (반경 `26` 원형 광장)
- **북쪽 문턱의 서 성소 (`Z = -46 .. -76`, `Y = 66`)**: 제단 수호묘 `(0.5, 66.0, -53.5)`, 4대 대포탈 `Z = -70` (`X = -24 / -8 / +8 / +24`), 북단 레이드 결투장 `(0.5, 65.0, -212.5)`
- **서쪽 상업·행정 대로 (`X = -27 .. -123`)**: 5대 상인 수레(`X = -38..-64`), 츄르 은행(`X = -86, Z = -17`), 대장간 & 30Lv 인챈트 도서관(`X = -88..-102, Z = +10`), 마을 회관(`X = -123, Z = -6`)
- **동쪽 위락·휴식 대로 (`X = +27 .. +118`)**: 2F 황금 냥냥 카지노 홀(`X = 42..84, Z = -38..-8`, 1F 중앙 경묘 트랙 `X = 54..72, Z = -26..-18`), 노천 온천(`X = 38..52, Z = 11..25`), 식당/응급 치유소(`X = 56..68, Z = 8..22`), 벚꽃 선술집(`X = 72, Z = 10`), 낚시 부두(`X = 96..118, Z = 18..34`)
- **남쪽 벚꽃 대교 & 12필지 분양 단지 (`Z = +56 .. +200`)**: 소형 `S-1..S-6` (`15×15`), 중형 `M-1..M-4` (`23×23`), 대형 `L-1..L-2` (`33×33`)

---

## 5. 데이터 영구 보존 주의사항
- 서버 진행도 및 경제/부동산 상태는 `server/plugins/YuumiLandCore/data.yml` (`altar.*`, `bank.*`, `casino.jackpot`, `plots.*`, `trust.*`, `codex.*`) 및 `server/plugins/YuumiLandCore/vaults/<uuid>.yml` (개인 금고 아이템)에 저장됩니다. 스키마 확장 시 하위 호환성을 유지하세요.
