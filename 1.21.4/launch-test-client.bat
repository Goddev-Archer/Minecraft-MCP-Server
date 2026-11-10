@echo off
title Minecraft MCP - Fabric Test Client (1.21.4)
echo ========================================================
echo   Starting Minecraft 1.21.4 Fabric Test Client...
echo   MCP Server will listen on http://127.0.0.1:25585
echo   In-Game Commands: /ai [frage], /mcp status, /mcp log
echo ========================================================
echo.
call gradlew.bat :fabric:runClient --no-daemon
pause
