# EV 충전기 상태 위젯 앱 설계서

## 1. 문서 목적

한국환경공단 「전기자동차 충전소 정보 OpenAPI 활용가이드 v1.25」를 이용해 사용자가 지도에서 충전소와 충전기(단말기)를 선택하고, 선택한 충전기의 현재 상태를 안드로이드 홈 화면 위젯에서 확인하는 앱을 설계한다.

### 핵심 사용자 흐름

```text
지도 열기 → 충전소 선택 → 충전기 목록 확인 → 단말기 선택/등록
      → 홈 화면 위젯 추가 → 위젯에서 상태 확인 → 탭하면 상세 화면
```

## 2. API 확인 결과

기준 문서: `한국환경공단_전기자동차 충전소 정보_OpenAPI활용가이드_v1.25.docx`

| 용도 | API | 주요 파라미터 | 주요 응답 |
|---|---|---|---|
| 지도 및 충전기 기본 정보 | `GET /B552584/EvCharger/getChargerInfo` | `pageNo`, `numOfRows`, `zcode`, `zscode`, `statId`, `chgerId`, `dataType=JSON` | 충전소명/ID, 충전기ID, 타입, 좌표, 주소, 운영기관, 상태 등 |
| 상태 갱신 | `GET /B552584/EvCharger/getChargerStatus` | `pageNo`, `numOfRows`, `period`, `statId`, `chgerId`, `dataType=JSON` | `statId`, `chgerId`, `stat`, `statUpdDt` 및 충전 시간 |

### 식별자

- 충전소: `statId`
- 충전기(단말기): `statId + chgerId`
- 운영기관: `busiId`

### 상태 코드

| 코드 | 앱 표시 | 위젯 색상 예시 |
|---:|---|---|
| 0 | 알 수 없음 | 회색 |
| 1 | 통신 이상 | 주황색 |
| 2 | 충전 대기/사용 가능 | 초록색 |
| 3 | 충전 중 | 파란색 |
| 4 | 운영 중지 | 빨간색 |
| 5 | 점검 중 | 주황색 |
| 6 | 예약 중 | 보라색 |
| 9 | 상태 미확인 | 회색 |

상태 코드는 API 문서 v1.25의 공통코드를 기준으로 관리하며, 향후 추가 코드가 들어와도 앱이 중단되지 않도록 미지의 값은 `UNKNOWN`으로 매핑한다.

## 3. 제안 아키텍처

### 3.1 구성도

```text
┌──────────────────────────────┐
│ Android App                  │
│ Compose UI / Map / Glance    │
│ ViewModel / Repository        │
│ Room / DataStore              │
└──────────────┬───────────────┘
               │ HTTPS, 앱 전용 API
┌──────────────▼───────────────┐
│ BFF/API Server               │
│ 인증키 보관 / 캐시 / 정규화   │
│ 충전소·상태 조회 API          │
└──────────────┬───────────────┘
               │ 서버에서만 호출
┌──────────────▼───────────────┐
│ 한국환경공단 공공데이터 API   │
└──────────────────────────────┘
```

앱과 공공 API 사이에 BFF를 둔다. API 가이드의 운영 URL이 HTTP이며 서비스키가 필요한 구조이므로, 서비스키를 APK에 포함하거나 앱에서 직접 호출하지 않는다. BFF는 HTTPS로 제공하고 공공 API 응답을 앱 도메인 모델로 변환한다.

### 3.2 기술 스택 및 모듈 구성 (Android First)

- **BFF (Backend For Frontend)**: Node.js + Express + TypeScript (보안키 은닉, 캐싱, 데이터 정규화)
- **Android App (Native)**: Kotlin + Jetpack Compose (지도, 상세 화면, UI)
- **Android Widget**: Jetpack Glance AppWidget + WorkManager (100% 백그라운드 갱신 안정성 보장)

```text
evsearch/
  bff/        Node.js + Express (TypeScript API Server)
  android/    Android Native App (Kotlin, Jetpack Compose, Glance Widget)
              - presentation/  MapScreen, StationDetailScreen, SavedChargersScreen
              - data/          Retrofit BFF Client, Room DB (SavedChargers), DataStore
              - domain/        ChargerStation, Charger, ChargerStatus
              - widget/        Glance AppWidget, WorkManager (15분 주기 백그라운드 갱신)
```

### 3.3 제한적 차지비 상태 어댑터

