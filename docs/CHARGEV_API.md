# ChargEV(GS차지비) API 인벤토리

> 이 문서는 GS차지비 안드로이드 앱(`com.lgntel.ngcharger`, v2.8.1, Flutter)을
> 정적 분석(`libapp.so` = Dart AOT 스냅샷 문자열 덤프)하고, 운영 서버에 무토큰
> 읽기 전용 호출로 교차 검증하여 정리한 **비공식** 참조 자료다.
> 운영 전환 시에는 공식 API/제휴 연동으로 교체해야 하며, 로그인 우회·대량
> 크롤링은 수행하지 않는다. (기존 `docs/ARCHITECTURE.md` §3.3의 격리 원칙과 동일)
>
> 최종 검증일: 2026-08-14 · 검증 기준 충전소: `두산알프하임` (es_key `502616`)

---

## 1. 서버 환경 (Base URL)

| 환경 | Host | 비고 |
|---|---|---|
| **PRD** | `https://app.gschargev.co.kr` | 앱 REST API 운영 (현재 BFF가 사용) |
| STAGE | `https://stgapp2.gschargev.co.kr` | 스테이징 미러 |
| STAGE | `https://stg-charger-app.gschargev.co.kr:10443` | 비표준 TLS 포트 |
| DEV | `https://devapp.gschargev.link` | 개발(.link TLD) |
| PRD(λ) | `https://e5a8do8r67.execute-api.ap-northeast-2.amazonaws.com/prod` | AWS API Gateway 보조 백엔드 |
| DEV(λ) | `https://h1f1jpe6jb.execute-api.ap-northeast-2.amazonaws.com/dev` | AWS API Gateway (dev) |
| CDN | `https://cdn.gschargev.co.kr` , `https://stgcdn.gschargev.co.kr` | 정적/이미지 |

서드파티: Toss Payments(`api.tosspayments.com`), Kakao(`kapi/kauth.kakao.com`),
ONE Store, KECO(`ev.or.kr`). deeplink scheme: `chargev://`, `gschargev://`.

`.env`(base APK 내부)에는 Toss/Kakao/Google/Hackle(A/B테스트 SDK)의 **서드파티 키**만
들어 있고 **자체 API 서버 키는 없다**(키 원문은 이 문서에 싣지 않음).

---

## 2. 인증 모델

- **2-토큰 (access + refresh)**. `POST /api/v2/members/login`
  `{loginId, password, gcmId(FCM), deviceId}` → `{accessToken, rToken, tokenVersionId}`.
- 모든 API 요청에 `Authorization: Bearer <accessToken>` 자동 부착(request interceptor).
- 401 만료 시 `POST /api/v2/members/refresh {token: rToken}` 로 1회 갱신 후 재시도(single-flight).
- 추가 공통 헤더 후보: `x-internal-token`(앱↔게이트웨이 정적 시크릿), `deviceId`,
  `X-OS-Name`, `X-App-Build`, `appChannel`, `phoneOsType`. 충전 제어 계열은 `traceId` 추가.
- **요청 서명(HMAC/nonce/timestamp) 스킴은 없음.** `.env`의 `HACKEL_*`는 서명키가 아니라
  Hackle(피처플래그·분석) SDK 키다.
- **헤드리스(BFF) 미해결 블로커**: `x-internal-token`의 실제 값은 Dart 문자열 덤프에
  없음(네이티브/빌드 상수 추정). login/refresh JSON 키 casing도 미확정 →
  실기기 트래픽 1회 캡처(mitmproxy 등) 필요.

---

## 3. 무토큰 공개 엔드포인트 (✅ 라이브 검증됨)

