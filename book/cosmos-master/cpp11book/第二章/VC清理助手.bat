@echo off

echo ɾ��VC�����������ʱ�ļ�����

echo ɾ����ʼ...

for /r . %%c in (.) do @if exist "%%c/ipch" rd /S /Q "%%c/ipch"

for /r . %%c in (.) do @if exist "%%c/Debug" rd /S /Q "%%c/Debug"

for /r . %%c in (.) do @if exist "%%c/Release" rd /S /Q "%%c/Release"

for /r . %%c in (*.bsc *.aps *.clw *.ncb *.plg *.positions *.WW *.user *.sdf *.psess *.suo) do del "%%c"

for /r . %%c in ("*VA_C__Documents and Settings_Administrator_Local Settings_Application Data_VisualAssist_vc6_history_") do del "%%c"

echo ɾ�����...