차지비(`CHARGEV_502616`, 경기 남양주시 두산알프하임)는 Elecvery 공개 장소 페이지
`148D13A4030C44F018C8287B7F39516D`의 서버 렌더링 상태를 보조 데이터로 사용한다.
페이지의 `chargerId`와 `충전가능`/`충전중`만 추출하며, 현재는 이 한 장소만 매칭한다.
ChargEV의 물리 번호(예: `110537`)는 끝 두 자리와 Elecvery 단말기 번호를 비교한다.
조회 결과는 3분 캐시하고, 외부 페이지 조회 실패 또는 단말기 매칭 실패 시 기존 정상 상태를
유지한다. 외부 페이지가 제공하지 않는 상태를 임의로 `AVAILABLE`로 만들지 않는다.

이 어댑터는 공개 HTML 구조에 의존하므로 운영 전환 시 공식 API/제휴 연동으로 교체할 수
있도록 `bff/src/services/elecveryService.ts`에 격리한다. 대량 크롤링이나 로그인 우회는
수행하지 않는다.

## 4. 기능 설계

### 4.1 지도 화면

- 현재 위치 또는 기본 지역을 기준으로 충전소를 조회한다.
- 지도 이동/확대가 끝났을 때 화면 영역 기반으로 조회한다.
- `getChargerInfo` 결과를 `statId` 기준으로 묶어 충전소 마커를 만든다.
- 마커에는 충전소 내 `사용 가능`, `충전 중`, `점검/오류` 개수를 요약해 표시한다.
- 동일 충전소에 여러 충전기가 있으므로 마커 클릭 후 단말기 목록으로 이동한다.

초기 버전은 행정구역 코드(`zcode`, `zscode`) 기반 조회를 사용한다. API가 지도 bounds를 직접 지원하지 않으므로, 대량 전체 조회를 매 지도 이동마다 수행하지 않고 BFF 캐시와 지역 단위 조회를 사용한다.

### 4.2 충전소 상세 / 단말기 선택

- 충전소명, 주소, 운영기관, 운영시간, 연락처, 주차료, 안내사항을 표시한다.
- 단말기별로 `chgerId`, 충전 타입, 충전용량, 충전방식, 상태, 상태 갱신 시각을 표시한다.
- 사용자가 단말기별 `위젯에 등록`을 선택한다.
- 등록 가능한 위젯 수는 초기에는 제한하지 않되, 홈 화면 가독성을 위해 권장 최대 개수(예: 5개)를 안내한다.

### 4.3 위젯

**위젯 목록과 즐겨찾기 목록은 서로 독립적인 두 목록이다.** 하나의 단말기가 두 목록에
동시에 속할 수 있고, 어느 목록에도 속하지 않게 되면 로컬 레코드를 지운다. Room의
`saved_chargers` 한 테이블에 `isWidget` / `isFavorite` 플래그로 구분한다.

- 위젯 목록(`isWidget`): 홈 화면 위젯에 표시할 대상. 위젯 탭에서 관리한다.
- 즐겨찾기 목록(`isFavorite`): 빈자리 알림 대상. 즐겨찾기 탭에서 관리한다.

홈 위젯은 위젯 목록의 앞 6대를 표시한다. 표시 항목은 별칭(또는 단말기 번호), 상태,
마지막 동기화 시각, 자동 갱신 주기이며 탭하면 앱이 열린다. 위젯은 서버를 직접 읽지 않고
Room의 마지막 정상 데이터를 그린다.

위젯 크기별 배치: 4×1은 한 줄 6슬롯, 4×2는 2행×3열, 4×3은 3행×2열.

### 4.3.1 상태 지속 시간 표기

즐겨찾기·위젯 목록과 충전소 상세는 상태가 얼마나 이어졌는지 함께 보여준다
(예: `충전 4시간 16분째`, `12분째 비어 있음`). 기준 시각은 사업자가 준 값만 쓴다
(ChargEV `last_start_time` → 없으면 `last_time`). 시각이 없으면 아무것도 표시하지 않고
경과 시간을 추정하지 않는다. 계산은 `presentation/common/StateDuration.kt` 한 곳에서만 한다.

### 4.4 즐겨찾기와 빈자리 알림

즐겨찾기 탭에서 사용자가 직접 정하는 값:

| 설정 | 범위 | 기본값 |
|---|---|---|
| 알림 켜기/끄기 | on/off | off |
| 감시 시간 범위 | 시작~종료(분 단위, 자정 넘김 허용), 시작 == 종료면 종일 | 18:00~23:00 |
| 확인 주기 | 30초 / 1분 / 2분 / 5분 | 1분 |
| 항목별 알림 | 즐겨찾기 단말기마다 on/off | on |

서버는 등록된 주기로 상태를 조회하고 `충전 중 → 충전 가능` **전환 순간에만** 푸시한다
(첫 관측은 기준선으로만 쓴다). 시간 범위 밖에서는 알림을 보내지 않는다.

## 5. 갱신 정책