> ### ★ 핵심 소스: `POST /api/station/nearbyStation` (에뮬레이터 트래픽 캡처로 확정)
>
> 앱은 **로그인 없이** 지도 데이터를 그린다. 그 비밀은 Bearer 토큰이 아니라
> **고정 앱 시크릿 헤더**다. 실기기(에뮬) 트래픽을 캡처해 확정했다(§9 참조).
>
> ```
> POST https://app.gschargev.co.kr/api/station/nearbyStation
> Headers:
>   x-internal-token: Allow-Access-9579364861794339   ← 열쇠 (Bearer 불필요)
>   co_div_cd: CODVC-1
>   x-os-name: aos
>   x-app-build: 225
>   User-Agent: Dart/3.10 (dart:io)
>   Content-Type: application/json
> Body:
>   { "latitude":"37.6507917", "longitude":"127.25462",
>     "limit":"3", "mbrs_grp_id":null, "mbrs_grp_mapp_seq":null }
> ```
>
> **이 하나로 충전소 + 충전기별 실시간 상태가 전부 나온다** (전국, 좌표 기준 거리순).
> 위조·Elecvery 스크래핑·c_num 스캔이 **전부 불필요**해졌다.
>
> 응답 `data[]` = 충전소, 각 `charger_list[]` = 충전기:
> | 필드 | 예시 | 의미 |
> |---|---|---|
> | `es_key` | `502616` | 충전소 키 |
> | `station_name` | `경기 남양주시 두산알프하임` | 충전소명 |
> | `distance` | `0.17` | 요청 좌표로부터 km |
> | `c_num` | `110508` | 충전기 물리번호 |
> | `ec_key` | `618873` | 충전기 고유키 |
> | `local_area` | `105동 지하 1층 주차장 출입구 옆(P1)` | 설치 위치 설명 |
> | **`charging_status` / `_cd`** | **`충전가능`/`0`, `충전중`/`1`, `충전불가`/`2`** | **실시간 상태 (3종)** |
> | `last_time` | `2026-08-14 23:08:07` | 상태 갱신 시각 |
> | `speed_nm` / `rated_kw` | `완속` / `7` | 충전 타입/용량 |
> | `plug_type` | `02` | 커넥터 타입 |
> | `danga` / `danga_type` | `470` / `2` | 요금(원/kWh)/구분 |
>
> **실측(2026-08-14)**: 두산알프하임 36대(충전중31/가능5), 강남역 반경 글라스타워·DF타워·KB손해보험 등 정상 조회.
> `charging_status`가 충전중/점검까지 구분하므로 `charger/info`(이진)보다 우월 → **주력 소스로 채택**.

---

이 프로젝트(BFF)가 **계정 없이 실제로 쓸 수 있는** API. 모두 2026-08-14 실측.

| Method | Path | 검증 결과 | 반환/의미 |
|---|---|---|---|
| GET | `/api/station/{keyword}` | 200 `0000` | 충전소명/주소 검색 → `es_key, station_name, road_addr, latitude, longitude, use_type` **6개뿐** (bid·충전기목록·상태 없음). 좌표의 유일한 공개 출처 |
| **POST** | **`/api/charger/info/{c_num}`** body `{"card_number":""}` | 200 | ⭐ **무토큰 실시간 상태 소스**. `0000`=충전 가능, `9999 "충전시작불가"`=사용 불가. GET은 405(POST 전용) |
| GET | `/api/v2/chargerStation/{c_num}` | 200 `0000` | c_num → `c_num, station_name, road_addr` 역매핑(상태·좌표 없음). 없는 번호는 `9999 "존재하지 않는 충전기 번호"` |
| POST | `/api/station/poi/type/` body `{"bid_list":[...]}` | 200 `0000` | 유효 bid 없으면 `station_info_list:[]` (bid는 토큰 경로에서만 획득) |
| GET | `/api/advertisement/bannerInfo` | 200 `0000` | 배너 목록(실데이터) |
| GET | `/api/v2/notice` | 200 | 공지(현재 데이터 없음, 토큰 오류 아님) |

