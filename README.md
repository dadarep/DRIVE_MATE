# DRIVE MATE

낯선 길 앞에서 망설이는 운전자와 동행하고, 새싹을 키우는 재미로 안전운전 연습과 회고를 이어가게 돕는 Pleos Connect 차량용 앱 프로토타입입니다.

## 제출 APK

- **서명된 release APK:** [`apk/TeamGreenlight_DRIVE_MATE_release.apk`](apk/TeamGreenlight_DRIVE_MATE_release.apk)
- 패키지: `com.drivemate.app` / 버전: `0.1.0` (`versionCode 1`)
- APK SHA-256: `369f7a48c5e994703d7d3c711859c701f6ec6d937b885a780f28234d8eb04cef`
- release 빌드이며 `debuggable=false`입니다. 서명 키와 비밀번호는 저장소에 포함하지 않습니다.
- `release/DRIVE_MATE-0.1.0-debug.apk`는 개발 당시의 이전 debug 빌드입니다. **심사·설치에는 위 apk/의 release 파일을 사용해 주세요.**

## 검증 환경

| 항목 | 사용 환경 |
|---|---|
| Pleos Connect 에뮬레이터 | **release_connect_v2.1.0**, Android Automotive OS 14 / API 34, x86_64 |
| 실제 실행 지문 | `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys` |
| 화면 규격 | **2560 × 1440** |
| Android SDK | minSdk 28 / targetSdk 34 / compileSdk 36 |
| 개발 도구 | Kotlin 2.1.20 / JDK 17 / Gradle 8.14.3 / Android Gradle Plugin 8.11.0 |
| Pleos SDK | Vehicle 2.3.2 / NaviHelper 3.0.5 / TextToSpeech 3.3.1 / SpeechToText 3.3.1 |
| 지도 | MapLibre Native Android 12.3.1 / OpenFreeMap / Photon / FOSSGIS OSRM |

지도·장소 검색·도로 경로 미리보기에는 인터넷 연결이 필요합니다. 에뮬레이터에서 경로를 보려면 GPS 좌표도 공급해야 합니다. 휴대폰이나 PC의 실제 위치를 자동으로 사용하는 앱은 아닙니다.

## 설치와 실행

Pleos Connect 에뮬레이터를 실행하고 다음 명령을 사용합니다. 아래 `--user 10`은 사용한 Pleos 이미지의 사용자 번호입니다.

```powershell
adb devices
adb install -g --user 10 apk/TeamGreenlight_DRIVE_MATE_release.apk
adb shell am start --user 10 -n com.drivemate.app/.MainActivity
```

- 화면에서 차속·위치·알림 권한을 확인합니다. 차속은 주행 기록에 필요하고 위치는 지도·경로 기능에 필요합니다.
- 기존 debug 앱과 제출 release APK는 서명이 다릅니다. 같은 패키지의 debug 앱에 덮어 설치할 수 없으므로 별도 테스트 에뮬레이터에서 설치하는 것을 권장합니다. 기존 앱을 제거하면 그 앱의 데이터도 삭제됩니다.
- 설치된 앱이 이번 제출 APK인지 `adb shell dumpsys package com.drivemate.app`의 버전으로 확인할 수 있습니다. 정확한 바이너리 식별은 위 SHA-256을 사용합니다.

## 에뮬레이터에서 시연하는 순서

1. P단·속도0 상태에서 앱이 **주차 중**으로 바뀔 때까지 기다립니다. P단·차속 절댓값0.05m/s 미만·플랫폼 UX 제한 해제가2초 연속 확인되어야 상세 입력이 열립니다.
2. 최초 설치 후에는 먼저 한 번 주행하고 주차합니다. 누적 주행이 1회 이상이면 `주행 회고` → `다음 목표 고르기`에서 연습 행동 하나를 저장할 수 있습니다. 씨앗과 저장한 목표가 표시됩니다.
3. 아래 GPS 보조 도구를 실행하고 앱에서 목적지를 **강남역**으로 고릅니다. 현재 위치와 도로 경로 미리보기를 확인합니다.
4. Pleos 조작 콘솔에서 D단과 속도를 공급합니다. 상세 회고·설정 입력은 닫히고 차량 신호를 기록합니다.
5. 속도를0으로 줄인 뒤 P단으로 바꿉니다. 주차 확인 후 `주행 회고`에서 지난 목표에 `어려웠어요` 또는 `잘됐어요`로 답합니다.
6. 잎1장을 받고, 다음 목표를 저장한 뒤 `정원 기록`에서 완료한 회고와 새 목표를 확인합니다.

### release APK용 GPS 시뮬레이션

Python 3, Android SDK의 ADB가 필요합니다. PC에서 아래 명령을 실행합니다.

```powershell
adb forward tcp:18088 tcp:8088
.	ools\gps-simulation\Start-GpsSimulation.ps1
```