Android 일반 위젯의 주기적 백그라운드 실행은 시스템 배터리 정책에 묶이고 WorkManager
최소 주기는 15분이다. 그래서 실시간성은 **서버 푸시**로 만들고, 주기 작업은 푸시가 막혔을
때의 보조 수단으로 둔다.

| 경로 | 동작 | 실효 지연 |
|---|---|---|
| 서버 상태 변화 감지 | BFF가 데이터 전용 FCM 푸시(`type=widget_sync`) → 앱이 즉시 동기화 후 위젯 재작성 | 수 초 |
| 빈자리 전환 | 알림 푸시(`type=vacancy`, data 동봉) → 알림 표시 + 위젯 갱신 | 수 초 |
| 사용자 지정 주기 | OneTimeWork 체인이 스스로 다음 회차를 예약(1/3/5/15/30분 선택) | 선택 주기 |
| 안전망 | 15분 PeriodicWork + `updatePeriodMillis` | 최대 15~30분 |
| 수동 | 위젯 새로고침 탭, 앱 진입/복귀, 목록 화면 새로고침 | 즉시 |

앱은 배치 상태 조회 시 허용 캐시 나이(`maxAgeMs`)를 함께 보낸다. 위젯 자동 갱신은 20초,
수동 새로고침과 서버 알림 폴링은 0~절반 주기를 써서 서버 캐시가 실시간성을 깎지 않게 한다.

`statUpdDt`(사업자 상태 갱신 시각)와 앱 조회 시각은 구분해 표시한다. 사용 가능 여부는
자리를 예약하지 않으므로 “충전 가능”을 “자리 확보”로 표현하지 않는다.

## 6. 도메인 및 저장 모델

### ChargerStation

```text
statId: String
name: String
address: String
addressDetail: String?
lat/lng: Double
useTime: String
operatorName: String
operatorCall: String
parkingFree: Boolean?
note: String?
updatedAt: Instant?
```

### Charger

```text
statId: String
chgerId: String
typeCode: String
typeName: String
outputKw: String?
method: String?
status: ChargerStatus
statusUpdatedAt: Instant?
lastChargeStartedAt: Instant?
lastChargeEndedAt: Instant?
isDeleted: Boolean
```

### SavedCharger (Room `saved_chargers`, v3)

```text
key: String                  // statId + ":" + chgerId
statId: String
chgerId: String
stationName: String
chargerTypeName: String
outputKw: String?
status: String
statusCode: Int
statusUpdatedAt: String?
lastFetchedAt: String
sortOrder: Int
customName: String?          // 사용자 별칭 (null이면 stationName 표시)
stateSinceAt: String?        // 현재 상태가 시작된 시각 (충전 경과 시간 표기용)
isWidget: Boolean            // 홈 위젯 표시 대상
isFavorite: Boolean          // 즐겨찾기(알림 후보) 대상
alertEnabled: Boolean        // 즐겨찾기 항목별 알림 수신 여부
```

두 플래그가 모두 false가 되면 행을 삭제한다(`deleteOrphans`). v2 → v3 마이그레이션에서는
기존 행을 위젯·즐겨찾기 양쪽에 넣어 기존 사용자의 기능이 끊기지 않게 한다.

Room에는 개인정보를 저장하지 않는다. 위젯 설정에는 내부 DB ID가 아닌 안정적인 `statId/chgerId` 식별자만 사용한다.

## 7. BFF API 계약(초안)

| 앱 API | 설명 |
|---|---|
| `GET /v1/stations?zcode=&zscode=&page=` | 지도/지역 충전소 및 단말기 정보 |
| `GET /v1/stations/{statId}` | 충전소 상세와 단말기 목록 |
| `GET /v1/chargers/{statId}/{chgerId}/status` | 단일 단말기 최신 상태 |
| `POST /v1/stations/batch-status` | 등록 단말기 상태 일괄 조회 (`maxAgeMs`로 허용 캐시 나이 지정) |
| `POST /v1/alerts/subscribe` | FCM 토큰 + 감시 대상(`keys[].notify`) + 시간 범위 + 주기 등록 |
| `POST /v1/alerts/unsubscribe` | 구독 해지 |
| `GET /v1/alerts/stats` | 감시 규모 점검 |

BFF 내부에서는 공공 API의 응답 코드와 HTTP 오류를 표준 오류(`RATE_LIMITED`, `UPSTREAM_UNAVAILABLE`, `INVALID_PARAMETER`)로 변환한다. `serviceKey`는 환경변수/비밀 저장소로 관리하고 로그·응답·소스에 출력하지 않는다.

## 8. 오류 및 데이터 품질

