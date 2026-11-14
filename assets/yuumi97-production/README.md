# 유미의 97랜드 에셋 제작본

Minecraft Java **1.21.4 / pack_format 46**용 3D 모델 9종과 32×32 아이템 도트 27종이다. 실제 납품 파일은 `E:\MinGem\resourcepack-gen\overrides\assets\yuumi97`과 아틀라스 등록용 `assets\minecraft\atlases\blocks.json`에 있다. 이 문서는 재생성·인수인계 절차이며, UI·인게임 검수의 완료 여부와 남은 항목은 별도 최종 검증 기록을 따른다.

## 제작 범위와 파일 구성

| 우선순위 | 모델 ID | 용도 |
| --- | --- | --- |
| P1 | `prop_yuumi_statue` | 펼친 문턱의 서 위 유미 분수 조각상 |
| P1 | `prop_slot_cabinet` | 고양이 귀, 3릴 창, 우측 레버 슬롯 캐비닛 |
| P1 | `prop_roulette_wheel` | 마호가니 테두리와 금색 스피너 룰렛 |
| P1 | `prop_derby_trophy` | 대리석 받침·질주하는 황금 고양이 트로피 |
| P2 | `prowling_wand` | 핑크·민트 손잡이와 젤리 발바닥 마법봉 |
| P2 | `book_of_thresholds` | 금장 모서리·청록 보석·벌어진 페이지 고서 |
| P2 | `you_and_me_bell` | 분홍 리본과 금색 단짝 방울 |
| P2 | `hat_yuumi_ears` | 머리에 착용하는 유미 귀·방울 머리띠 |
| P2 | `plushie_yuumi` | 액자·진열용 식빵 자세 봉제인형 |

P3는 `PackBuilder.java`의 실제 카탈로그 **27종 전부**를 포함한다.

- 화폐·카지노: `churu_salmon`, `churu_tuna`, `churu_golden`, `casino_chip`, `jackpot_crown`, `chip_100`, `chip_1000`, `chip_10000`
- 성장·이동: `torn_page`, `recall_bookmark`, `starlight_stone`, `starlight_scroll`
- 음식: `zoomies_skewer`, `kneading_bento`, `catnip_elixir`, `royal_feast_can`
- 보스 핵: `boss_core_rat`, `boss_core_hound`, `boss_core_void`
- 책·유물: `book_of_thresholds`, `you_and_me_bell`, `prowling_wand`, `final_chapter_tome`, `nine_lives_charm`
- 슬롯 심볼: `slot_seven`, `slot_yuumi`, `slot_paw`

원래 명세의 P3 열거는 19종이다. 누락된 `casino_chip`, `jackpot_crown`, 기존 3D화 유물 3종, 슬롯 심볼 3종까지 보완했다. P2의 `plushie_yuumi`도 명세에 따라 포함했다.

납품 override는 **52파일**이다: 3D 모델 JSON 9 + 전용 아틀라스 PNG 9 + 아이콘 PNG 27 + GUI 보조 모델 JSON 3 + item definition JSON 3 + 아틀라스 등록 JSON 1. `README.md`는 PackBuilder가 제외한다. 빌드 ZIP은 **108개 엔트리**이며, 기존 27개와 신규 6개를 합쳐 **33개 item definition**이 들어간다. GUI 보조 모델은 별도 아이템을 늘리지 않는다.

## 소스와 검수 자료

- `sources/statue.mjs`, `casino.mjs`, `relics.mjs`: 이름이 붙은 바닐라 cuboid, UV, display 변환과 아틀라스 제작 원본.
- `sources/icons.mjs`: 27종 픽셀 도트 원본. 명시적으로 배치한 실루엣·명암·하이라이트를 RGBA8 PNG로 기록한다. 아이콘은 32×32, P1 아틀라스는 128×128, P2는 64×64다.
- `sources/item-definitions.mjs`: 마법봉·문턱의 서·방울 3종의 표시 문맥 선택과 9종 프롭 텍스처의 아틀라스 등록. `minecraft:display_context=gui`에서는 `models/gui`의 평면 아이콘, 나머지 문맥에서는 `models/item`의 3D 모델을 사용한다.
- `tools/asset-kit.mjs`: 외부 npm 패키지가 없는 Node PNG·모델 작성 도구. 초기 Java2D 도우미 계획을 동일 제작 도구를 공유하는 이 방식으로 대체했다. 프로젝트 Java 소스를 변경하는 방식이 아니다.
- `tools/validate-assets.mjs`: 모델 좌표·회전·UV·텍스처 참조·display, PNG 규격, 27종 ID, 3종 GUI 선택과 프롭 아틀라스 9개 등록을 검사한다. 잘못된 입력이 거부되는 fault injection도 포함한다.
- `tools/render-models.py`: 납품 JSON/PNG를 Blender로 다시 읽고 `sources/blend/*.blend`를 저장·재개방해 다각도 이미지를 만든다. `.blend`는 검수·편집 사본이고 재생성 기준은 `.mjs`다.
- `review/models`, `review/icons`: 모델 렌더와 32px 원본·8배 확대·16px 축소 아이콘 보드. `review/static-validation.json`, `review/icons/validation.json`은 기술 검사 기록이다. `review/build-final.log`는 빌드 로그다.
- `review/revisions`, `backups`: 수정 전 검수 자료와 보존본. 소스·렌더·문서는 override에 넣지 않는다. PackBuilder는 README와 `.gitkeep` 외의 일반 파일을 모두 팩에 포함한다.

