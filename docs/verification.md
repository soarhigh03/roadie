# 검증 기록

검증일: 2026-11-16. 이 문서는 실제로 수행한 검사와 수행하지 않은 연결 시험을 구분한다. 실차 테스트는 수행하지 않았다.

## 환경과 화면 규격

| 항목 | 실제 사용 값 |
| --- | --- |
| 호스트 | macOS, Apple Silicon, OpenJDK 17.0.15 |
| 빌드 | Gradle 8.13, AGP 8.9.1, Kotlin 2.1.0 |
| Android SDK | Platform 35, Build Tools 35.0.0 |
| Emulator 실행 도구 | 37.2.13.0, build 16740729 |
| 차량 이미지 | 공식 Pleos Connect Emulator 2.1.0, Android 14 API 34, ARM64 |
| AVD | Roadie_Pleos_2_1, 4GB RAM, 2 CPU, Cold Boot |
| 전체 디스플레이 | 2560 x 1440px |
| 밀도 | 160dpi와 320dpi에서 검사. 공식 프로필은 160dpi |
| 차량 셸의 앱 분할 창 | 320dpi에서 1682 x 1070px, 공식 160dpi Cold Boot에서 1682 x 1210px |
| 320dpi 초기 계측 렌더 | 841 x 535dp의 가로 창, 560 x 535dp와 480 x 535dp의 작은 창 |
| 160dpi 최종 계측 렌더 | 1200 x 1210dp와 560 x 1210dp, 480 x 360dp에서 글자 1.5배 |
| 운전자 사용자 | Android user 10. headless system user 0과 구분 |

처음 설치돼 있던 Android Emulator 36.1.9에서는 이미지의 차량 서비스 연결과 큰 APK 전송에 문제가 있어 최신 공식 도구로 전환했다. 공식 이미지 ZIP의 CRC와 새 에뮬레이터 ZIP의 공식 SHA-1을 확인했다. AVD와 내려받은 도구, 이미지는 `/tmp`에 있어 호스트 재부팅 후 다시 준비해야 할 수 있다. SDK와 시스템 이미지는 저장소에 재배포하지 않는다.

## 자동 검사

| 검사 | 결과 |
| --- | --- |
| `:core:test` | 23개 통과, 실패 0 |
| `npm --prefix server test` | 23개 통과, 실패 0 |
| `npm --prefix server run check` | JavaScript 문법 검사 통과 |
| `scripts/test-pleos.sh`와 동일한 계측 실행 | Pleos user 10에서 Android 계측 8개 통과 |
| `:app:assembleDebug` | 성공 |
| `:app:assembleRelease` | 성공, 별도 release 키 사용 |
| `:app:lintRelease` | 오류 0, 경고 14 |

코어는 취소된 요청의 늦은 응답, 듣기와 말하기 전환, 발화 중단, 권한 거부와 재시도, 포커스 상실과 동의에 따른 복귀, 네트워크 재시도, 세션 종료, 기억 동의와 삭제, 데모 격리, 휴식 제안, 뉴스 출처 보존을 검증한다. 서버는 인증 누락, 입력 크기, 요청 제한, 취소 전파, 비밀정보를 숨기는 오류 응답, 좌표 검증, RSS 날짜와 중복, 오래된 데이터 처리를 검증한다.

Android 계측 8개는 실제 Compose 버튼 콜백, 72dp 이상 주요 조작 영역, 작은 창 배치, 480 x 360dp 창에서 1.5배 글자, 정차 여부에 따른 출처 노출, DataStore 복원, Android Keystore 암호화와 기억 삭제를 검증한다. MainActivity를 실제 재생성해 세션이 유지되고 백그라운드로 내리면 세션이 종료되는 것도 확인했다. AudioManager에 별도의 미디어 포커스 요청을 보내 Roadie 어댑터의 일시 상실과 복귀 콜백을 검증했다. 이는 내비 음성과의 실제 경쟁 시험과 구분한다.

차량 셸의 창 전환 중에는 Compose 트리가 잠시 존재해도 화면에 표시되지 않아, 테스트가 실제 표시를 기다리도록 했다. debug 전용 렌더 호스트는 창 크기 변경으로 재생성돼도 내용을 복원한다. 기능이나 검사를 끄지 않았으며 [최종 8개 계측 결과](tests/pleos-instrumentation.txt)를 보관한다. APK 전송은 `--no-streaming` 방식을 사용했다.