### `POST /api/charger/info/{c_num}` — 가용 응답 필드 (실측)
```json
{ "result":"0000","result_message":"성공",
  "data":{ "station_name":"경기 남양주시 두산알프하임","c_num":"110508",
           "speed":"1","speed_nm":"완속","price_type":"비회원가","price":"470.0",
           "connector_type":"02","connector_type_yn":"N","connector_type_nm":"AC완속" } }
```
비가용: `{"result":"9999","result_message":"충전시작불가","popup_message":"...충전불가한 상태입니다."}`
→ 이 API는 **"충전 개시 가능 여부"라는 이진 신호**다. 충전중 vs 점검/고장을 구분하지 못한다.

### c_num(충전기 물리번호)은 전역 연번 (실측)
```
110506~110507 → 김포한강신도시3차푸르지오아파트
110508~110543 → 경기 남양주시 두산알프하임 (정확히 36대)
110544~        → "존재하지 않는 충전기 번호" (9999)
```
→ **단말기 번호 = `c_num - (구간시작 - 1)`**. 두산알프하임 `110508` = **1번기**.
→ 시드 c_num 하나에서 위아래로 확장하며 `station_name`이 바뀌는 지점을 경계로
   충전소별 c_num 구간 인덱스를 만들 수 있다(오프라인 배치 권장).

**두산알프하임 36대 실측 가용 단말기(2026-08-14)**: `1·2·4·15·19·25·31·35` (8대).
이 집합은 Elecvery 공개 페이지의 `충전가능` 집합과 **정확히 일치**함을 확인.

---

## 4. 토큰 필요 엔드포인트 (🔒 라이브 확인)

| Method | Path | 확인 |
|---|---|---|
| GET | `/api/v2/setup` | `1300 "인증 토큰 없음"` (유저 설정 화면용) |
| POST | `/api/station/chargerList` `{bid, ...}` | 회원 그룹 충전기 목록용. 일반 조회는 `nearbyStation`으로 대체 |
| POST | `/api/charge/*` | 충전 개시/제어 (실제 충전은 로그인·결제 필요) |
| GET | `/api/myStation/*`, `/api/v2/members/*`, `/api/history/*` | 즐겨찾기·회원정보·이용내역 |

> **정정**: `nearbyStation`은 초기 프로빙에서 `latitude/longitude`만 보내 `401 Token is null`이
> 났으나, 이는 인증이 아니라 **`x-internal-token` 헤더 누락** 때문이었다. §3의 헤더 세트를
> 붙이면 **로그인 없이 200 실데이터**를 반환한다(에뮬 캡처로 확정). 즉 지도의 핵심 소스는
> 무토큰으로 사용 가능하다.

---

## 5. 전체 API 카탈로그 (그룹별)

`libapp.so` 문자열에서 추출. `*.dart`(pointycastle 암호 라이브러리)·프레임워크
경로는 제외. 일부 토큰 말미 `r`은 스냅샷 bleed 아티팩트(예: `infor`→`info`).

### station (충전소/지도)
`/api/station/{keyword}`(검색) · `/api/station/chargerList` · `/api/station/nearbyStation` ·
`/api/station/poi/type/` · `/api/v2/chargerStation/{c_num}` ·
`/api/myStation/favoriteStation[/]` · `/api/myStation/readRecentStation`

### charger (충전기 정보/신고)
`/api/charger/info/{c_num}` · `/api/charger/maker/` · `/api/v2/charger/maker` ·
`/api/v2/charger/chargerMaker` · `/api/v2/charger/install` ·
`/api/v2/charger/report[/{id}]` · `/api/v2/charger/report/upload` ·
`/api/v2/charger/nonmember/report`(비회원)

### charge (충전 세션 제어)
`/api/charge/qr` · `/api/charge/start` · `/api/charge/start_new` · `/api/charge/stop` ·
`/api/charge/charginginfo/{id}` · `/api/charge/setAppSoc` · `/api/charge/auth/new` ·
`/api/charge/authcancel/new` · `/api/charge/tossreport/new`

