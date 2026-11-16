# Pleos 연동 근거

확인일: 2026-11-16. 최신 공개 문서와 Maven에 게시된 버전을 기준으로 한다. 소스 코드의 API 이름은 문서의 Programming Guide와 API Reference에서 확인했으며 SDK 바이너리를 역공학하지 않았다.

| 구성요소 | 사용 버전 | 적용 |
| --- | --- | --- |
| Pleos Connect Emulator | 2.1.0, API 34, ARM64 | 공식 2.x 저장소 이미지 |
| SpeechToText | 3.3.1 | ON_DEVICE, initialize, ResultListener, request, stop, release |
| TextToSpeech | 3.3.1 | ON_DEVICE, initialize, EventListener, speak, stop, release |
| FusedLocation | 1.1.3 | isSupported, initialize, registerFusedLocationCallback, getLastKnownLocation, unregisterFusedLocationCallback, release |
| NaviHelper | 3.0.5 | initialize, addListener, requestRoute(RouteInfo), onRoutingCompleted, onError, removeListener, release |
| Vehicle | 2.3.2 문서 확인, 의존성 제외 | 운행 거리나 카메라 기반 감지 기능을 사용하지 않음 |
| Car API | API 34 환경, compile 35 optional android.car | CarUxRestrictionsManager를 통해 실제 설정 제한 확인 |

## 음성

[SpeechToText](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)와 [TextToSpeech](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)의 권한은 각각 `pleos.car.permission.STT_SERVICE`, `pleos.car.permission.TTS_SERVICE`다. Android `SpeechRecognizer`, 직접 `AudioRecord` 또는 휴대폰 음성 엔진을 fallback으로 사용하지 않는다.

ON_DEVICE 모드는 문서상 별도 client ID와 secret을 요구하지 않는다. HYBRID와 SERVER 모드는 앱 등록과 자격증명이 필요하므로 사용하지 않는다. 음성 서비스 자체가 제공되지 않거나 기기 내 엔진이 준비되지 않으면 오류를 표시한다. 미지원 호출어를 UI에서 동작한다고 안내하지 않는다. 텍스트가 확정된 후에만 서버에 전송한다. 부분 인식은 화면에만 표시한다.

실제 2.1.0 이미지의 권한 목록에서는 STT_SERVICE와 TTS_SERVICE가 `normal`, FUSED_LOCATION이 `dangerous`, NAVI_ROUTE_SEARCH가 `normal`이었다. 따라서 음성에는 일반 Android 마이크 위험 권한 대화상자가 나타나지 않는다. 녹음은 Pleos CaaS 서비스가 관리하며 차량 개인정보 설정과 에뮬레이터 마이크 입력을 별도로 확인해야 한다.

[서비스 제공 상태 표](https://document.pleos.ai/en/docs/pleos-only/create-my-project)는 STT, TTS, FusedLocation을 아직 제공하지 않는 것으로 표기하는 반면 개별 문서와 Maven에는 최신 SDK가 게시되어 있었다. 이 불일치를 코드에서 임의로 해결하지 않으며, 실제 서비스 지원과 승인은 별도로 검증해야 한다.

## 포커스와 주행 제한

[공식 오디오 포커스 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development)에 따라 AudioAttributes와 AudioFocusRequest를 사용한다. 미디어 용도와 음성 콘텐츠, 일시 포커스를 요청하고 loss, transient loss, duck 요청에서 모두 녹음과 출력을 중단한다. 기본값은 복귀 시 조용히 기다리는 것이다. 명시적으로 자동 듣기를 선택했고 이전 상태가 듣기였을 때만 일시 포커스 복귀 후 재개한다.

[공식 주행 제한 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)에 따라 distractionOptimized 메타데이터를 선언하고 CarUxRestrictionsManager 신호를 구독한다. 실제 신호가 없거나 연결이 끊기면 설정과 상세 출처를 잠근다. 단순 타이머나 데모 버튼으로 정차 상태를 조작하지 않는다.

## 위치와 내비

[FusedLocation 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)의 `pleos.car.permission.FUSED_LOCATION`을 선언한다. 사용자 동의 후 한 번 위치를 요청하고 2분보다 오래되거나 잘못된 좌표는 사용하지 않는다. 콜백 해제와 release를 항상 수행한다.

[NaviHelper 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)의 `RouteInfo`에 좌표, 장소 이름, ID, 주소와 `poiProvider`를 전달한다. `pleos.car.permission.NAVI_ROUTE_SEARCH`를 선언한다. Kakao 결과를 `poiProvider="Kakao"`로 전달하는 실제 차량 수용 여부와 최종 길 안내는 검증 기록의 범위를 따른다. `onRoutingCompleted`를 경로 탐색 확인으로 사용하며 실제 도착이나 음성 안내 완료로 해석하지 않는다.

## 공모전 안내

[개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)의 API 28 이상, Gradle 8.0 이상, Java 또는 Kotlin 조건을 반영했다. [제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)는 GitHub 소스, `/apk/팀명_앱이름_release.apk` 또는 Release APK, README와 최종 커밋 해시를 안내한다. 확인 시점에는 1차의 소스와 APK 제출이 선택 가산점, 2차 PT에서는 동작 가능한 앱이 요구되는 것으로 표기되어 있었다. 실제 제출과 이메일 발송은 수행하지 않는다.

한국어 음성 요청은 STT와 TTS 모두 `setLocale(Locale.KOREA)` 후 실행한다. 시스템 언어에 의존하지 않는다. 2026-11-16에 확인한 [STT locale](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)과 [TTS locale](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/set-locale) 문서의 호출 순서를 따른다. debug 빌드의 `RoadieVoice` 로그에는 준비, 시작, 최종 결과 도착, 종료와 오류 종류만 남기며 발화나 응답 내용은 기록하지 않는다. release 빌드는 이 진단 이벤트도 기록하지 않는다.
