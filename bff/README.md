# EV Search BFF API Server (TypeScript)

한국환경공단 전기차 충전소 OpenAPI v1.25 데이터를 가공하여 프론트엔드 및 안드로이드 앱에 제공하는 TypeScript 기반 BFF(Backend For Frontend) 서버입니다.

## 주요 기능

1. **보안**: 공공데이터 OpenAPI 서비스 키를 백엔드 환경변수(`.env`)로 격리 관리
2. **캐싱**: 2분/5분 단위 인메모리 캐싱으로 공공 API 호출량 절감 및 응답 속도 향상
3. **데이터 정규화**:
   - `stat` 상태 코드(2: 사용가능, 3: 충전중 등)를 `ChargerStatusType` 문자열 및 색상 상태로 매핑
   - 충전기 타입(`chgerType`)을 한글 명칭(DC차데모, DC콤보 등)으로 변환
   - `statId` 기준 충전소 그룹핑 및 충전소별 상태 요약(total, available, charging 등) 계산
   - YYYYMMDDHHMMSS 공공 API 시각 데이터를 ISO 8601 형식으로 파싱

## API 엔드포인트

- `GET /health`: 서버 헬스체크
- `GET /v1/stations?zcode=11&page=1&numOfRows=50`: 지역별 충전소 목록 및 요약 정보 조회
- `GET /v1/stations/:statId`: 특정 충전소 상세 정보 및 단말기 목록 조회
- `POST /v1/stations/batch-status`: 등록된 위젯 충전기 단말기 목록(`[{ statId, chgerId }]`) 상태 일괄 조회

## 실행 방법

### 로컬 실행

```bash
# 개발 모드 실행
npm run dev

# 빌드 및 프로덕션 실행
npm run build
npm start
```

### Docker 실행

```bash
# 이미지 빌드
docker build -t evsearch-bff .

# 컨테이너 실행 (환경변수는 .env 또는 -e 로 주입)
docker run -d --name evsearch-bff -p 4000:4000 \
  -e ENCODED_SERVICE_KEY=your_service_key \
  -e USE_LIVE_API=true \
  evsearch-bff

# 또는 docker-compose 사용
docker compose up -d --build

# 상태 확인
curl http://localhost:4000/health
```

## 환경 변수

| 변수 | 설명 | 기본값 |
|---|---|---|
| `PORT` | 서버 포트 | `4000` |
| `ENCODED_SERVICE_KEY` | 한국환경공단 OpenAPI 인코딩 서비스 키 | 없음 |
| `USE_LIVE_API` | `true` 이면 KECO 실시간 API 호출, 아니면 mock 데이터 사용 | `false` |

