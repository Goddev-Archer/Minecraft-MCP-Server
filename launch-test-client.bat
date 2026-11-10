@echo off
title Minecraft MCP Test Client Launcher
echo ========================================================
echo   Minecraft MCP Test Client Launcher
echo ========================================================
echo   [1] Minecraft 1.21.1 (Fabric)
echo   [2] Minecraft 1.21.4 (Fabric)
echo ========================================================
set /p choice="Waehle eine Version (1 oder 2) [Standard: 1]: "
if "%choice%"=="2" (
    cd 1.21.4
    call launch-test-client.bat
) else (
    cd 1.21.1
    call launch-test-client.bat
)
