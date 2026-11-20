# 유미의 97랜드 에셋 제작 파이프라인

Minecraft Java 1.21.4 / pack_format 46. 현재 계약은 `docs/directives/active/01_ASTRA_WEAPONS_VFX_AND_MONSTERS.md`다. 이름 붙은 cuboid·UV·픽셀의 편집 원본은 `sources/*.mjs`, 납품은 `resourcepack-gen/overrides`, 납품 JSON을 다시 읽은 Blender 편집·검수 장면은 `sources/blend`에 있다.

## 납품 구성

| 구성 | 기존 | 이번 추가 | 합계 |
| --- | ---: | ---: | ---: |
| 3D JSON / prop PNG 각각 | 35 | 61 | 96 |
| 32px 픽셀 아이콘 | 57 | 4 | 61 |
| GUI 모델 / selector 각각 | 18 | 4 | 22 |
| override item definition | 29 | 61 | 90 |
| blocks atlas single 엔트리 | 35 | 61 | 96 |
| 몬스터 리그 / 본 | 0 | 18 / 42 | 18 / 42 |

총 367 override 파일이다. 신규 GUI 4개는 `models/item/<id>_gui.json`, 기존 18개는 `models/gui/<id>.json`이므로 models/item 파일 수와 3D 모델 수는 다르다. PackBuilder 소유의 기존 기본 pointer 6개 및 순수 2D 모델은 여기에서 추가하지 않는다.

`sources/asset-contract.mjs`가 정확한 ID·분류·크기·파일 목록을 정의한다. 기존 아틀라스 외 174파일은 `sources/preservation-baseline.json`의 SHA256과 일치해야 한다. 이전 납품 보고서·이미지·blend는 역사적 증거로 보존하고 현재 실행 경로는 하나만 유지한다.

## 실행

에셋 재생성 및 정적 검증만 실행한다. `-Build` 인수 자체를 받지 않는다.

```powershell
& E:\MinGem\assets\yuumi97-production\regenerate.ps1
```

Blender 5.2에서 GPU 작업도 직렬 실행한다. 준비 프로세스에서 텍스처를 pack한 .blend를 저장하고, 모델마다 별도 프로세스로 다시 열어 원본 해시를 확인한 뒤 렌더한다.

```powershell
& E:\MinGem\assets\yuumi97-production\tools\render-models.ps1 -Device GPU
& E:\MinGem\assets\yuumi97-production\tools\render-models.ps1 -Device GPU -Rigs
node E:\MinGem\assets\yuumi97-production\tools\compose-review.mjs all
node E:\MinGem\assets\yuumi97-production\tools\verify-delivery.mjs
```

`-ModelIds`로 일부만 재검수할 수 있다. 단품 기본 대상은 성물/길잡이/VFX 19종, `-Rigs` 기본 대상은 조립 몬스터 18종이다. 두 실행은 공통 잠금을 사용한다. Java 21은 검수 이미지를 배열하는 ReviewBoards.java에만 사용하며 팩 빌더나 플러그인을 빌드하지 않는다.

## 생성 소스와 좌표

- `hero-relics.mjs`, `hero-icons.mjs`: 4성물/가이드북, 길잡이, 신규 GUI 아이콘. `hero-grip-anchors.json`은 좌우 손의 중립 표시공간 그립 목표다.
- `skill-vfx.mjs`: 14개 스킬 메시. `skill-vfx-contract.json`은 구성 요소·피벗·방향을 기록한다.
- `monsters-astral.mjs`, `monsters-foundry.mjs`, `monsters-void.mjs`: 각 장 6종/14파트. `monster-rigs.mjs`는 18개 조립 계약을 production과 overrides에 같은 바이트로 출력한다.
- `review-catalog.mjs`: 바닥/수평/수직/방향성/손/본을 명시적으로 구분한다.
- `item-definitions.mjs`: 22 GUI selector, 68 직접 참조, 96 blocks atlas 엔트리를 생성한다.

기본 정면은 −Z, 16 모델 단위가 1블록이다. 본체 바닥은 [8,0,8], 자식 본 회전축은 [8,8,8], 소켓 오프셋은 본체 바닥에서 측정한 스케일 적용 전 모델 단위다. Bukkit yaw·pitch 부호·위치 보정은 [통합 계약](INTEGRATION.md)을 참조한다. 배치 오차를 display.fixed로 보상하지 않는다.

## 검수 산출물

- `review/validation.json`: 파일·참조·RGBA8·UV·좌표·회전·atlas·174 보존·리그 수학·오염 주입 검사.
- `review/astral-clearance.json`, `foundry-clearance.json`, `void-clearance.json`: 18종 × 5개 지정 공격 각도에서 실제 회전 cuboid의 분리축 충돌 및 바닥 검사. 의도된 관절 베어링 겹침은 이름 쌍으로 명시해 별도 기록한다. `tools/check-*-clearance.mjs`로 다시 실행할 수 있고 재생성에도 연결되어 있다.
- `review/models/<id>/`: 19종 정면/측면/후면/3⁄4/상면과 저장본 증거.
- `review/contexts/<id>/`: 좌우/1·3인칭 손 지그, 길잡이 방향, VFX 평면·바닥 접촉.
- `review/rigs/<id>/`: 중립 4방향, −60° 예비동작 2방향, +55° 타격 총 7장. 렌더 후 실제 평가된 회전값도 기록한다.
- `review/overhaul-relics-and-vfx.png`: 필수 19종 시트.
- `review/overhaul-monsters-ch1-ch2-ch3.png`: 필수 18종 조립 시트. 장별·개별 확대 시트도 생성한다.
- `review/visual-review.json`, `review/visual-review.md`: 실제 이미지 관찰·수정 근거. 검증기가 미감을 자동 판정하지 않는다.
- `review/delivery.json`: 최신 파일·증거 SHA256과 검수 결과.

Blender는 실제 Minecraft 광원·셰이더·플레이어 포즈·AI·충돌을 재현하지 않는다. 실제 리소스 로딩·야간 발광·게임플레이 검증은 SOL 통합 확인 항목으로 분리한다.

## 작업 경계

쓰기 범위는 이 production 디렉터리와 resourcepack-gen/overrides뿐이다. yuumi-core, scripts, PackBuilder.java, 건축 파이프라인, 서버 파일을 수정하지 않는다. 전체 빌드·팩 패키징·서버/클라이언트 실행은 하지 않는다.
