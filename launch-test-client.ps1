param(
    [ValidateSet("1.21.1", "1.21.4")]
    [string]$Version = ""
)

if (-not $Version) {
    Write-Host "========================================================" -ForegroundColor Cyan
    Write-Host "  Minecraft MCP Test Client Launcher" -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Cyan
    Write-Host "  1) Minecraft 1.21.1 (Fabric)"
    Write-Host "  2) Minecraft 1.21.4 (Fabric)"
    $choice = Read-Host "Waehle eine Version [1/2, Standard: 1]"
    if ($choice -eq "2") { $Version = "1.21.4" } else { $Version = "1.21.1" }
}

Set-Location $Version
& .\launch-test-client.ps1