**`textures/prop` 사용 시 아틀라스 등록은 필수다.** Minecraft의 blocks 아틀라스가 자동 탐색하는 `textures/item`, `textures/block`과 달리 `textures/prop`은 모델 JSON에서 참조하고 파일이 존재하는 것만으로 등록되지 않는다. `overrides/assets/minecraft/atlases/blocks.json`의 `minecraft:single` source 9개가 `yuumi97:prop/<모델 ID>`를 등록하며, 바닐라 아틀라스 source와 병합된다. 이 파일 누락은 실제 클라이언트에서 텍스처 로딩 실패를 일으킨다. **프롭 텍스처나 모델을 추가·이름 변경할 때는 `sources/item-definitions.mjs`의 아틀라스 ID 목록도 반드시 갱신한다.** 기본 `regenerate.ps1` 순서에 해당 생성기가 포함되어 있으므로 별도의 수동 복사 단계는 없다.

Blender 재검수는 별도 작업이다. 설치된 Blender에서 다음 형태로 실행한다. GPU 렌더는 병렬로 겹치지 않게 수행한다.

```powershell
& '<Blender 실행 파일의 절대 경로>' -b --factory-startup --python-exit-code 1 --python E:\MinGem\assets\yuumi97-production\tools\render-models.py -- prop_yuumi_statue
```

## 재생성과 빌드

설치된 Node를 `Get-Command node`로 찾는다. PNG·JSON 생성에는 npm 설치가 필요 없다. 빌드는 저장소의 포터블 `runtime/jdk-21`과 `runtime/gradle`을 사용하는 기존 `scripts/build-all.ps1`에 맡긴다.

```powershell
# 9종 모델 + 27종 아이콘 + GUI 선택 재생성, 정적 검사. 서버 빌드는 수행하지 않는다.
powershell -NoProfile -ExecutionPolicy Bypass -File E:\MinGem\assets\yuumi97-production\regenerate.ps1

# 위 작업 후 리소스팩 생성 및 플러그인 빌드·배포까지 수행한다.
powershell -NoProfile -ExecutionPolicy Bypass -File E:\MinGem\assets\yuumi97-production\regenerate.ps1 -Build
```

생성 순서는 `statue → casino → relics → icons → item-definitions → validate-assets`로 고정하며 한 단계라도 실패하면 중단한다. `-Build`일 때만 `GRADLE_USER_HOME`을 저장소 내부 `runtime/gradle-user-home`으로 지정하고 종료 후 기존 환경값을 복원한다.

기본 재생성은 동일 ID의 override와 검수표를 덮어쓴다. `-Build`는 추가로 `server/resourcepack/Yuumi97Pack.zip`, `.sha1`, `server/server-icon.png`, `server/plugins/YuumiLandCore.jar`를 갱신한다. 중요한 수동 수정은 먼저 보존하고 실행한다. ZIP은 엔트리 시각 때문에 동일 원본으로 재빌드해도 해시가 달라질 수 있으므로 해당 ZIP과 `.sha1`의 일치를 확인한다.

## 서버 반영과 확인

`ResourcePackServer`는 시작 시 ZIP 바이트와 해시를 메모리에 보관한다. 빌드 후 운영자가 서버를 정상 종료·재시작하고 클라이언트를 재접속해 새 팩을 받아야 한다. `/메뉴 props`만으로 HTTP 팩 캐시가 갱신되지는 않는다. 이 재생성 스크립트는 서버를 시작·종료하거나 플레이어 데이터를 바꾸지 않는다.

- OP `/메뉴 props`: 조각상 1, 슬롯 7, 룰렛 1, 트로피 1의 총 10개 `ItemDisplay` 배치를 갱신한다.
- OP `/메뉴 give hat_yuumi_ears`, `/메뉴 give plushie_yuumi`: 신규 외형 지급. 모자는 실제 머리 착용, 봉제인형은 액자·지면 문맥까지 확인한다.
- 유물 기능 확인에는 `/메뉴 give wand`, `/메뉴 give bell`과 서버가 정상 지급한 문턱의 서를 사용한다. 임의 ID 지급의 기본 분기는 외형을 입힌 조각된 호박이므로 실제 유물 기능 검증을 대신하지 않는다.
- 3종 유물은 GUI에서 도트, 1·3인칭 손과 월드에서는 입체 모델인지 확인한다. 16px에서는 색과 실루엣을 기준으로 판독하고 작은 숫자·룬의 완전한 가독성은 32px 기준으로 본다.
- 카지노 모델은 정적 장식이다. 릴·룰렛 애니메이션, 보상·확률·화폐 로직을 새로 구현하지 않는다. 기존 클릭 대상 블록, NPC 접근로, 캐비닛 주변 이동과 시야가 유지되는지 실제 게임에서 확인한다.

ZIP 패키징·빌드 성공과 Blender 렌더는 실제 Minecraft 로더·머리 착용·손 배치 검증을 대신하지 않는다. 검수 증거가 없는 항목은 최종 기록에 미검증으로 남긴다. 서버 진행도·경제·분양·개인 금고 데이터와 Java 게임 로직은 이 에셋 제작 범위에 포함하지 않는다.
