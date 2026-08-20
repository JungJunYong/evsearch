import NodeCache from 'node-cache';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { ChargerStation, Charger, ChargerStatusType } from '../types/ev.js';

/**
 * ChargEV (GS차지비) 연동 서비스 — nearbyStation 단일 소스 (2026-08 재작성).
 *
 * 앱(com.lgntel.ngcharger) 트래픽을 에뮬레이터로 캡처해 확인한 결과, 지도는
 * 로그인 없이 고정 시크릿 헤더(x-internal-token)만으로 POST /api/station/nearbyStation
 * 을 호출하며, 응답에 충전소 + 충전기별 실시간 상태(충전가능/충전중/충전불가)가 전부 담긴다.
 * 이전 버전의 위조 fallback / c_num 스캔 / Elecvery 스크래핑은 모두 폐기했다.
 * 자세한 계약은 docs/CHARGEV_API.md 참조.
 *
 * 주의: nearbyStation 응답에는 충전소 절대 좌표(lat/lng)가 없고 distance만 있다.
 * 좌표는 검색 API(GET /api/station/{keyword})가 제공하므로, es_key→좌표 매핑을
 * 파일에 영속 캐시하여 지도 마커/위젯 상세 조회에 재사용한다.
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const CHARGEV_BASE_URL = 'https://app.gschargev.co.kr';
// 앱↔게이트웨이 공유 정적 시크릿. 서버에서 회전될 수 있어 env로 오버라이드 가능.
const INTERNAL_TOKEN = process.env.CHARGEV_INTERNAL_TOKEN || 'Allow-Access-9579364861794339';

// 검색 결과(및 상세)를 짧게 캐시
const chargevCache = new NodeCache({ stdTTL: 90, checkperiod: 30 });

// es_key → 좌표/이름 영속 캐시 (검색 API로 학습, 지도/위젯이 재사용)
interface StationCoord {
  lat: number;
  lng: number;
  name?: string;
  roadAddr?: string;
  useType?: string;
}
const coordCache = new Map<string, StationCoord>();
const COORDS_FILE = path.join(__dirname, '..', 'data', 'chargev_coords.json');

// 전국 POI 좌표 인덱스 (poi/type/PI/new 로 시드). 이름은 없고 좌표/사용가능여부만.
interface PoiEntry {
  lat: number;
  lng: number;
  chargingAvailable: boolean; // charging_status 'Y'(가용 있음) / 'N'
  useType?: string;
}
const poiCoords = new Map<string, PoiEntry>();

/** 좌표 해석: 이름까지 있는 검색 캐시를 우선, 없으면 전국 POI 인덱스. */
function resolveCoord(esKey: string): StationCoord | undefined {
  const c = coordCache.get(esKey);
  if (c && c.lat && c.lng) return c;
  const p = poiCoords.get(esKey);
  if (p && p.lat && p.lng) return { lat: p.lat, lng: p.lng, useType: p.useType };
  return undefined;
}

function loadCoordCache(): void {
  // 최소 seed: 실사용 중인 두산알프하임 (없으면 위젯이 재시작 직후에도 동작)
  coordCache.set('502616', {
    lat: 37.6522570064497,
    lng: 127.253934074825,
    name: '경기 남양주시 두산알프하임',
    roadAddr: '경기 남양주시 백봉로 32',
    useType: '2',
  });
  try {
    if (fs.existsSync(COORDS_FILE)) {
      const raw = JSON.parse(fs.readFileSync(COORDS_FILE, 'utf-8')) as Record<string, StationCoord>;
      for (const [k, v] of Object.entries(raw)) coordCache.set(k, v);
    }
  } catch (e) {
    console.warn('[ChargeV] coord cache load failed:', e);
  }
}

let saveTimer: NodeJS.Timeout | null = null;
function persistCoordCache(): void {
  if (saveTimer) return; // debounce
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      const obj: Record<string, StationCoord> = {};
      for (const [k, v] of coordCache.entries()) obj[k] = v;
      fs.mkdirSync(path.dirname(COORDS_FILE), { recursive: true });
      fs.writeFileSync(COORDS_FILE, JSON.stringify(obj), 'utf-8');
    } catch (e) {
      console.warn('[ChargeV] coord cache persist failed:', e);
    }
  }, 2000);
}

function upsertCoord(esKey: string, c: StationCoord): void {
  const key = String(esKey);
  const prev = coordCache.get(key);
  if (!prev || prev.lat !== c.lat || prev.lng !== c.lng || prev.name !== c.name) {
    coordCache.set(key, c);
    persistCoordCache();
  }
}