### member / auth (회원·인증)
`/api/v2/members/login` · `/api/v2/members/refresh` · `/api/v2/members/logout` ·
`/api/v2/members/info` · `/api/v2/members/password` · `/api/v2/members/readPointCash` ·
`/api/v2/members/unpaid[/creditCard/ | /virtualAccount]` · `/api/v2/getMemberKey` ·
`/api/member/join` · `/api/member/checkIdDuplication` · `/api/member/findIdAuth` ·
`/api/member/checkCi[Update]` · `/api/member/checkJoinCi` · `/api/member/checkPwAuth` ·
`/api/member/deleteCancel` · `/api/v2/member/setNewPassword` ·
`/api/auth/mobile` · `/api/auth/requestAuth` · `/api/auth/smsSend` ·
`/api/members/snsAuth/{joinSns|registerSns|removeSns}` ·
`/api/integration/{join|carAuth|terms}`

### rfcard / roamcard (충전 카드)
`/api/members/rfcard/{apply|auth|cancel|register|lost|giftcon|membershipInfo|...}` ·
`/api/members/roamcard/register` · `/api/members/rombid` · `/api/members/validateRoamInfoChk`

### payment (결제)
`/api/payment/{main[/car|/carNumber]|card|creditcard[/nontoss]|couponRegist|
extpoint|firstprio|cash/refund|toss/regist|naverpay/{list|registr|unRegist|duplicate}}`

### history (이용/결제 내역)
`/api/history/{paylist_new|details|retry|retryInfo/|payType|orderType/|carCardType|
chg_to_corp|email/all_new}`

### terms / notice / event / etc
`/api/v2/terms-settings` · `/api/newTerms/` · `/api/v2/newTerms/` · `/api/integration/terms` ·
`/api/v2/notice[/]` · `/api/v2/popupNotice` · `/api/v2/faq` · `/api/v2/category/{faq|inquiry}` ·
`/api/v2/inquiry[/]` · `/api/v2/event[/]` · `/api/v2/eventInfo` · `/api/v2/eventPreRegistCheck` ·
`/api/v2/push` · `/api/v2/unreadPush` · `/api/v2/setup` · `/api/v2/bank/` ·
`/api/advertisement/bannerInfo` · `/api/image/upload/common/inquiry`

### 응답 엔벌로프
모든 응답: `{ result: "0000"(성공) | "9999"/"1300"(실패), result_message, data, popup_message }`.
**실패도 HTTP 200으로 내려온다**(예외: `nearbyStation` 401). → HTTP 상태가 아니라 `result` 코드로 성공/실패를 판정해야 한다.

### 로컬 캐시 스키마 (앱 내 SQLite, 참고)
```sql
CREATE TABLE station_poi (
  es_key TEXT PRIMARY KEY, category TEXT, latitude TEXT, longitude TEXT,
  promotion TEXT, use_type TEXT, plug_types TEXT, bid TEXT, bid_type TEXT,
  charging_status TEXT, cluster_cnt INTEGER );
```

### 주요 요청 클래스 필드 (스냅샷 추정)
- `ChargerListRequest { bid, yz, payStatusNew, carNo }` (현재 BFF가 보내는 `payStatusNewCd`는 실재 필드 아님)
- `NearByStationRequest { latitude, longitude, ... }`
- `GetStationPoiRequest { bidList }`
- 와이어 JSON은 대체로 **snake_case**(`es_key, road_addr, c_num, pay_status_new` ...).

---

## 6. 현재 BFF 연동의 문제 ("이상한" 부분의 정체)

`bff/src/services/chargevService.ts` / `elecveryService.ts` 기준.

