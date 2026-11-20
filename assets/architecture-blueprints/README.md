# 유미의 97랜드 — 건축 NBT 제작·검수

Stream 02의 편집 가능한 건축 소스와 독립 검증·컬러 단면 렌더 파이프라인입니다. Minecraft Java 1.21.4 / DataVersion 4189 전용입니다. 기준은 `docs/directives/active/02_CARBON_ASTRA_ARCHITECTURE_NBT.md`와 `docs/directives/concepts/03_seamless_megadungeons_concept.png`입니다.

## 재생성

저장소 루트 `E:\MinGem`에서 Node.js 24로 실행합니다. 일반 제작·검수에는 npm 설치, Python, Blender, Java 또는 실행 중인 서버가 필요하지 않습니다.

```powershell
node assets/architecture-blueprints/build-structures.mjs
node assets/architecture-blueprints/verify-structures.mjs
node assets/architecture-blueprints/test-verifier.mjs
node assets/architecture-blueprints/render-review.mjs
# 재컴파일해 NBT 20종 + manifest 바이트가 그대로인지 확인
node assets/architecture-blueprints/test-reproducibility.mjs
```

`build-structures.mjs`는 `yuumi-core/src/main/resources/structures/overhaul/`의 지정된 20종과 `manifest.json`만 생성·갱신합니다. 생성기 실행 후 검증 종료 코드 0과 `review/verification-report.json`의 `valid: true`를 확인하고 렌더 이미지를 직접 검토합니다. 출력은 결정론적이며 동일 입력으로 SHA-256이 바뀌지 않습니다.

외부 구현과의 추가 교차 검증:

```powershell
node assets/architecture-blueprints/cross-check-nbt.mjs
# 다른 설치의 reference parser를 이용할 경우 node_modules 경로를 첫 인수로 지정
```

교차 검증만 기존 `yuumi-core/build/integration-client/node_modules`의 `prismarine-nbt`와 `prismarine-block`을 읽습니다. 독립 파서로 NBT 전체 트리와 모든 팔레트 상태 ID를 비교하며 결과를 `review/independent-parser-report.json`에 남깁니다. 일반 파이프라인에는 이 캐시 의존성이 없습니다.

## 편집 구조

- `designs/hub.mjs`: 대공방·키오스크, 고정 시설·NPC 위치, 네 방향 접근 경로.
- `designs/dungeons.mjs`: 6구역 공간 구성, 테마별 입체 부재·천장 조형, 계단·기믹 공간, 원형 장미창.
- `lib/voxel.mjs`, `lib/registry.mjs`, `lib/nbt.mjs`: 조밀 셀 격자, 합법적인 블록 상태, 결정론적 Gzip NBT 직렬화·독립 재파싱.
- `verify-structures.mjs`: 디자인 모듈을 불러오지 않는 독립 규격 검사, 플레이어 충돌 검사와 연결 그래프.
- `lib/render.mjs`, `lib/raster.mjs`: 실제 NBT 기반 컬러 아이소메트릭·평면·종단면 PNG 렌더.

모든 빈 셀을 `minecraft:air`로 기록합니다. 전체 20종은 704,299셀입니다. 계단·담장·울타리·기둥 등의 속성은 실제 1.21.4 레지스트리에 존재하는 값만 사용합니다. 일반 책장·제작대에는 존재하지 않는 `facing`을 넣지 않습니다. 블록 엔티티의 동적 데이터와 엔티티는 포함하지 않습니다.

`data/`의 블록 상태·충돌 AABB는 기존 minecraft-data 3.119.0의 1.21.4 자료를 고정한 것입니다. 출처는 `data/provenance.json`, 원본의 MIT 라이선스 선언은 보존한 `data/UPSTREAM-README.md`의 License 절에 있습니다. 색상 렌더는 본 작업의 도식 표현이며 Minecraft 텍스처를 복제하지 않습니다.

## 최종 파일과 좌표 계약

| 파일 | 크기 `[W,H,L]` | 원점 `[X,Y,Z]` |
|---|---|---|
| `hub_grand_utility_hall.nbt` | `[29,26,25]` | `[-36,64,-44]` |
| `hub_guidebook_kiosk.nbt` | `[7,9,7]` | `[5,64,5]` |
| `ch{1,2,3}_zone1.nbt` | `[31,24,28]` | `[cx-15,60,-28]` |
| `ch{1,2,3}_zone2.nbt` | `[39,28,38]` | `[cx-19,60,-66]` |
| `ch{1,2,3}_zone3.nbt` | `[31,28,42]` | `[cx-15,60,-108]` |
| `ch{1,2,3}_zone4.nbt` | `[37,28,36]` | `[cx-18,60,-144]` |
| `ch{1,2,3}_zone5.nbt` | `[33,26,32]` | `[cx-16,60,-176]` |
| `ch{1,2,3}_zone6.nbt` | `[45,32,45]` | `[cx-22,60,-221]` |

