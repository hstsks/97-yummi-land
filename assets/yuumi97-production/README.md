# 유미의 97랜드 v3.0 에셋 파이프라인

현재 v3.1 랜드마크 6종의 재생성·검수 명령 및 장착 계약은 [README_V31.md](README_V31.md)를 사용한다. 아래는 보존된 v3.0 납품 기록이다.

Minecraft Java 1.21.4 / pack_format 46. 원본은 이름 붙은 복셀과 픽셀 도트를 생성하는 sources/*.mjs이며, 납품은 resourcepack-gen/overrides 내부 JSON·PNG다. Blender 파일은 납품 JSON을 다시 읽어 만든 편집·검수 사본이다.

## 고정 납품 계약

| 구분 | 기존 | 신규 | 총수 |
| --- | ---: | ---: | ---: |
| 3D 모델 및 prop 아틀라스 | 9 | 20 | 29 |
| 32×32 RGBA8 아이콘 | 27 | 30 | 57 |
| GUI 2D / 월드 3D selector | 3 | 15 | 18 |
| override item definition | 3 | 20 | 23 |
| blocks 아틀라스 single 등록 | 9 | 20 | 29 |

총 override 에셋은 157파일이다. 순수 2D 신규 15종은 PNG만 공급하며 models/item과 기본 item definition 등록은 SOL의 PackBuilder 작업 소관이다. 기존 6종의 기본 item pointer도 기존 PackBuilder 후처리를 유지한다. 기존 hat_yuumi_ears와 plushie_yuumi에 신규 2D 아이콘을 추가하지 않는다.

sources/asset-contract.mjs가 확정 ID·분류·크기·표시 분기를 정의한다. 실행 중인 Java 소스를 파싱하지 않으므로 SOL의 동시 편집에 영향을 받지 않는다.

## 안전한 재생성

병행 작업 중에는 반드시 다음 명령만 실행한다. -Build 스위치를 붙이지 않는다.

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File E:\MinGem\assets\yuumi97-production\regenerate.ps1
~~~

생성 순서: statue → casino → relics → environment-v3 → housing-v3 → class-weapons → icons → item-definitions → validate-assets. 기존 에셋의 제작 전 해시는 sources/v2-baseline.json에 보존하며 실제 보존본은 backups/v3-start-20261115-185351에 있다. 새 검수 기록은 review/static-validation.json과 review/icons/validation.json에 남는다.

쓰기 허용 범위는 assets/yuumi97-production와 resourcepack-gen/overrides뿐이다. yuumi-core, PackBuilder.java, 루트 scripts, server 파일의 수정·빌드·패키징은 수행하지 않는다. 단독 PackBuilder 실행도 서버 출력물을 갱신하므로 이번 에셋 제작에서는 실행하지 않는다.

## 제작 소스

- environment-v3.mjs: 캡슐 머신·증권 보드·봉인 오벨리스크·축복 제단·연회 가마솥. 128px 아틀라스.
- housing-v3.mjs: 캣타워·코타츠·네온 간판은 128px, 셰프 모자·증권 탑햇은 64px.
- class-weapons.mjs: 10대 직업 성물. 64px 아틀라스. 그립 기준점과 중립 표시공간 목표는 class-weapons-anchors.json에 기록한다.
- icons.mjs / icons-v3.mjs: 기존 27종을 보존하고 30종을 추가한 32px 픽셀 원본. 모든 PNG는 RGBA8, 이진 알파, 투명 외곽 여백을 가진다.
- item-definitions.mjs: 18개 GUI selector, 5개 신규 환경 pointer, 정확한 29개 prop atlas source를 생성한다.

원형과 곡선은 바닐라 cuboid 계단 단면으로 구성한다. 발광·유리는 팔레트와 표면 명암으로 표현하며 별도 셰이더나 모드에 의존하지 않는다. 배치형 신규 모델은 Y=0 바닥과 중심 [8,0,8], 정면 -Z를 따른다. 가구의 45도 배치는 외부 ItemDisplay 회전이며 요소 회전 규칙과 구별한다.

## Blender 및 픽셀 검수

tools/render-v3.ps1은 준비와 저장본 재개방을 분리하고 모델별 렌더를 직렬 실행한다. 설치 Blender 5.2.1 LTS를 사용한다. sources/blend에 텍스처가 포함된 .blend를 저장하고 새 Blender 프로세스로 다시 연다. review/models/<id>에 4방향 렌더를, review/contexts/<id>에 문맥 지그와 수치 기록을 보존한다. 도구의 CLI 인수와 상태는 스크립트 자체를 참조한다.

review/icons의 전체 보드는 57종, icons-v3-contact.png는 신규 30종이다. 밝은/어두운 바탕의 32px, nearest 확대 및 16px 축소로 실루엣을 점검한다. 작은 룬과 숫자 디테일은 32px에서 판독하며 16px에서는 실루엣·색으로 구별한다. 생성기는 시각 검수 완료를 자동 선언하지 않는다.

실제 관찰 및 수정 기록은 review/v3-visual-review.md, 최종 수량·증거·검증 범위는 review/v3-delivery.json에 기록한다. Blender의 머리/손 지그는 display 변환을 비교하는 근사 검수이며 실제 Minecraft 클라이언트의 장착·사용·리소스 로딩 검증을 대신하지 않는다.

실행 확인한 검수 명령은 다음과 같다. GPU 사용 시에도 스크립트가 모델을 직렬 처리한다. AUTO는 이번 환경에서 RTX 3070 / OPTIX를 선택했다.

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File E:\MinGem\assets\yuumi97-production\tools\render-v3.ps1 -BlenderPath 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' -Device AUTO
& 'E:\MinGem\runtime\jdk-21\bin\java.exe' '-Djava.awt.headless=true' 'E:\MinGem\assets\yuumi97-production\tools\ReviewBoardV3.java' 'E:\MinGem\assets\yuumi97-production'
node E:\MinGem\assets\yuumi97-production\tools\verify-v3-delivery.mjs
~~~

최종 검증은 PASS다. overview는 review/models-v3-overview.png, 신규 아이콘 시트는 review/icons/icons-v3-contact.png, 그룹 시트는 review/environment-v3.png·housing-v3.png·weapons-v3.png에서 확인한다. 개별 모델의 contact-sheet.png와 개별 문맥의 context-sheet.png도 보존한다.

## 통합 인계

SOL은 Java의 확정 ID, BOW/HEAD 등 실제 아이템 종류와 override를 함께 패키징한다. 팩 생성 후 서버 캐시 갱신·클라이언트 재접속·실제 손과 머리 착용·분양지 배치를 별도로 확인해야 한다. 이 작업은 서버 진행도나 플레이어 데이터를 변경하지 않는다. 기존 tools/verify-pack.ps1은 v2 빌드/JAR까지 검사하는 레거시 도구이므로 이번 에셋 검증에는 사용하지 않는다.
