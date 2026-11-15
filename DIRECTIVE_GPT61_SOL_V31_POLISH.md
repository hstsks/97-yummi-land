# 💻 [GPT 6.1 SOL 전용 작업 지시서 — v3.1] 광장 사각지대 고밀도화 · 초호화 카지노 궁전 외관 · 딜러 단상/히트박스 · BAT/PS1 한글 인코딩 패치

당신은 **`GPT 6.1 SOL`** 이며, `유미의 97랜드 v3.1` 폴리싱 패치의 **인게임 허브 건축/조경 대개조, 카지노 내부 딜러 UX 개선, DPS 허수아비 시스템, 서버 실행 스크립트 인코딩 완결 전담 엔지니어**입니다.
동시에 다른 세션에서 **`GPT 6 Astra`** 가 광장·카지노용 초대형 3D 랜드마크 프롭 6종(`DIRECTIVE_GPT6_ASTRA_V31_LANDMARKS.md`)을 제작 중이므로 아래 **파일 경계 규칙**과 **상세 엔지니어링 명세**를 100% 준수하여 구현 및 검증을 완료하세요.

---

## 0. 동시 병렬 작업 절대 규칙 (Zero-Collision Rules)

1. **쓰기 허용 경로 (Write Scope)**:
   - `E:\MinGem\yuumi-core\**` (`HubWorldManager.java`, `CasinoManager.java`, `RpgCombatListener.java`, `YuumiPlugin.java`, `V3RuntimeChecks.java` 등)
   - `E:\MinGem\START_SERVER.bat` 및 `E:\MinGem\scripts\*.ps1`
2. **수정 절대 금지 경로 (Forbidden Scope)**:
   - `E:\MinGem\assets\yuumi97-production\**` (`GPT 6 Astra` 전담!)
   - `E:\MinGem\resourcepack-gen\overrides\**` (`GPT 6 Astra`가 실시간 갱신 중이므로 절대 쓰기/삭제 금지! 읽기는 허용)
3. **포터블 런타임 사용**:
   - 반드시 `E:\MinGem\runtime\jdk-21\bin\java.exe` 및 `E:\MinGem\runtime\gradle\bin\gradle.bat`을 사용하여 빌드·검증하세요.

---

## 1. Module A: `START_SERVER.bat` & `scripts/*.ps1` 한글 깨짐 100% 근절 + 중복 구동 가드

사용자 실제 콘솔 스크린샷에서 확인된 3가지 버그(`'誘몄닔'은(는) 내부 또는 외부 명령...`, PowerShell 한글 깨짐 `?뤇 [?좊??97?쒕뱶]...`, 이미 켜진 서버 위 중복 실행 시 `latest.log` 잠금 오류 및 무한 백업 루프)를 완벽히 해결합니다.

1. **`E:\MinGem\START_SERVER.bat` — 100% 순수 영문 ASCII화 (BOM 없는 ASCII 저장!)**:
   - Windows `cmd.exe`에서 `chcp 65001` 직후 멀티바이트 한글(`title ...`)이 오면 바이트 오프셋이 밀려 한글 중간 바이트를 명령어로 오인 실행하는 고질적 버그를 원천 차단하기 위해, `.bat` 파일에는 한글을 단 1글자도 넣지 마세요:
     ```bat
     @echo off
     chcp 65001 >nul
     powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-server.ps1"
     pause
     ```
2. **`E:\MinGem\scripts\*.ps1` 전체 (10개 파일 모두!) — UTF-8 with BOM (`0xEF, 0xBB, 0xBF`) 일괄 변환**:
   - Windows PowerShell 5.1(`powershell.exe`)은 UTF-8 BOM이 없는 `.ps1` 파일을 무조건 CP949(ANSI)로 파싱해 한글 리터럴이 전부 깨집니다. `scripts/` 폴더 내 모든 `.ps1` 파일을 **UTF-8 with BOM**으로 재저장하고 검증 테스트로 바이트 헤더(`EF BB BF`)를 확인하세요!
