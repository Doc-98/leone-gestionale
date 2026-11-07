@echo off
rem Doppio clic per avviare il gestionale in locale. Accetta le stesse opzioni dello script, es.: avvia.cmd -Locale
rem «Bypass» vale solo per questo script: non cambia le impostazioni di sicurezza del computer.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0avvia.ps1" %*