`cx = slot * 1024`. 회전·미러링 없이 배치합니다. Manifest의 던전 `origin[0]`과 `anchors[].world[0]`는 `cx=0` 기준이며 실제 슬롯 배치 시 X에만 cx를 더합니다. 허브는 절대 좌표입니다.

`manifest.structures[]`는 파일·크기·원점·바이트 수·SHA-256·블록 수·팔레트별 수량·접속면·기능 앵커·예약 AIR·검수 경로를 담습니다. 앵커의 `kind: block`은 블록 셀, `kind: entityFeet`는 캐릭터 발 위치입니다. 앵커의 `world`는 `origin + local`이며 임의로 Y에 0.5를 더하지 않습니다.

- 던전 주 바닥 블록: local Y6 / world Y66. 캐릭터 발: world Y67.
- 발코니 바닥: local Y14 / world Y74. 원거리 몹 발: world Y75.
- 화톳불 쉼터: local Y7 / world Y67 바닥. 체크포인트 발: world Y68, Z−138. 불은 중심 스폰 옆의 분리된 화로에 둡니다.
- 성문: world Z−28/−66/−108/−144/−176, X=cx±4, AIR Y67..74. 다음 모듈 접속면은 각각 한 칸 북쪽입니다. 서로 겹치거나 한 칸 비우지 않습니다.
- Zone1 남벽, Zone6 북벽은 닫혀 있습니다. 파티 시작점은 내부 `(cx+0.5,67,-6.5)`입니다.
- 보스 엄폐 기둥: X=cx±10, Z−208/−190, 불투명 코어 Y67..82. 보스와 귀환 발 위치는 각각 Z−209/−216, Y67입니다.
- 퍼즐 기단 상부 local Y7..10과 허브 NPC·키오스크 홀로그램 공간은 동적 배치를 위해 비워 둡니다.
- 시계판 배경 블록은 local X3/X9 또는 Z3/Z9, 실제 외측 부착 평면은 X3/X10 또는 Z3/Z10입니다. `clockMounts`의 중심·평면은 모두 local 좌표이며 원점을 더하고 면 법선 방향으로 표시 객체의 두께만큼 조정합니다.
- **사용자 확정 예외:** 수정 성당의 장미창은 원형 지름 19를 유지하도록 local Y11..29로 확장했습니다. 지시서의 Y14..29에 맞춘 타원으로 압축하지 않습니다.

NPC·가이드북·3D 시계판·전투 몹·퍼즐 객체·룬 성문·보물 내용·상호작용은 Stream 03이 manifest 좌표에 부착합니다. NBT에는 닫힌 관문이나 실행 명령 블록을 넣지 않습니다.

## 검수 산출물

- `review/hub-utility-hall-sheet.png`: 대공방 외관/절개, 기능 평면, 키오스크, 발광 블록 분포 및 연동 좌표.
- `review/megadungeon-ch1-ch2-ch3-cutaways.png`: 18모듈 전체 아이소메트릭, 테마별 221블록 조립 종단면.
- `review/hub_*.png`, `review/ch*_zone*.png`: 개별 모듈 확대 검수 이미지.
- `review/verification-report.json`: 20종·15개 이음새·기능·충돌 동선 검사 결과.
- `review/validator-tests.json`: 검증기에 결함을 주입한 부정 테스트 결과.
- `review/reproducibility-report.json`: 재빌드한 NBT 20종과 manifest의 바이트 단위 동일성.
- `review/render-evidence.json`: 이미지 생성에 사용한 NBT 해시와 절개 방식.
- `review/visual-review.md`: 실제 관찰·수정과 미검증 항목 기록.

아이소메트릭은 내부 검사를 위해 가까운 남·동 벽과 지붕 일부를 절개합니다. 종단면은 중앙 절단면 뒤에 어둡게 처리한 서측 입면을 겹쳐 천장·회랑을 함께 표시합니다. 발광 도식은 광원 위치를 보여 주며 실제 Minecraft 광량 전파 시뮬레이션이 아닙니다.

검증기는 0.6×1.8블록 캐릭터 부피, 최대 0.6블록 단차, 0.5블록 수평 표본과 이동 중 충돌 검사를 사용합니다. 전투 동작이나 서버 틱을 재현하지 않습니다. 실제 Paper 스탬핑·유체 틱·몹 AI·성문 애니메이션·게임 조명은 Stream 03 통합 검수 대상이며, 이 작업에서는 서버나 통합 빌드를 실행하지 않습니다.