3. **`E:\MinGem\scripts\start-server.ps1` 기능 보강**:
   - 스크립트 최상단(파라미터 직후)에 아래 콘솔 인코딩 및 창 타이틀 설정 적용:
     ```powershell
     [Console]::InputEncoding  = [System.Text.Encoding]::UTF8
     [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
     $OutputEncoding           = [System.Text.Encoding]::UTF8
     $Host.UI.RawUI.WindowTitle = "[유미의 97랜드 v3.1] 마인크래프트 자율 운영 서버"
     ```
   - `$JvmArgs`에 `-Dsun.stdout.encoding=UTF-8`, `-Dsun.stderr.encoding=UTF-8`, `-Dstdout.encoding=UTF-8`, `-Dstderr.encoding=UTF-8`을 추가하여 Java 21 표준출력/에러 한글 깨짐까지 완전 방지하세요.
   - **포트 `25565` 중복 구동 감지 가드**: `while ($true)` 진입 전 및 재기동 전에 현재 로컬 TCP `25565` 포트가 이미 `LISTENING` 상태인지(`Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue` 또는 소켓 프로브) 확인하여, 이미 서버가 실행 중이면 `[경고] 이미 유미의 97랜드 서버(포트 25565)가 실행 중입니다! 기존 서버 콘솔 창을 확인해 주세요.`를 출력하고 즉시 `break` 하여 `latest.log` 충돌과 무한 백업을 차단하세요.
4. **`E:\MinGem\scripts\backup-world.ps1` 보강**:
   - `$Targets` 배열에 `"yuumi_hub"`를 추가(`@("world", "world_nether", "world_the_end", "yuumi_hub", "plugins\YuumiLandCore")`)하여 남쪽 12필지 유저 분양 주택까지 안전하게 백업되도록 개선하세요.

---

## 2. Module B: 중앙 광장 내·외곽 빈 공간 완전 해소 & DPS 허수아비 수련장 (`hub_v3_1.built`)

사용자 인게임 스크린샷에서 확인된 핵심 원인은 **반경 `26.5` 원형 광장 바깥쪽 5개 잔디 포켓(`~2,200 m²`)이 여전히 `Y=64` 평면 초록 잔디밭으로 비어 있었고, `plantCherryTree`가 높이 4블록 일자 막대기 나무라 스카이라인이 허전했던 것**입니다.

### 2.1 안전한 자동 업그레이드 마이그레이션 (`hub_v3_1.built`)
- `HubWorldManager#ensureCityBuilt()`에서 `!data.getBoolean("hub_v3_1.built", false)`이면 공공 구역 전체를 `v3.1` 레이아웃으로 재구축한 뒤 `data.set("hub_v3_1.built", true)`를 저장합니다 (**남쪽 12필지 중 주인이 있는 필지 `isOwnedColumn`은 기존과 동일하게 100% 절대 보호!** `/메뉴 setup-hub` 호출 시에도 `v3.1` 전체가 재구축되도록 연동).

### 2.2 막대기 나무 폐기 → 입체 수제 왕벚나무 (`plantGrandSakuraTree`) 전면 적용
- 기존 `plantCherryTree`를 업그레이드(또는 `plantGrandSakuraTree`로 교체)하여, 굵은 밑동 뿌리(`CHERRY_WOOD` 보강) + 높이 `8~11`블록으로 자연스럽게 휘어지며 갈라지는 3~4갈래 큰 가지 + 반경 `4.5~5.5`블록의 풍성한 다층 `CHERRY_LEAVES` 수관 + 수관 아래로 살짝 늘어진 잎줄기·매달린 `LANTERN` / `SPORE_BLOSSOM` + 지면 주변 `PINK_PETALS`(꽃잎 카펫)·꽃 화초가 어우러진 **진짜 왕벚나무**를 심어 시야 상단을 포근하게 채우세요! (단, 주요 보행 도로 중앙 `|X|<=5` 또는 `|Z|<=5` 통행 시야는 막지 않도록 수관 하단 높이를 `Y >= 69` 이상으로 확보).

### 2.3 원형 광장 외곽 5대 빈 잔디 사각지대 100% 알차게 채우기 (절대 빈 평지 방치 금지!)
기존 도로·대포탈·상점과 충돌하지 않는 정확한 좌표에 아래 5대 외곽 풍경 구역을 조성합니다:

1. **북서 외곽 (`X = -36..-8, Z = -44..-24`) — 『🕰 24m 황금 고양이 시계탑 & 별빛 허수아비 수련장』**:
   - `(-26, 64, -34)` 중심에 석영 기둥·심층암 지붕·금장 테두리·종루가 어우러진 **높이 24블록(`Y = 65..88`) 고풍스러운 랜드마크 시계탑**을 건설하고, 시계탑 남쪽·동쪽 상단 외벽(`Y = 79`)에 Astra의 3D 시계판 **`prop_plaza_clock_face` (`scale = 2.0f`)**(미존재 시 금블록·시계 액자 폴백)를 마운트합니다!
   - 북서 전직 가제보 바로 북측(`X = -22..-10, Z = -28..-21`)에 석재 타일 바닥과 무기 거치대, 횃불 화로를 두른 **『⚔ 성향 스킬 & DPS 수련 마당』** 을 포장하고, **`(-19.5, 65.0, -25.5)` / `(-15.5, 65.0, -26.5)` / `(-11.5, 65.0, -25.5)`** 3개 지점에 **타격 가능한 수련용 허수아비 3기**를 배치합니다!
     - *수련 허수아비 기능 (`HubWorldManager` + `RpgCombatListener` 연동)*:
       - 외형: Astra의 `prop_training_dummy` (`scale = 1.35f`, 미존재 시 건초더미+조각된 호박/갑옷거치대 폴백) + 무적/체력 자동회복 LivingEntity(예: AI 꺼진 무적 해제 커스텀 고정 `ArmorStand`/`Zombie`/`Piglin` 또는 체력 `2048` 고정 재생 수련 마네킹 엔티티 — `yuumi_training_dummy` PDC 태그 부착!).
       - `HubWorldManager#onEntityDamage` 및 `RpgCombatListener#hostile`에서 **`yuumi_training_dummy` 엔티티에 한해 허브 평화 구역 내에서도 플레이어의 평타·활·10대 성물 주력기·각성 궁극기·룬 효과가 모두 정상 적중**하도록 허용하세요! (단, 허수아비는 넉백되지 않고 죽지 않으며 매 타격 즉시 체력을 회복하고, 타격한 플레이어의 액션바/임시 홀로그램에 **`🎯 [수련 허수아비] 이번 타격: 24.5 DMG │ 최근 5초 DPS: 48.2`** 를 실시간 표시 + 타격 효과음 재생! 경험치/영혼석 드롭은 당연히 제외!).
2. **남서 외곽 (`X = -37..-7, Z = +23..+34`, 남쪽 운하 북안 서측) — 『🍵 수변 벚꽃 찻집 & 화덕 베이커리 테라스』**:
   - 스크린샷 좌측 하단에 비어 보이던 초록 잔디밭을 따뜻한 화강암·벚꽃 목재 수변 프롬나드로 포장하고, `v3/jp_small_house_1.nbt`(또는 `jp_medium_house_1.nbt`) 찻집 건물, 벽돌 화덕 오븐(`SMOKER` + `BRICKS` 굴뚝), 운하(`Z=35`)를 내려다보는 난간 테라스 벤치, 풍성한 왕벚나무 2그루와 꽃수레로 꽉 채웁니다.
3. **남동 외곽 (`X = +7..+36, Z = +23..+34`, 남쪽 운하 북안 동측) — 『🌸 운하 전망 정자 & 비단잉어 아치 목교 정원』**:
   - 스크린샷 우측 하단의 빈 잔디밭과 남동 광장 정원을 하나로 이어, 굽이치는 작은 **연못/수로(`PRISMARINE` 바닥 + `SEA_LANTERN` + 수련잎)** 와 그 위를 가로지르는 **붉은 아치형 일본풍 목교(타이코바시, `MANGROVE_STAIRS` / `CHERRY_STAIRS` & `CHERRY_FENCE`)**, 수변 정자(`v3/jp_gazebo_1.nbt`), 석등(`STONE_BRICK_WALL` + `LANTERN`), 왕벚나무 군락을 조성합니다.
4. **북동 외곽 (`X = +8..+28, Z = -44..-24`) — 『🏛 명예의 전당 개선문 단상 & 황금 가챠 야타이 파빌리온』**:
   - 기존의 작은 금블록 3개 단상을 **백색 석영 열주와 황금 월계관 아치가 감싸는 웅장한 『명예의 전당 챔피언 스테이지』** 로 격상하고, 가챠 부스 역시 비바람을 막아주는 화려한 일본 축제 지붕 파빌리온과 인형/모자 진열대로 업그레이드합니다.