1. **[치명] 실시간 데이터를 위조한다.** API 실패 시 20~36대의 가짜 충전기를 만들고
   (`chargevService.ts:132-156`), 상태를 `i===13→MAINTENANCE, i%4===1||i%5===2→CHARGING`
   규칙으로 배정하며 `statusUpdatedAt`에 `new Date()`를 찍는다. 위젯/지도가 이 허구
   숫자를 "방금 갱신된 실시간 값"으로 표시한다.
2. **[치명] 사실상 죽은 코드.** `fetchDynamicChargersFromApi`는 `CHARGEV_TOKEN`이 없으면
   즉시 `null`(`:14-15`). 환경변수는 보통 미설정 → 실제 호출은 **한 번도 일어나지 않고**
   100% 위조 경로로 흐른다.
3. **[구조적] `chargerList`는 원리적으로 실패한다.** 코드가 `es_key`를 `bid` 자리에
   넣지만(`:130`) 둘은 다른 키다. **검색 응답에는 bid가 아예 없고**(6필드뿐), bid는
   401로 막힌 `nearbyStation`에만 존재 → 공개 경로로는 bid를 얻을 수 없다.
4. **[치명] Elecvery 매칭 off-by-7 버그.** `elecveryService.ts:80-87`이 `110508`의 뒤 두
   자리 `08`로 매칭한다. 올바른 대응은 `c_num - 110507`(→ `110508`=1번기). **유일하게 진짜
   데이터가 있는 알프하임조차 7칸 어긋난 남의 충전기 상태를 보여준다.**
5. **[정확성] `getChargevByChargerNumber`가 없는 값을 지어낸다.** status를 무조건
   `AVAILABLE`, lat/lng를 `0`으로 하드코딩(`:220-236`) → 지도에 기니만 앞바다 마커.
   바로 옆 `POST /api/charger/info/{c_num}`가 무토큰으로 진짜 상태를 주는데도 안 쓴다.
6. **[규격] 에러 판정 오류.** `if (!res.ok) return null`(`:31`)로 HTTP 상태만 본다.
   ChargEV는 실패도 200+`result:9999`로 내려주므로 오탐·미탐이 동시에 난다.
7. **[성능] 워밍업 N+1 + TTL 없는 오염.** `allChargevStations`/`stationDetailMap` Map은
   TTL이 없어 위조 데이터가 프로세스 수명 내내 잔류한다.

---

## 7. 권장 재작성 방향 (nearbyStation 단일 소스) ★확정

에뮬 캡처로 무토큰 `nearbyStation`을 찾았으므로, 이전의 `charger/info` + `c_num 스캔`
+ `Elecvery` 3단 우회는 **폐기**한다. 위조 fallback도 전부 삭제한다.

1. **지도/주변 조회** — `POST /api/station/nearbyStation` (§3 헤더 필수) `{latitude, longitude, limit}`.
   응답 `data[]`(충전소) → `charger_list[]`(충전기 실시간)를 그대로 도메인 모델로 변환.
2. **검색** — `GET /api/station/{keyword}` 유지(충전소명/주소 검색 → es_key·좌표).
   상세는 그 좌표로 `nearbyStation`을 호출해 `charger_list`를 얻는다(검색 응답엔 충전기 없음).
3. **상태 매핑** — `charging_status_cd`: `0→AVAILABLE`, `1→CHARGING`, `2→MAINTENANCE`(충전불가).
   추측 금지, 서버 값 그대로. `last_time`을 `statusUpdatedAt`으로.
4. **정직성** — 데이터 없으면 `chargers:[]`. 응답에 `dataSource:'chargev-nearby'|'none'`,
   `observedAt`을 넣어 위조 재유입을 타입 레벨에서 차단. `statusUpdatedAt`은 상류 `last_time`에서만.
5. **캐시** — 좌표 그리드(또는 es_key) 단위 TTL 60~120초 + stale-while-revalidate.
   워밍업 N+1 제거, TTL 없는 Map 폐기.

