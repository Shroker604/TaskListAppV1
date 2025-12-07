@echo off
set "args=%*"
set "args=%args:"=%"
echo %args% >> "%~dp0events.txt"
