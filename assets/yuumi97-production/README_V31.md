# v3.1 랜드마크 납품 및 SOL 통합 인계

이번 승인 범위는 랜드마크 6종이다. 확장된 지시서의 NBT 건축물 9종은 이 납품에 포함하지 않는다.

## 에셋 계약

전체 override: 모델 JSON 35 + prop PNG 35 + 기존 32px 아이콘 57 + GUI JSON 18 + item definition 29 + blocks atlas 1 = **175파일**.
기존 v3.0의 atlas 제외 156파일은 `sources/v3-baseline.json` SHA256과 동일해야 한다. 기존 v3 검수 보고서와 200개 증거 파일도 보존한다.

신규 6종은 128×128 RGBA8, 정면 −Z, 직접 `minecraft:model` item definition이다. GUI 아이콘/selector는 추가하지 않는다. 권위 있는 목록은 `sources/asset-contract.mjs`, 장착 계약은 `sources/landmarks-v31-mounts.json`이다. 이전 v3 20종/아이콘 30종 묶음은 유지한다.

| 모델 | 장착 | 앵커(모델 단위) | 기준 스케일 |
| --- | --- | --- | ---: |
| prop_casino_marquee | wall | [8,8,8], 후면 Z=8 | 2.7 |
| prop_casino_chandelier | ceiling | [8,24,8], 최하단 Y=−4 | 2.3 |
| prop_dealer_throne | floor | [8,0,8] | 1.5 |
| prop_plaza_clock_face | wall | [8,8,8], 후면 Z=8 | 2.0 |
| prop_training_dummy | floor | [8,0,8] | 1.35 |
| prop_sakura_lantern_arch | gate | [8,20,8] | 2.0 |

시계는 정적인 10:10 바늘이다. 초롱 현판은 대문 위에 부착하는 장식이며 전체 대문 구조가 아니다. 발광은 밝은 색의 표현이며 자체 발광 shader나 ItemDisplay Brightness를 설정하지 않는다.

## 안전한 재생성·검수

쓰기 범위는 이 production 디렉터리 및 `resourcepack-gen/overrides`뿐이다. `-Build`, 전체 빌드, 단독 PackBuilder, 서버 실행을 사용하지 않는다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File E:\MinGem\assets\yuumi97-production\regenerate.ps1
$landmarkIds = @('prop_casino_marquee','prop_casino_chandelier','prop_dealer_throne','prop_plaza_clock_face','prop_training_dummy','prop_sakura_lantern_arch')
& E:\MinGem\assets\yuumi97-production\tools\render-v3.ps1 -ModelIds $landmarkIds -Device AUTO -BatchName v31
& E:\MinGem\runtime\jdk-21\bin\java.exe '-Djava.awt.headless=true' E:\MinGem\assets\yuumi97-production\tools\ReviewBoardV31.java E:\MinGem\assets\yuumi97-production
node E:\MinGem\assets\yuumi97-production\tools\verify-v31-delivery.mjs
```

이 Java 명령은 production 내부의 이미지 시트 조합기만 실행한다. PackBuilder나 플러그인을 빌드하지 않는다. v3 전용 verifier와 보드는 역사적 납품용으로 보존하며 현재 납품에는 위 v31 명령을 사용한다.

`regenerate.ps1`은 기존 생성기를 유지하고 `landmarks-v31.mjs`를 item definition 생성 전에 실행한다. 정적 결과는 `review/static-validation-v31.json`에 기록한다. 렌더러는 기존 GPU 잠금을 공유하며 신규 6종의 준비 → 저장 → 모델별 새 Blender 프로세스 재개방을 직렬 실행한다. 각 `.blend`는 이름 붙은 복셀과 패킹 텍스처를 포함한다.

검수 결과는 24개 다각도 이미지, 장착 지그 6개 및 딜러 가림 참고 이미지 1개, 마키/시계 16m·32m 거리 이미지 4개다. 필수 `review/landmarks-v31.png`는 6행×3열 정면/측면/3/4 시트이며 입력 이미지 누락 시 실패한다. 거리 검수는 640×640, 70° FOV, 표의 스케일을 적용한 Blender 원근 이미지다.

관찰 기록은 `review/v31-visual-review.md`, 최종 해시·검증 범위는 `review/v31-delivery.json`에 남긴다. 재생성으로 형상이 바뀌면 관련 `.blend`와 이미지도 다시 만들고 관찰 기록을 갱신해야 한다.

## SOL 배치 계약

현재 마운터는 `ItemDisplayTransform.NONE`이며 모델 [8,8,8]이 엔티티 원점이다. `display.fixed`로 월드 위치를 보정하지 않는다. 외부 회전 R, 균일 스케일 s에서 앵커의 월드 오프셋은 `R × (anchor − [8,8,8]) × s/16`이다. 원하는 부착 위치에서 이 오프셋을 빼서 엔티티 위치를 정한다.

- 바닥형: 바닥의 월드 Y = entityY − s/2. 엔티티 Y를 왕좌는 바닥 Y+0.75, 허수아비는 바닥 Y+0.675에 놓는다.
- 샹들리에: 행거 월드 Y = entityY +2.3, 최하단 = entityY −1.725.
- 현판: 고정부 월드 Y = entityY +1.5.
- 벽걸이: 후면 부착 중심과 엔티티 위치가 일치한다. −Z 정면이 벽 바깥을 향하도록 yaw와 벽 두께를 확인한다.

이 문서는 Java나 건축물 배치를 변경하지 않는다. 실제 팩 로딩, SOL의 좌표/yaw, 배치 지형과의 충돌, 딜러 엔티티·히트박스, 야간 밝기는 Minecraft 통합 검증 대상이다. Blender 지그는 실제 게임 검증을 대체하지 않는다.
