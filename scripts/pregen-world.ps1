param(
    [int]$Radius = 2500
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  🌍 [유미의 97랜드] 사전 월드 생성 안내 (Chunky)" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "서버가 켜진 상태에서 서버 콘솔창에 아래 명령어를 순서대로 입력하면"
Write-Host "지인들이 탐험할 때 지형 생성 렉이 완벽하게 사라집니다:`n"
Write-Host "  chunky world world" -ForegroundColor Green
Write-Host "  chunky center 0 0" -ForegroundColor Green
Write-Host "  chunky radius $Radius" -ForegroundColor Green
Write-Host "  chunky start" -ForegroundColor Green
Write-Host "`n진행률 확인: chunky progress  |  일시정지: chunky pause" -ForegroundColor Gray