5. **동측 광장~카지노 연결부 (`X = +26..+41, Z = -36..-7`) — 『⛲ 벨라지오 댄싱 분수 전정 (Casino Forecourt)』**:
   - 광장 동쪽 끝에서 카지노 서벽(`X = 42`)까지 이어지던 640m²의 빈 잔디밭을 완전히 없애고, 황금·석영 모자이크 광장 바닥과 좌우 대칭 **벨라지오 조명 분수대 2조**(`PRISMARINE_BRICKS` + `GOLD_BLOCK` 수조, `END_ROD`와 유리 기둥으로 솟구치는 하얀 물줄기 연출!), 레드카펫 산책로, 황금 가로등 열주로 화려하게 연결합니다!

### 2.4 원형 광장 내부(`R = 0..26`) 입체감 강화
- **동·서·남·북 4대 방위 환영 대문 (`(0, 65, -26)`, `(0, 65, +26)`, `(-26, 65, 0)`, `(+26, 65, 0)`)**:
  - 4개 대로가 원형 광장과 만나는 지점에 통행 폭 7블록 이상의 웅장한 **벚꽃·석영·금장 환영 아치 대문**을 세우고, 각 대문 상단 중앙(`Y = 72.5`)에 Astra의 **`prop_sakura_lantern_arch` (`scale = 2.0f`)** 를 마운트합니다!
- **중앙 분수대 2단 확장 (`R = 4` → `R = 7.0`) & 공중 방사형 연등 가랜드**:
  - 중앙 분수대 둘레에 4개의 사분원 꽃화단(`PINK_TULIP`, `ALLIUM`, `CHERRY_LEAVES` 생울타리)과 곡선형 휴식 벤치를 두르고, 중앙 분수 탑 상단(`Y = 74`)에서 동·서·남·북 4대 환영 대문 상단으로 이어지는 **4줄의 공중 체인·연등·벚꽃 가랜드(`Y = 72..74`)** 를 연결해 광장 상공의 개방감과 축제 분위기를 동시에 잡으세요!

---

## 3. Module C: 『황금 냥냥 카지노 홀』 라스베이거스 궁전급 외관 대개조 (`휘황찬란 삐까뻔쩍!`)

중앙 광장에서 동쪽을 바라봤을 때 단번에 감탄이 나오도록 `buildGoldenPawsCasinoHall` + `buildV3Casino`의 외관을 전면 업그레이드합니다 (**내부 1F 경묘 트랙 `53..73, -27..-17`, 1F/2F 슬롯 `X=44`, 2F 증권소 `46..58, -35..-29`의 기능 좌표는 그대로 보존!**).

1. **평지붕 탈피 → 4대 코너 황금 첨탑(`Y = 87`) & 중앙 스테인드글라스 황금 크라운 대돔(`Y = 91`)**:
   - 건물의 네 모퉁이(`SW: 42..47, -13..-8`, `SE: 79..84, -13..-8`, `NW: 42..47, -38..-33`, `NE: 79..84, -38..-33`)를 `Y = 76` 위로 더 솟아오른 **4개의 8각/사각 황금·크림슨 벨벳 첨탑(`Y = 76..87`, 최상단 금블록+프로그라이트+엔드막대 피너클)** 으로 증축합니다!
   - 중앙 아트리움 상공(`X = 53..73, Z = -28..-16`)에는 평평한 유리 대신 **`Y = 76..88` 높이로 우아하게 솟아오른 황금 리브(Gold Rib) & 옐로우/마젠타 스테인드글라스 그랜드 돔**을 올리고, 돔 꼭대기(`63, 89..91, -22`)에 빛나는 황금 왕관 조형물을 얹습니다.
2. **서측 광장 정면 파사드(`X = 41..42, Z = -38..-8`) 초호화 쇼피스화**:
   - 중앙 광장에서 가장 넓게 보이는 면이 바로 **`X = 42` 서측 외벽**입니다!
   - 서측 외벽 바깥(`X = 41`)에 일정 간격으로 **입체 석영 열주(Pilaster) + 황금 주두 + 수직 `OCHRE_FROGLIGHT` / `PEARLESCENT_FROGLIGHT` 네온 라인**과 아치형 크림슨 몰딩을 입혀 평면 느낌을 완전히 지웁니다.
   - 서측 외벽 정중앙 상단(`X = 40.5, Y = 77.5, Z = -23.0, yaw = 90f`)에 Astra의 초대형 **`prop_casino_marquee` (`scale = 2.7f`)** 를 마운트하고, 배경 박공(`X = 41..42, Y = 76..83, Z = -29..-17`)에도 황금·크림슨·프로그라이트 아치 크라운을 세워 **광장 어디서나 빛나는 `777` 카지노 궁전**으로 보이게 만드세요!
   - *무료 급식소 위치 정돈*: 기존에 서측 광장 정면(`40, 65, -13`)에 노출되어 럭셔리 뷰를 가렸던 뒷골목 무료 급식소와 이모냥(`casino_welfare`)을 **카지노 북서쪽 아늑한 뒷골목(`X = 40, Y = 65, Z = -35`, 북서쪽 작은 쪽문 `X = 42, Z = -35` 개방)** 으로 깔끔히 이동시키고, 기존 `Z = -13..-12` 서벽은 우아한 쇼윈도로 마감하세요!
