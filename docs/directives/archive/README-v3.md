# 유미의 97랜드 v3.0

## 플레이 동선

- `/메뉴`: 성향·각성·레벨·자산·미궁 클리어 확인, 시설 나침반, 미수령 보상. `/메뉴 보상`으로도 보상을 받습니다.
- 광장 북서 가제보의 마스터냥: 10대 성향 선택, 성물 재발급, Lv.10 A/B 각성. 문장 우클릭: 특전·3슬롯 책갈피함. 직업 성장과 각성은 직업별로 보존됩니다.
- `/파티 생성|초대 <이름>|수락|탈퇴|추방 <이름>|정보` (영문 `/party`). `/미궁`은 길찾기입니다. 전원 북쪽 관문 12블록 이내 집결 후 동의하면 1~8인 입장합니다.
- T1 무료, T2·T3 파티장 열쇠 1개. 30분 제한, 접속 종료 3분 유예. 사망자는 무료 관전 후 다음 방에서 부활합니다. 재시작 시 원정을 종료하고 안전 복구합니다.
- 대장간의 공방: 지오드 감정·5종 룬 제작/부여. 건축 상인: 가구 공방. 식당: 3종 만찬. 모든 직업이 이용하며 전문직은 공임·재료·품질 우대를 받습니다.
- 카지노 2층 서북 라운지: 4대 종목 증권 거래. 10분 시세 갱신, 종목당 200주 한도, 매도세 2%는 잭팟에 적립됩니다.
- 광장 북동 가챠: 캡슐 1개 / 고대 금화 3개 / 액면 합계 1,500칩 중 선택. 캡슐은 직접 우클릭 개봉도 가능합니다.
- 가구는 본인·공유 필지의 바닥에 우클릭 설치합니다. 빈손 Shift+우클릭 45도 회전, 코타츠 우클릭 착석, Shift+좌클릭 회수. 매각·초기화 시 설치자에게 반환됩니다.
- `/칭호` (영문 `/title`): 획득 칭호 선택. 광장 명예의 전당에 갑부·미궁·세공 Top 3가 표시됩니다.

## 성물 조작

일반 성물은 우클릭 주력기, 각성 후 Shift+우클릭 궁극기입니다. 활은 Shift+좌클릭 주력기로 바닐라 시위 당기기를 보존합니다. 낚싯대는 좌클릭 주력기이며 찌가 없을 때만 Shift+우클릭 궁극기를 사용합니다. 대낫은 성숙 작물 우클릭 수확이 우선입니다. NPC와 상호작용 블록에서는 전투 스킬을 억제합니다.

## 빌드·검증

저장소의 포터블 JDK 21과 Gradle만 사용합니다. `GRADLE_USER_HOME`은 저장소의 `runtime/gradle-user-home`으로 지정합니다.

```powershell
$env:JAVA_HOME='E:\MinGem\runtime\jdk-21'
$env:GRADLE_USER_HOME='E:\MinGem\runtime\gradle-user-home'
& 'E:\MinGem\runtime\gradle\bin\gradle.bat' -p 'E:\MinGem\yuumi-core' v3Checks jar --no-daemon '-Dorg.gradle.java.home=E:\MinGem\runtime\jdk-21'
powershell -ExecutionPolicy Bypass -File E:\MinGem\scripts\build-all.ps1
powershell -ExecutionPolicy Bypass -File E:\MinGem\yuumi-core\src\test\verify-pack.ps1
powershell -ExecutionPolicy Bypass -File E:\MinGem\scripts\verify-boot.ps1
```

콘솔 또는 OP의 `/메뉴 verify-v3`는 Paper 위에서 아이템/YAML 왕복, 성장 메타데이터 보존, NBT 20개, 인접 보관함 보호, 허브·미궁 준비 상태를 검사합니다.

`v3Checks`는 경제·가챠 분포·직업 성장/제작/SP·280종 회전 경계·12필지·미궁 인원 배율과 유예 경계를 검사합니다. 팩 검사는 폴백 전용 산출물도 필요합니다. 생성 명령:

```powershell
& 'E:\MinGem\runtime\jdk-21\bin\java.exe' E:\MinGem\resourcepack-gen\PackBuilder.java E:\MinGem\yuumi-core\build\fallback-verification
```

기존 부팅 스크립트의 종료 코드만으로 오류 유무를 판단하지 말고 로그의 `ERROR`·`Exception`·구조물 누락, `hub_v3.built=true`, 던전 로드와 정상 종료를 함께 확인하세요. 실제 클라이언트 조작/외형 검증은 서버 부팅 검사와 별개입니다.

## 데이터·동시 작업

기존 데이터 키와 금고 파일은 보존됩니다. 신규 상태는 `rpg`, `dungeon`, `stocks`, `housing`, `social`, `rewards` 등에 추가하며, 아이템 세공은 기존 `starlight_level`을 계속 읽고 `yuumi_star_level`도 호환합니다. 미수령 보상은 재접속 또는 수동 수령할 수 있습니다.

v3 허브 마이그레이션은 소유 필지의 전체 높이와 설치 엔티티를 제외하고 공공 구역을 재건축합니다. 배포 전 `yuumi_hub`와 플러그인 데이터·금고를 별도로 백업하세요. 기존 백업 스크립트는 허브를 포함하지 않습니다.

`assets/yuumi97-production` 및 `resourcepack-gen/overrides`는 에셋 전담 세션의 경로입니다. 이 구현은 두 경로에 쓰지 않습니다. 패키징은 해당 오버라이드를 읽어서 최종 ZIP에 우선 병합합니다.