lint 경고는 API 34 대상 지정, 사용 버전보다 새로운 라이브러리 알림, Compose 파라미터 순서, 아이콘 형태와 KTX 사용 권고다. API 34는 현재 Pleos 이미지의 대상 버전이며 API 35 이상 차량 환경은 시험하지 않았다.

## 실제 서버와 앱 연결

최초 검증은 사용자의 선택에 따라 OpenAI와 Kakao 키를 설정하지 않은 상태에서 수행했다.

`node --env-file=server/.env scripts/smoke-server.mjs`는 실행 중인 로컬 서버를 통해 인증 없는 요청의 401, 실제 RSS 수집, 모델 키가 없을 때의 `503 ai_not_configured`, 장소 키가 없을 때의 `503 places_not_configured`를 확인했다. 생성한 대체 응답을 뉴스나 AI로 사용하지 않았다.

2026-11-16에 사용자가 서버 키를 저장한 뒤 `scripts/verify-live.mjs --places --ai`로 실제 공급자 연결을 추가 검증했다. 11:25 UTC 실행에서 OpenAI Responses API의 `gpt-4.1-mini`가 HTTP 200과 57자 응답을 반환했다. 고정된 테스트 인사말을 사용했으며 응답 본문과 키는 검사 로그에 남기지 않았다. 모델 호출 성공은 차량 마이크부터 이어지는 전체 대화 시험과 구분한다.

Kakao는 고정 테스트 좌표 `(37.3, 127.1)`에서 HTTP 200을 반환했다. 초기 검색에 섞인 택시 전용 쉼터와 관리 영업소를 발견해 교통 시설의 휴게소 분류와 이름 검사를 추가했다. 변경 후 죽전간이휴게소, 죽전휴게소 서울방향, 용인졸음쉼터 강릉방향이 반환됐고 ID, 좌표, 거리 계약을 통과했다. 최대 3페이지에서 3건까지 찾으며 매장, 사무소, 잘못된 좌표와 중복을 제외하는 서버 테스트를 추가했다. 분류와 명칭에 의존하는 검색으로, 현재 도로에서 진입 가능한지는 내비 경로로 별도 확인해야 한다.

| 관심사 | 출처 | 항목 수 | 최종 확인한 최신 게시 시각 UTC |
| --- | --- | --- | --- |
| AI와 테크 | 한국경제 | 3 | 2026-11-16 07:19:22 |
| 과학과 우주 | NASA | 3 | 2026-11-16 05:05:00 |
| 문화와 일상 | 한국경제 | 3 | 2026-11-16 08:29:32 |

실제 debug 앱의 설정 화면에서 loopback 주소와 개발 접근 토큰을 저장하고 `adb reverse`로 서버를 연결했다. 데모를 끈 상태에서 브리핑 3건과 한국경제의 기사 제목, 출처 URL, 게시 시각이 앱에 표시됐다. 에뮬레이터 Cold Boot 후에도 첫 안내와 서버 설정이 복원되고 같은 실제 RSS를 다시 조회했다. 시각은 기기의 시간대로 변환하며 캡처 당시 에뮬레이터는 GMT-08:00이었다.

음성 세션 시작, 마이크 끄기와 다시 켜기, RSS 요청에 따른 녹음 취소를 조작했다. 브리핑과 휴식 제안에서 Pleos TTS의 준비, 재생 시작, 완료 콜백을 확인했다.

추가로 macOS Yuna가 합성한 고정 문장 "안녕 로디. 오늘은 어떤 이야기를 해볼까?"를 16kHz, mono, 16-bit PCM으로 준비해 에뮬레이터의 공식 gRPC `injectAudio`에 입력했다. 호스트 마이크는 껐고 gRPC는 loopback과 Bearer 인증으로 제한했다. STT가 종료 이벤트 후 약 27ms 뒤 최종 결과를 보내는 것을 발견해, 종료 이벤트만으로 결과 대기를 취소하던 어댑터를 수정했다. 최종 결과나 명시적 오류, 35초 제한까지 기다리며 사용자의 중단은 즉시 취소한다.

