@echo off
:inputdir
set /p a="please input your fold(like F: or F:\fold1)"
if not exist %a% (
   echo "path is invalid,please input an available path"
   goto inputdir
   )

:lintanddelete
if not exist %a%\gen md %a%\gen
if not exist %a%\AndroidManifest.xml (
   color C7
   echo Not having AndroidManifest.xml,just exit
)
aapt package -f -m -J %a%\gen -S %a%\res -I %ANDROID_JAR% -M %a%\AndroidManifest.xml
echo "begin lint"
call cslint.bat "%a%"
:: copy line that contains "drawable"
find "res\drawable" 1.txt >UnusedDrawables.txt
echo "%a%\"
for /f "tokens=1 delims=:" %%i in (UnusedDrawables.txt) do  (
         echo "%%i"
		 set filePath = %a%\%%i
		 if exist %a%\%%i ( 
		     del %a%\%%i
		 )	else ( 
		     echo "%a%\%%i not exist"
			 )
			 pause
)
CHOICE /C:YN /M "Continue:Y  exit: N" 
echo "%errorlevel%"
if %ERRORLEVEL% equ 1 (
    echo "lintanddelete "
	goto lintanddelete
	)
if %ERRORLEVEL% equ 2 (
	echo "done"
)
pause

