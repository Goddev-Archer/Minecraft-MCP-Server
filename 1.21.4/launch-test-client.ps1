Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Starting Minecraft 1.21.4 Fabric Test Client..." -ForegroundColor Green
Write-Host "  MCP Server will listen on http://127.0.0.1:25585" -ForegroundColor Yellow
Write-Host "  In-Game Commands: /ai <frage>, /mcp status, /mcp log" -ForegroundColor White
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
& .\gradlew.bat :fabric:runClient