수정 후 11:42 UTC 시험에서 **Pleos STT 최종 결과, 실제 OpenAI 응답, Pleos TTS 시작과 완료, 대기 상태 복귀**가 모두 이어졌다. [실제 AI 응답 화면](screenshots/pleos-live-ai-response.png)을 기록했다. 두 번째 합성 입력에서는 TTS 시작 후 `말 끊고 이야기하기`를 눌러 새 STT 준비 이벤트를 확인했고, `마이크 끄기`로 별도 음소거 상태가 됐다. 실제 사람의 발화나 차량 마이크 품질, 사람이 출력 음성을 청취한 시험은 아니다.

오디오 입력과 출력 캡처를 동시에 실행한 첫 시도에서는 에뮬레이터 프로세스가 종료돼 실패로 남겼다. 소프트웨어 그래픽으로 전환했을 때는 OEM DrivingView의 `libUnreal.so` 오류와 시스템 재시작이 발생했다. `-gpu host`로 Cold Boot하고 출력 스트림 캡처를 제외한 뒤 위 음성 연결과 8개 계측 검사를 통과했다. 음성 파형 수집이나 실차 안정성까지 검증한 것으로 해석하지 않는다.

고정된 한국 테스트 좌표를 에뮬레이터 gRPC `setGps`로 주입하고, Roadie의 위치 설명과 실제 FUSED_LOCATION 권한 요청을 거쳐 검색했다. Pleos FusedLocation에서 받은 위치로 서버와 Kakao까지 연결돼 [실제 앱에 휴게소 3곳과 거리](screenshots/pleos-live-rest-places.png)가 표시됐다. 사용자의 실제 위치를 사용한 시험은 아니다.

죽전간이휴게소를 선택해 NaviHelper 경로 요청을 보냈다. 이미지에 Navi 서비스와 차량 내비 앱이 설치돼 있었지만 20초 안에 경로 완료 응답을 받지 못했다. 앱은 [내비 응답을 확인하지 못했다는 안내](screenshots/pleos-navigation-timeout.png)를 표시하고 장소 목록을 유지했다. 경로 수용이나 안내 시작을 성공으로 기록하지 않는다.

듣기 중 앱을 벗어나 실제 차량 설정 앱으로 이동한 후 Roadie로 돌아오자 준비 상태가 됐다. 자동 녹음으로 돌아가지 않는 UI 흐름과 종료 시 stop, release 호출을 확인했다. 추가 발화 중단 시험에서 AudioService 기록의 Pleos 음성 인식 세션 305가 `rec stop`으로 끝났고 Roadie는 준비 상태로 복귀했다. OEM 자체 호출어 서비스의 녹음 표시는 Roadie 녹음과 구분했다. 차량 HOME 키는 앱을 보이는 상태로 둘 수 있어 백그라운드 판정의 근거로 사용하지 않았다.

실제 CarUxRestrictions 서비스가 연결되고 설정 접근이 허용되는 신호를 확인했다. 임의의 정차 플래그를 사용하지 않았다. 실제 차량 주행이나 내비 음성과의 포커스 경쟁은 재현하지 않았다.

## 화면 캡처

[출발](screenshots/day-ready.png), [말하기](screenshots/day-speaking.png), [휴식 제안](screenshots/rest-suggestion.png), [밤의 조용한 동행](screenshots/night-quiet.png), [작은 창의 마이크 꺼짐](screenshots/compact-muted.png)은 Pleos에서 실제 Compose UI를 렌더한 계측 캡처다. 재현 가능한 데모 상태라고 화면에 표시한다. Figma 화면 이미지를 앱에 넣은 것이 아니다.

[실제 RSS 출처](screenshots/pleos-live-sources.png)는 공식 160dpi로 Cold Boot한 앱과 차량 셸을 포함한 원본 디스플레이 캡처다. 실행 중 밀도만 변경했을 때 OEM 상단 영역이 앱 제목과 겹쳤으나, AVD 하드웨어 밀도를 160dpi로 맞춘 뒤 Cold Boot하자 정상 배치됐다. 런타임 밀도 변경은 물리 화면 회전 시험의 대체가 아니며 실제 배포 차량의 창과 인셋 검증은 추가로 필요하다.

## 서명과 보안

