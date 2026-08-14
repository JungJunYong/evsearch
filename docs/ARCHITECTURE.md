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

위젯 1개는 1개의 `statId + chgerId`를 표현한다.

- 충전소명 및 단말기 번호
- 현재 상태와 상태 색상
- 충전 타입/용량
- 마지막 상태 갱신 시각
- 마지막 조회 시각 및 오래된 데이터 표시
- 위젯 탭 시 충전기 상세 화면으로 이동
- 위젯 설정 변경 시 다른 충전기로 교체 가능
- 앱 내 `위젯` 탭에서 등록된 충전기 목록을 확인하고 각 항목의 별칭(`customName`)을 수정하거나 등록을 해제할 수 있다. 별칭은 홈 위젯과 앱 내 목록에 동시에 반영된다.

위젯은 서버의 상태를 직접 읽지 않고 Room의 마지막 정상 데이터를 표시한다. 갱신 성공 시 Room과 위젯을 함께 갱신하고, 실패 시 마지막 정상 상태를 유지하면서 `업데이트 실패` 또는 `N분 전`을 표시한다.

## 5. 갱신 정책

공공 API의 `getChargerStatus`는 `period`를 1~10분 범위로 제공하지만, Android 일반 위젯의 주기적 백그라운드 실행은 시스템 배터리 정책의 영향을 받으며 WorkManager 최소 주기는 통상 15분이다.

| 상황 | 동작 |
|---|---|
| 지도/상세 화면 진입 | `getChargerInfo` 또는 상세 상태 즉시 조회 |
| 사용자가 새로고침 | 등록 단말기만 상태 조회 |
| 위젯 갱신 | WorkManager에서 등록 단말기 상태 일괄 조회 |
| 네트워크 실패 | 지수 백오프 재시도, 마지막 정상 데이터 표시 |
| 오래된 데이터 | 상태 옆에 갱신 시각 표시, 임의로 사용 가능 판정하지 않음 |

`statUpdDt`는 API가 상태를 갱신한 시각이며 앱의 조회 시각과 구분한다. 위젯 문구는 `상태 기준 3분 전 · 앱 조회 1분 전`처럼 데이터 신뢰성을 명확하게 표현한다. 사용 가능 여부는 예약을 보장하지 않으므로 “사용 가능”을 “자리 확보”로 표현하지 않는다.

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

### SavedCharger

```text
key: String                  // statId + ":" + chgerId
statId: String
chgerId: String
displayName: String?
customName: String?          // 사용자가 임의로 지정한 커스텀 별칭 (null이면 stationName 표시)
sortOrder: Int
createdAt: Instant
lastStatus: ChargerStatus?
lastStatusUpdatedAt: Instant?
lastFetchedAt: Instant?
```

Room에는 개인정보를 저장하지 않는다. 위젯 설정에는 내부 DB ID가 아닌 안정적인 `statId/chgerId` 식별자만 사용한다.

## 7. BFF API 계약(초안)

| 앱 API | 설명 |
|---|---|
| `GET /v1/stations?zcode=&zscode=&page=` | 지도/지역 충전소 및 단말기 정보 |
| `GET /v1/stations/{statId}` | 충전소 상세와 단말기 목록 |
| `GET /v1/chargers/{statId}/{chgerId}/status` | 단일 단말기 최신 상태 |
| `POST /v1/charger-statuses:batch` | 등록 단말기 상태 일괄 조회 |

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

