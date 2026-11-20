# 유미의 97랜드 v3.1

v3.0 데이터와 현장 방문 동선을 유지하면서 실행기 인코딩, 축제 광장, 카지노 궁전 외관, 수련장과 NPC 클릭 판정을 개선합니다.

- 소스 수정: `yuumi-core/**`, `START_SERVER.bat`, `scripts/*.ps1`.
- Astra 전담 경로 `assets/yuumi97-production/**`, `resourcepack-gen/overrides/**`는 읽기 전용입니다.
- `hub_v3_1.built`는 공공 마이그레이션 성공 후 저장합니다. 소유 필지는 전체 높이의 블록·보관함·설치 엔티티를 보호합니다.
- DPS는 플레이어와 허수아비 조합별 최근 5초 피해 합계 / 5입니다. 실제 체력 감소와 전투 보상은 차단하면서 RPG 표식·룬·흡혈은 유지합니다.
- 공격자에게만 보이는 임시 DPS 홀로그램으로 AuraSkills 액션바와 함께 사용할 수 있습니다. 마지막 타격 후 5초, 퇴장·재건축·종료 시 정리합니다.
- 허수아비는 투사체 적중을 허용하고 전용 팀의 충돌 금지·넉백 저항·고정 앵커로 밀림을 막습니다.
- 모든 NPC 고양이에 Interaction을 연결하며 두 클릭 이벤트를 같은 역할 라우터로 처리합니다.

## 빌드와 검증

Windows PowerShell 5.1에서 `scripts/build-all.ps1`, `scripts/verify-boot.ps1`을 실행합니다. 저장소의 JDK 21·Gradle과 `runtime/gradle-user-home`을 사용합니다. 부팅 검증은 v3.0 검사와 `V31_RUNTIME_CHECKS PASS` 모두를 요구하며 오류·시간 초과·강제 종료를 실패로 반환합니다.

검증기는 BlueMap이 설치되어 있으면 비동기 리소스 로딩 완료까지 기다립니다. PS1 파일은 UTF-8 BOM으로 저장하고, JVM 콘솔에 보내는 명령 스트림은 BOM 없는 UTF-8을 사용합니다.

`yuumi-core`에서 포터블 Gradle의 `check`는 결정적 계산 검사를 실행합니다. `v31Fixture`는 `yuumi-core/build/server-integration` 전용 테스트 데이터를 준비합니다. 테스트 전용 무료 변경·짧은 쿨타임·확정 룬 발동 설정은 실제 서버에 적용하지 않습니다.

프로토콜 검사는 `src/test/integration/v31-client.cjs`를 사용합니다. 기존 테스트 클라이언트의 Mineflayer와 vec3를 `NODE_PATH=build/integration-client/node_modules`로 연결하고, 별도 포트 25567의 테스트 서버에서 실행합니다. 이 스크립트는 운영 서버 포트에 접속하지 않습니다.

실제 실행 결과와 백업·로그 근거는 `build/verification-v31/summary.md`에 기록합니다. 프로토콜 검증은 그래픽 렌더링 검수와 구분합니다.