- 조작 콘솔: <http://127.0.0.1:18088/>. 브라우저로 열어 활성 상태로 유지합니다.
- 기본 모의 경로는 **양재역 → 강남역**입니다. 앱에서도 강남역을 선택해야 같은 목적지의 경로를 볼 수 있습니다.
- 기본 모드는 앱의 비공개 데이터에 접근하지 않으므로 서명된 release APK에서 사용할 수 있습니다. 콘솔의 차속·기어에 따라 모의 GPS를 이동시키며 앱의 주행·보상 저장값을 직접 수정하지 않습니다.
- 목적지를 바꾸면 보조 도구의 `-Destination "위도,경도"`도 같이 바꿉니다. 기본 모드가 앱의 목적지 변경을 자동으로 읽는 것은 아닙니다.
- 앱의 기록 중지와 PC의 GPS 공급은 별개입니다. 보조 도구 종료: `.	ools\gps-simulation\Stop-GpsSimulation.ps1`.
- 자세한 사용법과 debug 앱 추적 모드는 [GPS 도구 설명](tools/gps-simulation/README.md)을 참조하세요. `tools/demo-data`의 샘플 데이터 로더는 debug 앱 전용이며 제출 release APK에서는 사용하지 않습니다.

## 현재 구현과 확인 범위

- 차량의 차속·기어와 `CarUxRestrictionsManager`를 사용한 기록 및 주차 게이트. 주행 중 상세 입력을 막고 재출발 시 입력 창을 닫습니다.
- 약250ms 차속 표본에서 유효100~750ms 간격, 속도 감소1.5m/s 이상과 감속도3m/s² 이상을 함께 만족할 때 급감속 **후보**를 기록합니다. 정확히250ms이면1.5m/s 감소는6m/s²에 해당합니다. 필요한 제동이나 법규 위반·과실을 판정하지 않습니다.
- 목표 저장 → 새 주행 후 목표 회고 → 잎 누적 → 다음 목표와 회고 이력 유지. 두 응답에 같은 보상을 주고 같은 목표ID의 중복 보상을 막습니다.
- 유효 주차 후 관찰된 정보에 따른 한 줄 리마인더. Safety 미착용 경고는 지원 차량에서만 사용하고 미수신을 착용 확인으로 해석하지 않습니다.
- Pleos ON_DEVICE TTS와 선택형 짧은 STT 회고. 기본 음성은 꺼져 있고 주행 중 질문은 조향·제동·방향지시등·회전 안내·오디오 상태 등의 보류 조건을 적용합니다.
- MapLibre 도로 지도, 장소 검색, 도로 경로 미리보기. 목적지 안내와 도로 상황 판단을 보장하는 내비게이션 제품이 아닙니다.
- 주차 중 기록 일시중지·재개, 최근 기록 삭제·전체 삭제, 음성 안내 및 음성 회고 선택.

화분 선택(잎3장), 정원 한 칸·다음 식물(잎5장), 출발 전 걱정 선택, 운전학원·가족 동행·해외 렌터카 연계, 카메라AI 회고는 **추후 계획**입니다. 현재 APK에서 사용할 수 있는 기능으로 제시하지 않습니다.

## 빌드와 검증

제출된 서명 APK를 바로 설치할 수 있습니다. 소스에서 빌드할 경우 JDK17과 Android SDK가 필요합니다.

```powershell
.\gradlew.bat --no-daemon assembleRelease testReleaseUnitTest lintRelease
```

서명 설정은 기본적으로 사용자 폴더의 `.android/drivemate-release-signing.properties`를 읽습니다. 다른 경로는 `DRIVEMATE_SIGNING_PROPERTIES` 환경변수로 지정합니다. 아래 항목을 자신의 키로 설정합니다. 이 파일과 키는 Git에 추가하지 않습니다.

```properties
storeFile=C:/private/your-release-key.jks
storePassword=YOUR_PRIVATE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_PRIVATE_PASSWORD
```

설정이 없으면 release 출력은 unsigned이며 설치 전에 자신의 키로 서명해야 합니다. 제출된 APK는 Team Greenlight의 별도 키로 서명되어 있습니다.

계측 테스트는 데이터를 삭제하므로 별도 테스트 기기에서만 실행합니다. 검증 당시 원본 AVD는 보존하고 read-only 에뮬레이터에서 실행했습니다.

```powershell
.\gradlew.bat -PdrivemateTestBuildType=release assembleReleaseAndroidTest
adb install -g --user 10 app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell am instrument --user 10 -w -e class com.drivemate.app.SettingsAndDeleteTest,com.drivemate.app.SttRecognitionTest com.drivemate.app.test/androidx.test.runner.AndroidJUnitRunner
python -m unittest discover -s tools/gps-simulation -p "test_*.py"
python -m unittest discover -s tools/demo-data -p "test_*.py"
```

검증 결과와 실제 release 실행 화면은 [검증 기록](docs/verification/README.md)에 정리합니다. 실차 권한·차종별 신호 지원·실제 마이크·주행 중 음성 적합성·장기 사용자 효과는 별도 검증 대상입니다. 앱 강제 중지나 전원 종료 중의 수집을 보장하지 않고, 미수집 구간을 추정 복원하지 않습니다.

## 지도와 데이터

주행·목표·회고는 기기 내부에 저장합니다. 지도 타일 요청, Photon 검색어와 검색 위치, OSRM 출발·도착 좌표는 외부 서비스로 전송됩니다. 오프라인 지도·서비스 수준 보장은 제공하지 않습니다. 출처와 라이선스는 앱 설정 및 `app/src/main/assets/licenses`에 표시합니다.