- `resultCode != 00`이면 성공 데이터로 저장하지 않는다.
- `delYn=Y` 단말기는 지도에서는 숨기거나 “삭제/철거”로 표시하고 위젯 등록을 막는다.
- 좌표가 없거나 숫자로 변환되지 않으면 지도 마커에서 제외하되 목록에서는 확인할 수 있게 한다.
- `statUpdDt`가 오래된 상태는 상태 코드 자체를 변경하지 않고 “오래된 정보” 배지를 추가한다.
- 공공 API가 빈 목록을 반환하는 경우 “충전기 없음”과 네트워크 오류를 구분한다.
- 중복 단말기는 `statId + chgerId`로 제거한다.

## 9. 보안 및 운영

- 현재 `test.js`에는 서비스키가 포함되어 있으므로 실제 키는 폐기/재발급하고 저장소에 커밋하지 않는다.
- `test.js`는 API 탐색용으로만 사용하고, 운영 앱의 호출 코드로 재사용하지 않는다.
- BFF는 HTTPS, 요청 제한, 캐시, 타임아웃(예: 5초), 재시도 횟수 제한을 적용한다.
- API 원문 로그에는 서비스키를 남기지 않으며, 사용자 식별 정보는 수집하지 않는 것을 기본값으로 한다.
- 서버 캐시는 지역 정보와 상태 정보를 분리한다. 기본 정보는 상대적으로 길게, 상태 정보는 짧게 캐시한다.

## 10. MVP 범위

1. 지역 기반 지도 충전소 조회
2. 충전소 상세에서 단말기 목록/상태 표시
3. 단말기 1개 위젯 등록 및 해제
4. 위젯 탭으로 상세 화면 이동
5. Room 캐시와 WorkManager 갱신
6. 네트워크 오류/오래된 상태 표시

다음 단계로 미룰 기능: 로그인/동기화, 즐겨찾기 공유, 충전 완료 알림, 위젯 다중 레이아웃, 도착 거리 기반 추천, 사업자별 추가 API 연동.

## 11. 구현 순서

1. Android 프로젝트 생성 및 도메인 모델/상태 코드 매퍼 작성
2. BFF 없이도 테스트 가능한 fake repository와 API DTO 작성
3. BFF의 공공 API 어댑터 및 캐시 구현
4. 지도 → 충전소 상세 → 단말기 선택 화면 구현
5. Room 저장 및 등록 단말기 관리 구현
6. Glance 위젯과 WorkManager 갱신 구현
7. 오류/오래된 데이터/삭제 단말기 테스트
8. 실제 API 키를 사용한 통합 테스트 후 키 교체 및 운영 배포

## 12. 결정이 필요한 항목

- 지도 SDK: Kakao Map SDK v2 Native (확정)
- 안드로이드 스택: Kotlin + Jetpack Compose + Glance AppWidget (확정)
- 백엔드 스택: Node.js + Express + TypeScript BFF (확정)
- 위젯 갱신 주기와 배터리 정책: 기본 15분 WorkManager, 수동 새로고침 가능
- 위젯 최대 등록 개수와 위젯 크기별 표시 필드
- 앱의 기본 지역/현재 위치 권한 정책

## 13. 디자인 언어 (Apple dark-tile)

토큰은 `android/app/src/main/java/com/evsearch/app/presentation/theme/AppleTokens.kt`,
공통 컴포넌트는 `presentation/common/AppleUi.kt`에 있다. 새 화면은 이 두 파일만 참조한다.

- **표면**: 글로벌 내비 `#000000`, 캔버스 `#1d1d1f`, 타일 `#272729 / #2a2a2c / #252527`.
  인접한 타일은 미세 단계차로만 구분한다.
- **액센트는 하나**: `#2997ff`(dark surface용 Sky Link Blue). 포커스 링은 `#0071e3`.
  두 번째 브랜드색은 만들지 않는다.
- **의미색은 상태 표기 전용**: 충전 가능/충전 중/장애/점검/중지/예약에만 Apple system
  color 계열을 쓰고, 버튼·링크·칩 같은 조작 요소에는 쓰지 않는다.
- **깊이**: 그림자 없음. 표면색 변화와 1px 헤어라인(`rgba(255,255,255,0.08)`)만 사용한다.
- **모양**: 유틸 버튼 8dp, Pearl 캡슐 11dp, 카드 타일 18dp, 액션·칩·검색은 pill.
- **활자**: 굵기 사다리 300/400/600/700(500은 쓰지 않음), 본문 17sp/1.47, 17sp 이상에는
  음수 자간, 미세 활자는 12sp/10sp.
- **누름 상태**: 모든 버튼이 `scale(0.95)` 하나로 통일. hover 상태는 정의하지 않는다.
- **위젯**: 같은 팔레트를 Glance drawable(`bg_widget_rounded`, `bg_card_*`)로 옮겼다.
  홈 화면에서는 ‘충전 가능’ 한 상태만 초록 15% 틴트로 강조하고 나머지는 중립 타일이다.