loadCoordCache();

// ---- 상태/필드 매핑 -------------------------------------------------------

/** ChargEV charging_status_cd: 0=충전가능, 1=충전중, 2=충전불가 (에뮬 캡처로 확정) */
function mapStatus(cd: unknown, label?: string): { status: ChargerStatusType; statusCode: number } {
  switch (String(cd)) {
    case '0':
      return { status: 'AVAILABLE', statusCode: 2 };
    case '1':
      return { status: 'CHARGING', statusCode: 3 };
    case '2':
      // '충전불가' — 점검/오류/오프라인 통합. 도메인상 MAINTENANCE로 표기.
      return { status: 'MAINTENANCE', statusCode: 5 };
    default:
      // 서버가 준 라벨이 '충전중'/'충전가능'이면 그것으로 보정
      if (label === '충전중') return { status: 'CHARGING', statusCode: 3 };
      if (label === '충전가능') return { status: 'AVAILABLE', statusCode: 2 };
      if (label === '충전불가') return { status: 'MAINTENANCE', statusCode: 5 };
      return { status: 'UNKNOWN', statusCode: 0 };
  }
}

/** "2026-08-14 23:08:07" (KST) → ISO 문자열. 파싱 실패 시 undefined. */
function parseKst(s: unknown): string | undefined {
  if (!s || typeof s !== 'string') return undefined;
  const d = new Date(s.replace(' ', 'T') + '+09:00');
  return isNaN(d.getTime()) ? undefined : d.toISOString();
}

function formatChargerCode(cNum: string): string {
  return cNum.length === 6 ? `${cNum.slice(0, 5)} ${cNum.slice(5)}` : cNum;
}

interface RawChargerListItem {
  c_num?: string | number;
  ec_key?: string | number;
  local_area?: string;
  charging_status?: string;
  charging_status_cd?: string | number;
  last_time?: string;
  last_start_time?: string;
  speed_nm?: string;
  rated_kw?: string | number;
  plug_type?: string;
  danga?: string | number;
  danga_type?: string | number;
}

interface RawNearbyStation {
  es_key?: string | number;
  station_name?: string;
  distance?: string | number;
  charger_list?: RawChargerListItem[];
}

function mapCharger(statId: string, item: RawChargerListItem, observedAt: string): Charger {
  const { status, statusCode } = mapStatus(item.charging_status_cd, item.charging_status);
  const cNum = String(item.c_num ?? '');
  const kw = item.rated_kw != null ? String(item.rated_kw) : undefined;
  const speed = item.speed_nm || '완속';
  return {
    statId,
    chgerId: cNum,
    chargerCode: formatChargerCode(cNum),
    location: item.local_area || undefined,
    typeCode: item.plug_type || '02',
    typeName: kw ? `${speed} (${kw}kW)` : speed,
    outputKw: kw,
    status,
    statusCode,
    statusUpdatedAt: parseKst(item.last_time) || observedAt,
    lastChargeStartedAt: parseKst(item.last_start_time),
    isDeleted: false,
    price: item.danga != null ? String(item.danga) : undefined,
    priceType: item.danga_type != null ? String(item.danga_type) : undefined,
  };
}

function summarize(chargers: Charger[]): ChargerStation['summary'] {
  return {
    total: chargers.length,
    available: chargers.filter((c) => c.status === 'AVAILABLE').length,
    charging: chargers.filter((c) => c.status === 'CHARGING').length,
    maintenance: chargers.filter((c) => c.status === 'MAINTENANCE' || c.status === 'COMM_ERROR').length,
    unknown: chargers.filter((c) => c.status === 'UNKNOWN').length,
  };
}

function mapStation(raw: RawNearbyStation, observedAt: string, coord?: StationCoord): ChargerStation {
  const esKey = String(raw.es_key ?? '');
  const statId = `CHARGEV_${esKey}`;
  const chargers = (raw.charger_list || []).map((c) => mapCharger(statId, c, observedAt));
  const distanceKm = raw.distance != null ? parseFloat(String(raw.distance)) : undefined;
  return {
    statId,
    name: raw.station_name || coord?.name || '',
    address: coord?.roadAddr || '',
    lat: coord?.lat ?? 0,
    lng: coord?.lng ?? 0,
    operatorName: 'GS차지비 (ChargEV)',
    useTime: coord?.useType === '2' ? '입주민/회원 전용' : '충전소 운영시간 확인',
    updatedAt: observedAt,
    observedAt,
    dataSource: 'chargev-nearby',
    distanceKm: distanceKm != null && !isNaN(distanceKm) ? distanceKm : undefined,
    chargers,
    summary: summarize(chargers),
  };
}

