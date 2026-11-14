# 콘솔 속도와 GPS 주행 시뮬레이션

PC에서 명시적으로 실행하는 에뮬레이터 시연 보조 도구입니다. 실제 차량용 기능이나 실제 위치가 아닙니다.

## 제출 release APK에서 사용

```powershell
adb forward tcp:18088 tcp:8088
.\Start-GpsSimulation.ps1
```

기본 모의 경로는 양재역(37.48465,127.03415)에서 강남역(37.49795,127.02762)까지입니다. 앱에서도 강남역을 목적지로 선택합니다. 브라우저에서 <http://127.0.0.1:18088/> 조작 콘솔을 열어 활성 상태로 두고 D단과 속도를 공급합니다. GPS가 도로 경로를 따라 이동합니다. P/N단이나 비활성 콘솔에서는 위치를 유지하고 R단은 진행 경로를 되짚습니다. 경로 끝에서는 속도를0으로 줄이고 P단을 선택합니다.

```powershell
.\Start-GpsSimulation.ps1 -Origin "37.48465,127.03415" -Destination "37.49795,127.02762"
.\Stop-GpsSimulation.ps1
```

이 모드는 앱 저장값을 읽거나 쓰지 않습니다. 목적지를 바꾸면 도구의 좌표도 바꿔야 합니다. 앱의 기록 중지 설정과 PC GPS 공급은 별개이며 공급을 끝내려면 Stop 스크립트를 실행합니다. 도구가 차속·기어를 변경하지는 않습니다.

## 이전 debug 앱 추적 모드

`Start-GpsSimulation.ps1 -FollowDebugApp`은 debug 앱의 현재 위치·목적지·기록 중지 값을 읽어 추적하는 기존 모드입니다. `run-as`가 허용되지 않는 release APK에서는 사용할 수 없습니다.

## 환경과 테스트

Python3 표준 라이브러리, 사용자 Android SDK/ADB, emulator-5554를 사용합니다. 경로는 OSRM 공개 데모 서버에서 가져오므로 인터넷과 서버 응답이 필요합니다. `python -m unittest test_console_gps_sim.py`로 좌표 범위·속도·기어·경로 끝 처리를 검사합니다. 로그는 `%TEMP%/drivemate-console-gps.log`입니다.
