# SOL 통합 계약 — 성물·VFX·18 몬스터

이 문서는 production 산출물의 연결 정보다. 코어·팩 빌더·서버는 이 작업에서 변경하거나 실행하지 않았다. 현재 상태의 최종 판정은 `review/delivery.json`을 참조한다.

## 리소스 식별자

`sources/asset-contract.mjs`가 단일 목록이다. 신규 61개 모두 `yuumi97:<id>` item definition으로 요청한다. `yuumi97:item/<id>`는 JSON 내부 모델 경로이며 ItemModel 키와 혼동하지 않는다.

4성물/가이드북은 `minecraft:display_context`가 `gui`일 때 `yuumi97:item/<id>_gui`, 나머지는 `yuumi97:item/<id>`로 분기한다. 길잡이·VFX·몬스터 57파트는 직접 모델이다. 몬스터 GUI 아이콘은 추가하지 않는다.

`assets/yuumi97/monster_rigs.json`은 ZIP 안에서 그대로 읽을 수 있다. production의 `sources/monster-rig-sockets.json`과 바이트가 같아야 한다. 12 일반/정예 몬스터는 body+arm, 6보스는 core+head+weapon이다. `boss_twin_archons`는 3본 한 세트를 두 런타임 개체가 공유한다.

## 좌표·본 조립

모델 단위는 1/16 블록이다. 정면 −Z, 위 +Y. 다음 기호를 사용한다.

- `g`: 런타임 몬스터의 바닥 월드 위치.
- `s`: 매니페스트의 baseScale. 소켓과 지오메트리에 각각 같은 스케일을 한 번 적용한다.
- `F=[8,0,8]`: root의 바닥 기준점.
- `O=[8,8,8]`: 자식 본 회전 원점.
- `d`: 매니페스트의 socket offset. **F에서 측정한 스케일 적용 전 모델 단위**.
- `B=Ry(180° − BukkitYaw)`: authored −Z를 Bukkit 방향에 맞추는 회전. Bukkit yaw 0은 +Z, 90은 −X다.
- 팔/무기 본의 `Q=Rx(−semanticPitch)`: 예비동작의 의미 pitch −60°는 실제 로컬 X +60°가 되어 앞쪽 팔/무기를 들어 올린다. 머리 본의 Q는 목 원점 주위의 별도 로컬 yaw이며 팔의 pitch를 상속하지 않는다.

```text
root vertex world  = g + B × s × (v − F) / 16
child vertex world = g + B × s × (d + Q × (v − O)) / 16
```

`ItemDisplayTransform.NONE` 기준으로 root display 위치는 `g + B*s*[0,8,0]/16`, 자식 display 위치는 `g+B*s*d/16`이다. 자식 회전은 `B*Q`다. 자식 소켓 위치에 자식 pitch를 적용하지 않는다. 자식은 +8 바닥 보정된 root display의 자식 좌표가 아니라 바닥 `g`에서 직접 계산한다. 잘못된 위치를 `display.fixed`로 보정하지 않는다.

독립 검산용 fixture:

```text
g=[0,0,0], s=1, BukkitYaw=0, semanticPitch=-60
d=[0,16,0], vertex=[8,8,-8]
expected pivot=[0,1,0]
expected vertex=[0,1.8660254037844386,0.5]
```

정적 검증은 yaw 0/90/180/270, scale 1/2, pitch −70/−65/−60/0/+55를 검사한다. Blender도 실제 조립된 본에서 pivot 오차 ≤0.01블록과 렌더 후 평가된 X각도를 검사한다. `.blend`의 frame 1/20/28/40에는 중립/−60/+55/중립 키를 저장했다. 렌더 프로세스에서는 액션 평가를 메모리에서만 해제해 명시한 검수 포즈를 정확히 적용한다.

## 성물과 VFX 배치

`hero-grip-anchors.json`은 중립 표시공간의 손잡이 기준점과 좌우 손 미러링을 기록한다. Minecraft 플레이어 상위 변환·카메라·무기 사용 애니메이션은 Blender 손 지그 범위 밖이다.

`model-review-contract.json`과 `skill-vfx-contract.json`을 함께 참고한다.

| 유형 | 로컬 기준 |
| --- | --- |
| 길잡이·성운 화살 | 피벗 [8,8,8], 화살촉 −Z, 꼬리 +Z |
| 황금/용암 검기 | XZ 수평, 칼날 전방 −Z |
| 암영 X베기·낙인 | 수직형, 정면 −Z |
| 거신 낙하검·천벌 성창 | 축 X=Z=8, 칼/창끝 Y=−14 |
| 비전/성역 바닥 마법진 | Y7.6..8.4, XZ−14..30, 중심 빈 공간 유지 |
| 조준 링 | Y7..9 수평 |
| 중력 특이점 | 구체 + X22.5° 강착 링 |
| 메테오 | 기본 진행 방향 −Y/−Z |
| 보물 어장 | 수면 Y8, 최하부 Y0으로 좁아짐 |
| 요리솥 | 다리 바닥 [8,0,8], 김 Y18..28 |

바닥 마법진은 원점 Y8 기준으로 아래쪽 0.025블록 두께가 있다. 실제 지면과 Z-fighting/관통이 없도록 런타임 중심 높이를 확인한다. 모든 광채는 복셀·팔레트이며 별도 emissive 셰이더나 모드가 필요하다고 가정하지 않는다. 적 경고용 #FF1133/#FF2A2A는 신규 아트에서 사용하지 않는다.

노바리온의 성운 날개는 alpha 200, 거울 추적자의 프리즘 검팔은 alpha 208의 전용 타일을 사용하며, 가장자리·골격은 불투명으로 유지한다. 실제 ItemDisplay의 부분 알파 합성·깊이 정렬은 클라이언트 통합 검증 대상이다.

## SOL에서 이어서 확인할 항목

1. pack_format 46 최종 ZIP에 96 prop atlas, 90 override item definition, monster_rigs.json이 포함되고 기존 PackBuilder의 기본 pointer와 정상 병합되는지.
2. 실제 클라이언트 리소스 로딩과 GUI/손/월드 분기, 좌우 손 그립, 길잡이 Bukkit yaw 방향.
3. 서버가 매니페스트 소켓·baseScale·pitch 부호를 그대로 적용하며 18종이 같은 위치에 조립되는지. AI 예비동작/타격/머리 추적의 보간·히트박스는 별도 확인.
4. 낮/밤 조명에서 VFX 인지성, 적 경고 영역과 색 분리, 투명 재질 정렬, 많은 ItemDisplay 동시 생성 시 성능.
5. 소환·이동·공격·사망 시 본 엔티티 생성/정리, 쌍집정관의 2개체 운용과 게임플레이.

위 항목은 실제 Minecraft 실행 전에는 통과로 표시하지 않는다.