// ---- 상류 호출 ------------------------------------------------------------

function nearbyHeaders(): Record<string, string> {
  return {
    'User-Agent': 'Dart/3.10 (dart:io)',
    'x-internal-token': INTERNAL_TOKEN,
    'co_div_cd': 'CODVC-1',
    'x-os-name': 'aos',
    'x-app-build': '225',
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };
}

/** POST /api/station/nearbyStation → 원본 충전소 배열 (실패 시 []). */
async function fetchNearbyRaw(lat: number, lng: number, limit: number): Promise<RawNearbyStation[]> {
  try {
    const res = await fetch(`${CHARGEV_BASE_URL}/api/station/nearbyStation`, {
      method: 'POST',
      headers: nearbyHeaders(),
      body: JSON.stringify({
        latitude: String(lat),
        longitude: String(lng),
        limit: String(limit),
        mbrs_grp_id: null,
        mbrs_grp_mapp_seq: null,
      }),
      signal: AbortSignal.timeout(7000),
    });
    if (!res.ok) {
      console.warn(`[ChargeV] nearbyStation HTTP ${res.status}`);
      return [];
    }
    const json = (await res.json()) as { result?: string; data?: RawNearbyStation[] };
    // ChargEV는 실패도 HTTP 200 + result≠'0000'로 내려주므로 result로 판정한다.
    if (json.result !== '0000' || !Array.isArray(json.data)) return [];
    return json.data;
  } catch (e) {
    console.warn('[ChargeV] nearbyStation fetch failed:', e);
    return [];
  }
}

export interface ChargevStationItem {
  es_key: string;
  station_name: string;
  road_addr: string;
  latitude: string;
  longitude: string;
  use_type: string;
}

interface ChargevSearchResponse {
  result: string;
  result_message: string;
  data: ChargevStationItem[];
}

interface RawPoi {
  es_key?: string | number;
  latitude?: string;
  longitude?: string;
  charging_status?: string;
  use_type?: string;
}

/**
 * POST /api/station/poi/type/PI/new → 전국 ChargEV 충전소 좌표 인덱스.
 * (es_key + lat/lng + charging_status Y/N). 이름/충전기 목록은 없다.
 * x-internal-token 검증이 느슨해 값 무관하게 동작하나 앱과 동일 헤더를 붙인다.
 */
async function fetchAllPoi(): Promise<RawPoi[]> {
  try {
    const res = await fetch(`${CHARGEV_BASE_URL}/api/station/poi/type/PI/new`, {
      method: 'POST',
      headers: nearbyHeaders(),
      body: JSON.stringify({ bid_list: null, charging_status: 'N', plug_types: null, use_type: null }),
      signal: AbortSignal.timeout(15000),
    });
    if (!res.ok) return [];
    const json = (await res.json()) as { result?: string; data?: { station_info_list?: RawPoi[] } };
    if (json.result !== '0000' || !json.data?.station_info_list) return [];
    return json.data.station_info_list;
  } catch (e) {
    console.warn('[ChargeV] poi/type fetch failed:', e);
    return [];
  }
}

/** 전국 POI 좌표를 poiCoords 캐시에 로드 (지도 마커/좌표 해석용). */
async function loadPoiCoords(): Promise<number> {
  const list = await fetchAllPoi();
  for (const p of list) {
    const esKey = String(p.es_key ?? '');
    const lat = parseFloat(p.latitude || '') || 0;
    const lng = parseFloat(p.longitude || '') || 0;
    if (!esKey || !lat || !lng) continue;
    poiCoords.set(esKey, {
      lat,
      lng,
      chargingAvailable: p.charging_status === 'Y',
      useType: p.use_type,
    });
  }
  return poiCoords.size;
}