3. **남측 그랜드 코린트식 포르티코 현관(`X = 55..71, Z = -8..-3`) & 아트리움 대형 샹들리에**:
   - 남측 입구(`Z = -8`) 앞으로 6개의 2층 높이(`Y = 65..74`) 석영·금장 열주와 화려한 삼각 박공(Pediment, `Y = 75..81`), 레드카펫 진입 계단, 입구 좌우 황금 화로대 & 슬롯/트로피 쇼케이스를 세우고, 남측 박공 정면(`63.5, 77.5, -3.2, yaw = 0f`)에도 **`prop_casino_marquee` (`scale = 2.7f`)** 를 마운트합니다!
   - 카지노 내부 중앙 아트리움 천장(`63.0, 74.5, -22.0`)에는 Astra의 **`prop_casino_chandelier` (`scale = 2.3f`)** 를 마운트합니다 (폴백 시에도 화려한 금장·자수정·바다랜턴 샹들리에 유지).

---

## 4. Module D: 카지노 내부 딜러 고양이 가림 현상 완전 해소 (단상 `+1m` · 스케일 확대 · 대형 히트박스)

기존 코드에서 `X=79, Y=65`에 높이 1m 통블록(`EMERALD_BLOCK`, `LODESTONE`)을 두고 딜러 고양이를 그 뒤 바닥 `X=80.5, Y=65.0`에 앉혀두어 플레이어(`X=77`) 시야에서 **100% 블록 뒤에 가려졌던 문제**를 완벽히 해결합니다.

1. **카지노 6대 NPC 전원 『1블록 높이 금장 벨벳 단상(`Pedestal`) 상향 + `prop_dealer_throne` 백드롭』**:
   - `clearNpcPad` 수정: NPC 발밑(`floorY`) 블록이 이미 `GOLD_BLOCK` / `CHISELED_QUARTZ_BLOCK` 등 단상 블록일 경우 `QUARTZ_BRICKS`로 덮어쓰지 않고 단상을 보존하도록 개선하세요.
   - **환전소(`61..65, 65, -12`) 유리 가림막 제거**: 시야를 막던 `Y = 66` `YELLOW_STAINED_GLASS_PANE`을 전면 제거하고 낮은 역계단/상단 반블록 오픈 카운터로 교체한 뒤, 중앙 `(63, 65, -12)`에 `GOLD_BLOCK` 단상을 놓고 **`캐셔냥`을 `(63.5, 66.0, -12.0, yaw = 0f)`** 에 앉힙니다 (뒤편 `(63.5, 66.0, -12.7)`에 `prop_dealer_throne` 마운트!).
   - **`1F 경묘 캐스터냥`**: `(52, 65, -15)` `GOLD_BLOCK` 단상 위 → **`(52.5, 66.0, -15.0, yaw = 0f)`** 배치!
   - **`1F 블랙잭 테이블 & 딜러냥`**:
     - `X = 79, Z = -17..-15` 테이블을 시야를 가리지 않는 낮은 고급 카지노 테이블(예: 바닥 `Y = 64`에 `EMERALD_BLOCK`을 매립하고 `Y = 65`에는 `GREEN_CARPET` 또는 상단 다크오크 트랩도어/반블록 배치 — `CasinoManager#onClickPhysicalCasinoMachine`에서 해당 블록 우클릭 시 동일하게 블랙잭 오픈 유지!)로 개선합니다.
     - `(80, 65, -16)`에 `GOLD_BLOCK` 딜러 단상을 세우고 **`1F 블랙잭 딜러냥`을 `(80.5, 66.0, -16.0, yaw = 90f)`**(서쪽 `-X` 플레이어를 정면 응시!)에 앉힙니다 + 뒤편 `(81.2, 66.0, -16.0, yaw = 90f)`에 `prop_dealer_throne` 마운트!
   - **`1F 룰렛 테이블 & 딜러냥`**:
     - `(79, 65, -23)` 룰렛 휠(`prop_roulette_wheel`)에 가리지 않도록, 딜러 단상을 바로 옆 오픈 라인인 **`(80, 65, -21)`** 에 `GOLD_BLOCK`으로 세우고 **`1F 룰렛 딜러냥`을 `(80.5, 66.0, -21.0, yaw = 90f)`** 에 앉힙니다 + 뒤편 `prop_dealer_throne` 마운트!
   - **`2F VIP 블랙잭 테이블 & 딜러냥`**:
     - 1F와 동일한 낮은 그린 카펫/트랩도어 오픈 테이블 적용 후, `(80, 71, -22)` `GOLD_BLOCK` 단상 위 **`(80.5, 72.0, -22.0, yaw = 90f)`** 에 앉힙니다 + 뒤편 `prop_dealer_throne` 마운트!
   - **`2F 츄르 증권 중개묘`**:
     - 전면 데스크(`Z = -33` 또는 `-32`)를 시야 가림 없는 낮은 카펫/반블록 데스크로 정돈하고, `(52, 71, -33)` `GOLD_BLOCK` 단상 위 **`(52.5, 72.0, -33.0, yaw = 0f)`** 에 앉힙니다 (`getStockBrokerLocation()`도 동일 좌표로 동기화).
