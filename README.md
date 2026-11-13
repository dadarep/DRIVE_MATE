# DRIVE MATE (v0.1.0)

낯선 길 앞에서 망설이는 운전자와 동행하고, 새싹을 키우는 재미로 안전운전 연습과 회고를 이어가게 돕는 Pleos Connect(AAOS) 차량용 앱 프로토타입입니다.

## 개발 및 실행 환경

- **플랫폼**: Android Automotive OS (Pleos Connect API 34 에뮬레이터 기준, `minSdk 28`, `targetSdk 34`, `compileSdk 36`)
- **언어 / 빌드**: Kotlin 2.1.20, JDK 17, Gradle 8.14.3
- **사용 SDK 및 라이브러리**:
  - Android Car API (`CarPropertyManager`, `CarUxRestrictionsManager`)
  - Pleos Vehicle SDK `2.3.2`
  - Pleos NaviHelper SDK `3.0.5`
  - Pleos TextToSpeech SDK `3.3.1` (`ON_DEVICE` 한국어 음성 안내)
  - Pleos SpeechToText SDK `3.3.1` (`ON_DEVICE` 선택형 음성 회고)
  - MapLibre Native Android `12.3.1` (OpenFreeMap 벡터 타일, Photon 장소 검색, OSRM 도로 경로 미리보기)

## 빌드 및 설치 방법

저장소의 `release/DRIVE_MATE-0.1.0-debug.apk`를 바로 설치하거나, 아래 명령으로 직접 빌드할 수 있습니다.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.drivemate.app/.MainActivity
```

## 주요 구현 기능

- **차량 신호 수집 및 주차 게이트 (`DriveRecorder`)**
  - `PERF_VEHICLE_SPEED`, `GEAR_SELECTION` 및 `CarUxRestrictionsManager`를 연동하여 주행/주차 상태를 판별합니다.
  - P단·속도 0.05m/s 미만·UX 제한 해제가 2초 연속 확인될 때만 상세 회고·목표 입력·설정 화면을 엽니다. 회고 도중 재출발 시 입력 화면을 즉시 닫습니다.
- **주행 관찰 및 급감속 후보 기록 (`CoachEngine`, `TripQuality`)**
  - 약 250ms 간격 차속 표본에서 연속 속도 감소량(1.5m/s 이상)과 감속도(3.0m/s² 이상)가 함께 충족될 때 돌아볼 후보로 기록합니다.
  - 신호 결측 구간은 계산에서 분리하며, 후보 0건과 확인 구간 부족을 구분해 안내합니다.
- **도착 한 줄 리마인더 (`TripReminder`, `CoachVoice`)**
  - 유효 주차 확정 후 안전벨트 미착용 경고(`CAR_SAFETY`), 급감속 후보, 급가속 관찰 여부를 바탕으로 다음 운전에서 기억할 한 문장을 화면과 한국어 TTS(`ON_DEVICE`)로 안내합니다.
- **선택형 짧은 음성 회고 (`VoiceReflection`, `VoiceReviewPolicy`)**
  - 음성 안내와 음성 회고를 모두 켠 경우, 안정 주행 조건 및 NaviHelper 회전 안내(TBT)·조향·제동·오디오 포커스 조건을 확인해 주행당 최대 1회 목표 회고를 묻습니다.
  - 주행 중 응답(`잘됐어` / `어려웠어` / `나중에`)은 임시 저장하며, 주차 확정 후 목표·주행 세션을 대조해 반영합니다.
- **목표 씨앗 저장 및 새싹(첫 잎) 성장 (`Garden`)**
  - 다음 연습 목표를 저장하면 씨앗이 심어지고, 이후 완료한 새 주행에서 해당 목표를 회고하면(`잘됐어요` / `어려웠어요` 모두 동일) 첫 잎이 지급됩니다.
  - 목표·회고·보상을 단일 JSON으로 원자 저장해 중복 보상을 방지합니다.
- **벡터 지도 및 경로 미리보기 (`RouteMap`, `DriveMapStyle`, `MapData`)**
  - MapLibre + OpenFreeMap 기반 주간/야간 벡터 지도와 OSRM 도로 경로 미리보기, 주행 방향(구면 방위각) 추적을 제공합니다.
- **사용자 통제 및 데이터 초기화 (`DataReset`)**
  - 주차 중 기록 일시중지/재개, 음성 안내 및 주행 중 음성 회고 개별 ON/OFF, 최근 기록 삭제 및 전체 데이터 초기화를 지원합니다.

## 에뮬레이터 시연 보조 도구

- `tools/demo-data`: 에뮬레이터 내 시연용 샘플 상태 로드 및 원본 복원 스크립트
- `tools/gps-simulation`: Pleos 조작 콘솔(`http://127.0.0.1:18088/`)의 차속·기어에 맞춰 도로 경로를 따라 에뮬레이터 GPS 좌표를 갱신하는 시뮬레이션 스크립트
