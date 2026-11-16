# 서버 API 0.1.0

모든 데이터 응답은 JSON이다. `/health`를 제외한 경로는 `Authorization: Bearer <ROADIE_ACCESS_TOKEN>`이 필요하다. 접근 토큰은 모델 키와 별개다. 모델 키와 Kakao 키는 서버 환경변수에만 둔다. release 앱은 HTTPS 주소만 허용한다.

## 대화

`POST /v1/chat`, `Content-Type: application/json`

```json
{"message":"오늘 길이 예뻐","history":[{"role":"user","text":"안녕"},{"role":"assistant","text":"반가워요."}]}
```

```json
{"reply":"창밖 풍경이 좋은가 봐요. 편안하게 함께 갈게요.","mode":"live"}
```

현재 발화는 1,200자 이하, 이력은 최근 6개 메시지 이하이며 `user`와 `assistant`만 허용한다. 응답 예시는 계약 설명용이다. 실행 중 고정 응답으로 대체하지 않는다. OpenAI Responses API의 `store: false`를 사용한다. 이는 공급자 자체의 안전 보관 정책을 없애는 설정은 아니다.

## 관심사 브리핑

`GET /v1/briefings?topic=tech`, topic은 `tech`, `science`, `culture` 중 하나다.

```json
{"topic":"tech","fetchedAt":"2026-11-16T00:00:00.000Z","mode":"live","articles":[{"id":"source-url-hash","title":"발행자가 제공한 제목","summary":"발행자가 제공한 제목","summaryKind":"headline","source":"한국경제","url":"https://www.hankyung.com/article/example","publishedAt":"2026-11-15T23:00:00.000Z","stale":false}]}
```

RSS의 실제 제목을 최대 3개 제공하는 짧은 헤드라인 브리핑이다. 본문을 생성하거나 생성한 내용을 뉴스로 표시하지 않는다. `summaryKind=headline`을 보존한다. 게시 24시간이 지난 항목은 `stale=true`다. 수집 시각과 게시 시각은 다르다. 메모리 캐시는 10분이며 오래됨 표시는 매 요청마다 다시 계산한다. 날짜가 없거나 미래로 5분 이상 벗어난 항목, 허용된 발행자 도메인 밖의 링크와 중복 링크는 제외한다. 유효한 뉴스가 없으면 `articles=[]`를 반환한다.

## 휴식 장소

`POST /v1/rest-places`

```json
{"latitude":37.5,"longitude":127.0}
```

```json
{"places":[{"id":"provider-id","name":"휴게소 이름","address":"주소","latitude":37.51,"longitude":127.01,"distanceMeters":1400}],"source":"Kakao Local","fetchedAt":"2026-11-16T00:00:00.000Z"}
```

동의한 현재 위치로 Kakao Local 키워드 검색을 한다. 반경 20km, 거리순 최대 3곳이며 실제 주행 경로와 차로 방향을 판별하지 않는다. 좌표를 파일이나 데이터베이스에 저장하지 않는다. 결과 선택 후 앱이 Pleos NaviHelper에 경로 탐색을 요청한다. 서버의 장소 검색 성공과 차량의 경로 탐색 성공은 별개다.

Kakao의 `category_name` 계층에서 교통,수송 아래 휴게소, 고속도로휴게소, 졸음쉼터로 분류된 장소만 제공한다. 휴게소 안의 식당과 카페, 영업소, 직원 또는 택시 전용 시설은 제외한다. 후보를 15건씩 최대 3페이지까지 조회하며, 유효한 3곳을 찾으면 중단한다. 중복 ID, 범위를 벗어난 좌표와 거리도 제외한다. 응답의 `category`는 제공자의 분류를 보존한다. 이 필터는 차량별 진입 가능성이나 실제 영업 여부를 보증하지 않는다.

## 오류와 접근 제어

오류 형식은 `{"error":"code"}`다. 발화, 키, 좌표, 공급자 원문 오류는 응답이나 로그에 기록하지 않는다.

| HTTP | 의미 |
| --- | --- |
| 400 | 잘못된 JSON, 대화 이력, 관심사 또는 좌표 |
| 401 | 접근 토큰 누락 또는 불일치 |
| 413 | 요청 본문 32KB 초과 |
| 415 | JSON Content-Type 누락 |
| 429 | IP당 분당 60회, 접근 토큰당 분당 20회 또는 동시 처리 2개 초과 |
| 502 | 공급자 오류, 잘못된 응답 또는 네트워크 실패 |
| 503 | `ai_not_configured` 또는 `places_not_configured` |
| 504 | 서버 28초 제한 초과 |

서버는 기본적으로 `127.0.0.1`에 바인딩한다. 개발용 공유 접근 토큰과 프로세스 내부 제한이므로 공용 서비스의 사용자 인증, 다중 인스턴스 제한, 과금 관리까지 제공하지 않는다. 인터넷 배포 시 TLS와 사용자별 인증, 공유 저장소 기반 제한을 추가해야 한다. 클라이언트 연결 종료 시 공급자 요청을 취소한다. 서버는 대화 이력을 영구 저장하지 않는다.