2. **고양이 스케일 확대(`Attribute.SCALE`) & `1.35 × 2.0` 대형 `Interaction` 클릭 판정 릴레이**:
   - `spawnCatNpc`에서 스폰되는 모든 고양이 NPC에 `Attribute.SCALE`을 적용해 크기를 **`1.28배`**(카지노 딜러·캐셔·중개묘는 **`1.35배`**!)로 키워 멀리서도 한눈에 들어오게 하고, 이름표 홀로그램 오프셋을 `+1.65m`로 자연스럽게 올립니다.
   - 각 고양이 NPC 위치에 동일한 `npcRoleKey`(`hub_npc_role`)와 `hubDecorKey`를 가진 투명 **`Interaction` 엔티티(`interactionWidth = 1.35f, interactionHeight = 2.0f, responsive = true`)** 를 함께 스폰하고, `HubWorldManager`에서 `PlayerInteractEntityEvent` 및 `PlayerInteractAtEntityEvent` 모두 `npcRoleKey`를 라우팅하도록 처리하여 **테이블 너머에서 대충 딜러 쪽을 우클릭해도 100% 한 번에 메뉴가 열리게** 만드세요!

---

## 5. `verify-v3` 자동 자가진단 확장 & 최종 완료 검증 조건

1. `V3RuntimeChecks.java`에 아래 **v3.1 자동 검증 항목**을 추가하세요:
   - `hub_v3_1.built == true` 확인.
   - 카지노 내부 5개 주요 딜러/캐셔/중개묘 각각에 대해, 플레이어 이용석(`캐셔 앞 (63.5, 66.6, -9.5)`, `1F 블랙잭 앞 (77.5, 66.6, -16.0)`, `1F 룰렛 앞 (77.5, 66.6, -21.0)`, `2F VIP 블랙잭 앞 (77.5, 72.6, -22.0)`, `2F 증권 앞 (52.5, 72.6, -30.5)`) 눈높이에서 딜러 고양이 중심(`cat.getLocation().add(0, 0.5, 0)`)으로 `world.rayTraceBlocks`를 쏘았을 때 **중간에 가리는 불투명 블록이 `null`(시야 100% 개방!)인지 수학적으로 검증**!
   - 북서 수련장 `yuumi_training_dummy` 3기 스폰 확인.
2. 파워셸로 `START_SERVER.bat`가 100% ASCII(`모든 바이트 < 0x80`)인지, 그리고 `E:\MinGem\scripts\*.ps1` 전 파일이 UTF-8 BOM(`0xEF, 0xBB, 0xBF`)으로 시작하는지 검증하세요.
3. 최종적으로 `powershell -ExecutionPolicy Bypass -File E:\MinGem\scripts\build-all.ps1` 및 `scripts\verify-boot.ps1` (`/메뉴 verify-v3` 포함)을 실행하여 **컴파일 에러 0건, 부팅/종료 예외 0건, 딜러 시야 RayTrace 테스트 100% 통과**를 확인한 뒤 완료 보고하세요.
