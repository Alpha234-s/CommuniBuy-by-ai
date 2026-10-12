@echo off
setlocal
cd /D "%~dp0"
set WWWROOT=%~dp0
@rem No kiosk, there is no need to stop existing process.
::taskkill /IM msedge.exe /F /T 2>NUL
::timeout /T 1 /NOBREAK >NUL
cd /D "C:\Program Files (x86)\Microsoft\Edge\Application\"
start "msedge - app" "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --args ^
--disable-web-security --allow-file-access-from-files --explicitly-allowed-ports=10080 ^
--user-agent="Mozilla/5.0 (Linux; Android 15; MOBILE Build/AP3A.250617.008; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/146.0.7680.178 Mobile Safari/537.36 XWEB/1460249 MMWEBSDK/20250802 MMWEBID/191 MicroMessenger/8.0.62.2900(0x28003EA0) WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64" ^
--no-first-run --incognito --disable-infobars ^
--app="%WWWROOT%index.html"
timeout /T 1 /NOBREAK >NUL
cd /D "%~dp0"
endlocal
exit /B 0