`apk/Roadie_Roadie_release.apk`는 `assembleRelease` 산출물이며 저장소 밖으로 공개하지 않는 로컬 키로 서명한다. 서명 키와 암호 설정, 서버 `.env`는 Git에서 제외한다. APK 서명과 SHA-256은 패키징 때 검증한다. 빌드 소스 커밋과 공개 인증서 지문은 `apk/BUILD_INFO.json`에 기록한다.

APK는 RSA 3072비트의 별도 Roadie Release 인증서와 APK Signature Scheme v2로 검증됐다. `aapt dump badging`으로 최초 버전 0.1.0, minSdk 28, targetSdk 34, debuggable 속성이 없는 것을 확인했다. 테스트용 debug 앱을 제거한 뒤 공식 160dpi Pleos 2.1.0의 운전자 user 10에 release APK를 설치했고 첫 실행 안내가 정상 표시됐다.

release 앱에서 위치 사용 설명을 확인하고 실제 FUSED_LOCATION 권한 요청을 거부했다. 앱은 장소를 조회하지 않고 위치 권한 안내를 표시했다. 다시 요청해 허용하자 FusedLocation이 반환한 좌표 검증을 거쳐 서버 미설정 안내까지 진행했다. release 앱에는 HTTPS 서버를 설정하지 않았으므로 Kakao 요청은 실행되지 않았다. 이 이미지의 권한 대화상자는 사용자 정의 권한을 '알 수 없는 작업'으로 표시하므로 앱의 사전 설명과 구분해서 보아야 한다.

`python3 scripts/audit-repo.py --apk apk/Roadie_Roadie_release.apk`로 추적된 비밀 파일, 키 형태, 금지된 문장 부호와 현재 서버 키, 접근 토큰, 서명 암호의 APK 포함 여부를 검사한다. 앱 백업과 기기 간 전송에서 앱 데이터를 제외한다.

## 미검증 범위와 남은 제한

- 실제 사람의 한국어 발화와 차량 마이크 품질, 출력 음성 청취. 합성 입력으로 STT, AI, TTS 콜백 연결은 확인
- Pleos 차량 마이크 개인정보 설정의 실제 거부와 재허용. 이 이미지에서 STT와 TTS 권한은 `normal`이므로 Android 마이크 위험 권한 팝업과 다르다
- 실제 차량 내비 음성과 Roadie 사이의 오디오 포커스 경쟁
- 실제 차량 좌표 품질과 진입 가능 여부. 에뮬레이터 모의 위치 검색은 통과했지만 NaviHelper 경로 완료 응답과 최종 안내는 확인하지 못함
- HTTPS 운영 서버와 사용자별 인증, 다중 서버에 걸친 요청 제한
- 모든 차량 화면 비율, 물리 화면 회전과 운전 중 시선 부하. Activity 재생성, 작은 창과 1.5배 글자 설정은 계측으로 확인
- 실차와 장시간 운전. 앱 경과 시간은 실제 운행 시간이 아니다

입체 캐릭터는 Figma 원본과 같은 외형을 유지한 말하기, 조용한 동행, 휴식 표정 3종을 사용한다. [에셋과 생성 기록](design/character-expressions.md)에 상태 매핑과 원본을 보존한 편집 프롬프트를 기록했다. 카메라 기반 감지와 호출어는 구현하지 않았다. JVM 상태 전이 검사와 에뮬레이터의 SDK 콜백은 실차 검증을 대신하지 않는다.

## 0.1.1 최종 패키지

`8953a6d6f273e23e9aec3049a976064a40bba8c3`의 앱 코드로 versionCode 2, versionName 0.1.1을 생성했다. 기존 Roadie Release 인증서의 v2 서명을 확인했고 APK는 53,159,495바이트다. SHA-256은 `7c6574758297ecd1fdf0f14ddc3186dd59b77eae32f66a8ffccb97f3df4f5890`이다.

Pleos 2.1.0 user 10에 최종 release APK를 설치하고 첫 안내 화면의 실제 시작을 확인했다. APK에 debuggable 속성이 없고 minSdk 28, targetSdk 34와 automotive 요구가 유지됐다. 현재 로컬의 OpenAI 키, Kakao 키, 서버 접근 토큰과 서명 암호가 APK에 없는 것을 검사했다. release용 HTTPS 서버는 배포하지 않았으며 실제 서버 연결 시험은 debug 앱과 loopback 개발 서버로 수행했다.