/** GET /api/station/{keyword} → 좌표 포함 충전소 목록 (상태/충전기 없음). */
async function fetchSearchRaw(keyword: string): Promise<ChargevStationItem[]> {
  try {
    const url = `${CHARGEV_BASE_URL}/api/station/${encodeURIComponent(keyword)}`;
    const res = await fetch(url, {
      method: 'GET',
      headers: { 'User-Agent': 'Dart/3.10 (dart:io)', Accept: 'application/json' },
      signal: AbortSignal.timeout(6000),
    });
    if (!res.ok) return [];
    const json = (await res.json()) as ChargevSearchResponse;
    if (json.result !== '0000' || !Array.isArray(json.data)) return [];
    return json.data;
  } catch (e) {
    console.warn(`[ChargeV] search '${keyword}' failed:`, e);
    return [];
  }
}

// ---- 공개 API -------------------------------------------------------------

const MAX_SEARCH_ENRICH = 12; // 검색 결과 상위 N개만 실시간 보강 (상류 호출 절약)

/**
 * 충전소명/주소 키워드 검색. 좌표를 캐시에 학습하고, 상위 결과는 각 좌표로
 * nearbyStation을 호출해 실시간 충전기 목록까지 채워 반환한다.
 */
export async function searchChargevStations(keyword: string): Promise<ChargerStation[]> {
  const trimmed = (keyword || '').trim();
  if (!trimmed) return [];

  const cacheKey = `search_${trimmed}`;
  const cached = chargevCache.get<ChargerStation[]>(cacheKey);
  if (cached) return cached;

  const items = await fetchSearchRaw(trimmed);
  if (items.length === 0) return [];

  // 좌표 캐시 학습
  const coordByKey = new Map<string, StationCoord>();
  for (const it of items) {
    const lat = parseFloat(it.latitude) || 0;
    const lng = parseFloat(it.longitude) || 0;
    const coord: StationCoord = { lat, lng, name: it.station_name, roadAddr: it.road_addr, useType: it.use_type };
    coordByKey.set(String(it.es_key), coord);
    if (lat && lng) upsertCoord(String(it.es_key), coord);
  }

  const observedAt = new Date().toISOString();
  const enrichList = items.slice(0, MAX_SEARCH_ENRICH);

  const enriched = await Promise.all(
    enrichList.map(async (it): Promise<ChargerStation> => {
      const esKey = String(it.es_key);
      const coord = coordByKey.get(esKey);
      const lat = coord?.lat || 0;
      const lng = coord?.lng || 0;
      if (lat && lng) {
        const raw = await fetchNearbyRaw(lat, lng, 3);
        const found = raw.find((s) => String(s.es_key) === esKey);
        if (found) {
          const st = mapStation(found, observedAt, coord);
          st.dataSource = 'chargev-nearby';
          return st;
        }
      }
      // 실시간 조회 실패: 좌표/이름만, 충전기는 비움 (위조 금지)
      return {
        statId: `CHARGEV_${esKey}`,
        name: it.station_name,
        address: it.road_addr,
        lat,
        lng,
        operatorName: 'GS차지비 (ChargEV)',
        useTime: it.use_type === '2' ? '입주민/회원 전용' : '충전소 운영시간 확인',
        updatedAt: observedAt,
        observedAt,
        dataSource: 'chargev-search',
        chargers: [],
        summary: { total: 0, available: 0, charging: 0, maintenance: 0, unknown: 0 },
      };
    })
  );

  chargevCache.set(cacheKey, enriched);
  return enriched;
}

/**
 * 좌표 기반 주변 충전소 실시간 조회 (지도용). 절대 좌표는 좌표 캐시로 보강한다.
 */
export async function getNearbyChargevStations(
  lat: number,
  lng: number,
  limit = 20
): Promise<ChargerStation[]> {
  const raw = await fetchNearbyRaw(lat, lng, limit);
  if (raw.length === 0) return [];
  const observedAt = new Date().toISOString();
  return raw.map((s) => mapStation(s, observedAt, resolveCoord(String(s.es_key))));
}

/**
 * statId(CHARGEV_{es_key})로 충전소 상세(실시간 충전기 목록) 조회.
 * 좌표 캐시가 있어야 nearbyStation을 호출할 수 있다. 없으면 null.
 */
// statId -> 마지막 상세 조회 시각. 호출부가 허용 캐시 나이를 지정할 수 있게 한다.
const detailFetchedAt = new Map<string, number>();

/**
 * 충전소 상세(실시간 충전기 상태).
 *
 * @param maxAgeMs 이 나이보다 오래된 캐시는 무시하고 업스트림을 다시 부른다.
 *                 위젯/알림의 준실시간 경로가 짧은 값을 준다. 기본값은 기존 캐시 TTL.
 */
