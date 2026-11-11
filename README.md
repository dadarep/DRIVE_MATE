# DRIVE MATE · Pleos prototype

지도 중심 차량 코칭 앱. 기존 정원 캐릭터와 주행 회고 데이터를 유지한다.

## 실행

JDK 17, Android SDK 36, Pleos Connect API 34 에뮬레이터가 필요하다.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.drivemate.app/.MainActivity
```

## 현재 동작

- AAOS 차량 속도·기어 + GPS 위치를 수신한다.
- Pleos Vehicle 2.3.2의 공개 Capability/Get API로 브레이크 눌림 상태, 방향지시등, 조향각을 읽는다. 차량 제어 API는 호출하지 않는다.
- 약 1초 차속 변화로 가감속을 관찰하고, 수신 가능한 브레이크·조향·방향지시등·내비 제한속도를 함께 사용한다. 신호 누락은 0이 아닌 미수신으로 다룬다.
- 한국어 Pleos TextToSpeech 3.3.1 ON_DEVICE 모드로 짧은 코칭을 재생한다. 음성 버튼으로 켜기/끄기 가능하다.
- 목적지 선택, Photon 장소 검색, 좌표 입력, 지도 길게 누르기를 지원한다. 입력과 상세 회고는 P단·정지·UX 제한 해제 상태에서만 열린다.
- NaviHelper 3.0.5로 선택한 목적지를 기본 Pleos 내비게이션에 전달하고 목적지·TBT·잔여 거리/시간·제한속도 콜백을 받는다.
- 지도는 OpenStreetMap 타일을 사용하며, 자체 지도 위 파란 경로는 OSRM 도로 경로 미리보기다. 직선을 실제 도로 경로처럼 표시하지 않는다. 네트워크 실패 시 목적지 핀과 오류 상태를 표시한다.
- 기존 주행 회고, 목표 씨앗/잎 데이터를 유지한다.

## 확인된 환경

`DriveMate_Pleos_API34`, Vehicle Service 2.3.22에서 다음 값을 직접 수신했다:

| 데이터 | 실제 응답 |
|---|---|
| 차속 / 기어 | 0.0 m/s / P |
| 브레이크 | false |
| 방향지시등 | OFF |
| 조향각 | 0.0° (지원 범위 -470..500°) |
| 위치 | GPS 좌표 업데이트 |
| 목적지 | 42dot 목적지 콜백 |
| 회전 안내 | Pleos TBT 응답 |
| 음성 | ON_DEVICE 한국어(ko-KR) 초기화 및 재생 시작/완료 콜백 확인 |

Android `CAR_EXTERIOR_LIGHTS`·`READ_CAR_STEERING`의 거부 결과는 Pleos 전용 공개 SDK의 지원 여부를 뜻하지 않는다. 두 경로의 권한은 다르다.

## 공개 자료

- https://github.com/Pleos-Playground/pleos-sample-vehicle-app
- https://github.com/Pleos-Playground/pleos-sample-navi-app
- https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
- https://github.com/komoot/photon
- https://project-osrm.org/
- https://operations.osmfoundation.org/policies/tiles/

SDK 공개 인터페이스와 초기화/해제·capability·callback 패턴을 참고해 앱에 맞게 구현했다. 다른 참가자의 비공개 코드나 비공개 API를 사용하지 않는다.

## 범위와 제한

코칭 임계값은 프로토타입 휴리스틱이며 안전운전 인증이나 사고 위험 판정이 아니다. 급감속은 주변 상황상 필요한 행동일 수 있다. 차선·앞차·보행자 감지 기능은 없다. 기본 Pleos 차량 화면과 중복되던 앱 내부 차량 시각화는 제거했고 지도·코칭이 앱 영역 전체를 사용한다.

## Pleos 조작 콘솔

에뮬레이터 실행 후 `adb forward tcp:18088 tcp:8088`을 실행하면 PC 브라우저에서 http://127.0.0.1:18088/ 로 접속할 수 있다. 페이지 접속 성공과 실제 차량 제어 브리지 연결 여부는 별개다. 에뮬레이터/ADB 재시작 후에는 포트 연결을 다시 실행한다.

지도/장소 검색/OSRM은 공개 온라인 서비스를 사용하는 개발용 구성으로 가용성 보장이 없다. 장소 검색어와 위치, 경로 탐색용 출발/도착 좌표는 각 서비스로 전송된다. OSM 타일은 화면에 보이는 것만 요청하고 디스크에 보관한다. 배포 전 상용 트래픽에 맞는 공급자·서비스 정책과 권한 승인을 검토해야 한다.

코칭 계산 단위 테스트 5개, APK 빌드, Android lintDebug가 통과했다(경고는 남아 있음). 주차 상태 실제 SDK 값, 목적지/TBT 연동, 도로 지도·OSRM 경로, Photon 장소 검색 결과, 한국어 음성 재생 완료를 확인했다. 가상 콘솔의 주행 제어 브리지가 503을 반환해 실제 가감속 신호를 주입하는 전체 주행 시나리오 검증은 남아 있다.