### charger_list → 도메인 매핑
| ChargEV | Charger(도메인) |
|---|---|
| `c_num` | `chgerId` / `chargerCode` |
| `local_area` | `location` |
| `charging_status_cd` (0/1/2) | `status` (AVAILABLE/CHARGING/MAINTENANCE), `statusCode` |
| `speed_nm`, `rated_kw` | `typeName`, `outputKw` |
| `plug_type` | `typeCode` |
| `last_time` | `statusUpdatedAt` |
| `danga` | (요금, 신규 필드 검토) |

### 폐기 대상 (chargevService.ts / elecveryService.ts)
- `fetchDynamicChargersFromApi`(죽은 토큰 게이트), `fallbackChargers` 생성(132-156),
  `getChargevByChargerNumber`의 하드코딩(220-236), `warmupChargevStations` N+1,
  `elecveryService`의 off-by-7 매칭 및 HTML 스크래핑 전체.
- Elecvery는 완전 제거 가능(nearbyStation이 충전중/불가까지 제공).

### 회귀 검증 기준값 (2026-08-14, 두산알프하임)
`nearbyStation`(알프하임 좌표) → 36대, 충전중 31 / 충전가능 5. 주변: 중흥에스클래스(20대),
호평한라비발디(3대). 강남역 좌표 → 글라스타워·DF타워·KB손해보험 등 정상.

---

## 8. 스테이징/폴백

`x-internal-token`이 서버에서 회전(변경)될 가능성에 대비:
- 토큰을 코드 상수가 아니라 **환경변수 `CHARGEV_INTERNAL_TOKEN`**(기본값=캡처값)으로 둔다.
- `nearbyStation` 실패(비 200 / result≠0000) 시 위조하지 말고 `chargers:[]` + `dataSource:'none'`.
- 토큰 회전 감지 시 재캡처 절차는 §9.

---

## 9. 에뮬레이터 트래픽 캡처 재현 (토큰 재확보용)

`x-internal-token` 등 헤더가 바뀌면 아래로 재캡처한다. (도구: Android SDK, mitmproxy, reFlutter)

1. **APK 확보** — apkcombo가 Cloudflare로 막히면 Playwright(headed Chrome)로 다운로드.
   M1 에뮬은 **arm64 split 포함 variant**(가장 큰 것)를 받을 것(v7a 전용은 실행 불가).
   `APKEditor.jar m`으로 split XAPK → 단일 APK 병합.
2. **에뮬** — `avdmanager` 버그 시 `~/.android/avd/<name>.avd/config.ini` 수동 작성.
   `emulator -avd <n> -writable-system -no-snapshot -gpu swiftshader_indirect` (GUI 모드가 헤드리스보다 안정적).
3. **CA 신뢰** — mitmproxy CA를 `openssl x509 -subject_hash_old`로 해시명(.0) 만들어
   `adb root; adb remount` 후 `/system/etc/security/cacerts/<hash>.0` push (Dart:io는 시스템 CA 신뢰).
   apex overlay는 에뮬 크래시 유발 → `/system`만 권장.
4. **캡처 경로 (핵심)** — QEMU `-http-proxy`와 transparent+DNAT는 **Dart HTTPS를 못 잡는다**.
   확실한 방법은 **mitmproxy WireGuard 모드**:
   - `mitmdump --mode wireguard` (UDP 51820). `~/.mitmproxy/wireguard.conf`의 `server_key`에서
     x25519 공개키를 계산해 표준 `.conf` 작성(client_key=Interface PrivateKey, Endpoint=10.0.2.2:51820, AllowedIPs=0.0.0.0/0).
   - WireGuard 앱(f-droid/apkcombo) 설치, conf를 `/data/data/com.wireguard.android/files/mitm.conf`에
     root로 주입(chown 10193) → 앱이 자동 인식. 터널 스위치 ON + VPN 권한 OK.
   - 차지비 실행 → `mitmdump -nr flows.mitm --flow-detail 3 "~u nearbyStation"`으로 헤더/바디 확인.