export async function getChargevStationDetail(
  statId: string,
  maxAgeMs = 90_000
): Promise<ChargerStation | null> {
  const cacheKey = `detail_${statId}`;
  const cached = chargevCache.get<ChargerStation>(cacheKey);
  const fetchedAt = detailFetchedAt.get(statId) || 0;
  if (cached && Date.now() - fetchedAt <= maxAgeMs) return cached;

  const esKey = statId.replace(/^CHARGEV_/, '');
  const coord = resolveCoord(esKey);
  if (!coord || !coord.lat || !coord.lng) return null;

  const raw = await fetchNearbyRaw(coord.lat, coord.lng, 20);
  const found = raw.find((s) => String(s.es_key) === esKey);
  if (!found) return null;

  const station = mapStation(found, new Date().toISOString(), coord);
  chargevCache.set(cacheKey, station);
  detailFetchedAt.set(statId, Date.now());
  return station;
}

/**
 * 충전기 물리번호(c_num)로 충전소를 역조회 (QR/현장번호 입력용).
 * nearbyStation은 좌표 기반이라 c_num 역조회가 불가하므로, 공개
 * GET /api/v2/chargerStation/{cNum} 로 station_name을 얻어 검색→상세로 잇는다.
 */
export async function getChargevByChargerNumber(cNum: string): Promise<ChargerStation | null> {
  try {
    const res = await fetch(`${CHARGEV_BASE_URL}/api/v2/chargerStation/${encodeURIComponent(cNum)}`, {
      method: 'GET',
      headers: { 'User-Agent': 'Dart/3.10 (dart:io)', Accept: 'application/json' },
      signal: AbortSignal.timeout(6000),
    });
    if (!res.ok) return null;
    const json = (await res.json()) as { result?: string; data?: { station_name?: string } };
    if (json.result !== '0000' || !json.data?.station_name) return null;

    // station_name으로 검색 → 좌표 확보 → 그 충전소 상세(실시간) 반환
    const stations = await searchChargevStations(json.data.station_name);
    // c_num이 포함된 충전소를 우선 반환
    const withCharger = stations.find((s) => s.chargers.some((c) => c.chgerId === String(cNum)));
    return withCharger || stations[0] || null;
  } catch (e) {
    console.warn(`[ChargeV] getChargevByChargerNumber('${cNum}') failed:`, e);
    return null;
  }
}

/**
 * 전국 ChargEV 충전소 지도 마커 목록 (poi/type 좌표 인덱스 기반).
 * 충전기 목록은 없고 좌표 + 사용가능여부(Y/N)만 — 상세는 상세 조회로.
 * summary.available: POI의 charging_status='Y'(가용 있음)를 1로 표기(대략치).
 */
export function getAllChargevStations(): ChargerStation[] {
  const observedAt = new Date().toISOString();
  const out: ChargerStation[] = [];
  for (const [esKey, p] of poiCoords.entries()) {
    if (!p.lat || !p.lng) continue;
    const named = coordCache.get(esKey);
    out.push({
      statId: `CHARGEV_${esKey}`,
      name: named?.name || 'GS차지비 충전소',
      address: named?.roadAddr || '',
      lat: p.lat,
      lng: p.lng,
      operatorName: 'GS차지비 (ChargEV)',
      useTime: p.useType === '2' ? '입주민/회원 전용' : '충전소 운영시간 확인',
      updatedAt: observedAt,
      observedAt,
      dataSource: 'chargev-search',
      chargers: [],
      // 마커 색상용 대략치: 가용 있음(Y)이면 available=1, 아니면 0. 정확한 대수는 상세에서.
      summary: {
        total: 0,
        available: p.chargingAvailable ? 1 : 0,
        charging: 0,
        maintenance: p.chargingAvailable ? 0 : 1,
        unknown: 0,
      },
    });
  }
  return out;
}

/** 서버 기동 시 전국 POI 좌표 인덱스를 로드 (지도/좌표 해석 기반). */
export async function warmupChargevStations(): Promise<void> {
  try {
    const n = await loadPoiCoords();
    console.log(`✅ [ChargeV Warmup] nationwide POI coords: ${n} stations`);
  } catch (e) {
    console.warn('[ChargeV Warmup] poi load failed:', e);
  }
}

/** 주기적 POI 좌표 갱신 (선택). */
export async function refreshChargevPoi(): Promise<number> {
  return loadPoiCoords();
}

// 하위 호환용 (직접 사용처 없음, 과거 API 유지)
export const allChargevStations = new Map<string, ChargerStation>();
