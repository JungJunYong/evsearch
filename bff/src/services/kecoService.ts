import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import NodeCache from 'node-cache';
import {
  getKepcoInstallStatus,
  getKepcoManageStatus,
  enrichWithKepcoInstall,
  enrichWithKepcoManage,
  mapZcodeToMetro,
} from './kepcoService.js';
import {
  Charger,
  ChargerStation,
  ChargerStatusType,
  RawKecoChargerItem,
  RawKecoApiResponse,
} from '../types/ev.js';
import {
  getChargevStationDetail,
  getAllChargevStations,
  warmupChargevStations,
} from './chargevService.js';
import {
  fetchAlfheimStatuses,
  isAlfheimChargevStation,
  matchingElecveryId,
} from './elecveryService.js';

// 충전소 목록 캐시: stations 데이터는 10분(TTL 600초)
// 충전기 실시간 상태 캐시: 상태 조회는 3분(TTL 180초)
const stationsCache = new NodeCache({ stdTTL: 600, checkperiod: 60 });
const statusCache = new NodeCache({ stdTTL: 180, checkperiod: 30 });

/**
 * Warm up nationwide stations in server cache & start periodic background auto-refresh
 */
export async function initServerCacheWarmup(): Promise<void> {
  console.log('🚀 [BFF Server Warmup] Pre-caching nationwide stations into memory...');
  try {
    // 1. Pre-warm ChargEV apartment stations across Korea
    await warmupChargevStations();

    // 2. Warm up public KECO + KEPCO stations
    const stations = await getStations(undefined, undefined, 1, 3000);
    console.log(`✅ [BFF Server Warmup] Nationwide station cache warm up complete! (${stations.length} stations ready)`);
  } catch (err) {
    console.warn('⚠️ [BFF Server Warmup] Pre-caching failed:', err);
  }

  // Background auto-refresh every 4 minutes (keep 100% cache hit rate for all users)
  setInterval(async () => {
    try {
      console.log('🔄 [BFF Background Refresh] Updating nationwide station cache in background...');
      // Force refresh by flushing stations cache
      stationsCache.flushAll();
      const stations = await getStations(undefined, undefined, 1, 3000);
      console.log(`✅ [BFF Background Refresh] Updated ${stations.length} stations in cache.`);
    } catch (err) {
      console.warn('⚠️ [BFF Background Refresh] Background update failed:', err);
    }
  }, 4 * 60 * 1000);
}

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
// dist/services -> dist/data (post-build copy) | src/services -> src/data (dev tsx)
const MOCK_CANDIDATE_PATHS = [
  path.join(__dirname, '../data/mockStations.json'),
  path.join(__dirname, '../../src/data/mockStations.json'),
];

const BASE_URL = 'http://apis.data.go.kr/B552584/EvCharger/getChargerInfo';

function getServiceKey(): string {
  return process.env.ENCODED_SERVICE_KEY || process.env.SERVICE_KEY || '';
}

/**
 * Load offline mock stations.
 * Searches candidate paths so it works both in dev (tsx, src/) and
 * in production Docker image (compiled dist/ with copied data/).
 */
function loadLocalMockStations(): ChargerStation[] {
  for (const mockPath of MOCK_CANDIDATE_PATHS) {
    try {
      if (fs.existsSync(mockPath)) {
        const raw = fs.readFileSync(mockPath, 'utf-8');
        const parsed = JSON.parse(raw);
        if (parsed.success && Array.isArray(parsed.data)) {
          return parsed.data;
        } else if (Array.isArray(parsed)) {
          return parsed;
        }
      }
    } catch (err) {
      console.warn(`[Offline Mock Storage] Failed to read ${mockPath}:`, err);
    }
  }
  console.warn('[Offline Mock Storage] No mockStations.json found in candidate paths.');
  return [];
}

/**
 * Fetch raw charger items from KECO OpenAPI (Only when USE_LIVE_API=true)
 */
async function fetchRawChargerInfo(paramsInput: {
  zcode?: string;
  zscode?: string;
  statId?: string;
  pageNo?: string;
  numOfRows?: string;
}): Promise<RawKecoChargerItem[]> {
  const API_KEY = getServiceKey();
  const searchParams = new URLSearchParams({
    pageNo: paramsInput.pageNo || '1',
    numOfRows: paramsInput.numOfRows || '3000',
    dataType: 'JSON',
    ...paramsInput,
  });

  const url = `${BASE_URL}?serviceKey=${API_KEY}&${searchParams.toString()}`;
  console.log(`[KECO API LIVE CALL] ${url}`);

  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(5000) });
    const data = (await res.json()) as RawKecoApiResponse;
    const items = data?.items?.item;

    if (!items) return [];
    return Array.isArray(items) ? items : [items];
  } catch (err) {
    console.warn('[KECO API FETCH FAILED/TIMEOUT]:', err);
    return [];
  }
}

export async function getStations(
  zcode?: string,
  zscode?: string,
  pageNo = 1,
  numOfRows = 3000
): Promise<ChargerStation[]> {
  const useLiveApi = process.env.USE_LIVE_API !== 'false';
  const kepcoKey = process.env.KEPCO_API_KEY || '';

  if (!useLiveApi) {
    console.log(`[BFF Local Storage] ⚡ Serving offline mock stations (zcode=${zcode || 'all'}, 0 API calls used)`);
    let mockList = loadLocalMockStations();

    // KEPCO 병합 (설치현황 + 운영정보)
    if (kepcoKey) {
      try {
        const regionCode = mapZcodeToMetro(zcode);
        const [installList, manageList] = await Promise.all([
          getKepcoInstallStatus(kepcoKey, regionCode),
          getKepcoManageStatus(kepcoKey),
        ]);
        mockList = enrichWithKepcoInstall(mockList, installList);
        mockList = enrichWithKepcoManage(mockList, manageList);
        console.log(`[BFF KEPCO] Enriched mock stations with KEPCO data`);
      } catch (err) {
        console.warn('[BFF KEPCO] Enrichment failed, using KECO only:', err);
      }
    }

    const chargevList = getAllChargevStations();
    if (chargevList.length > 0) {
      const existingIds = new Set(mockList.map((s) => s.statId));
      for (const cv of chargevList) {
        if (!existingIds.has(cv.statId)) {
          mockList.push(cv);
        }
      }
    }

    if (zcode) {
      return mockList.filter((s) => s.zcode === zcode);
    }
    return mockList;
  }

  // 라이브 API 호출 시 지역 단위 목록 캐시 (5분 TTL)
  const cacheKey = `stations_${zcode || 'all'}_${zscode || 'all'}_${pageNo}_${numOfRows}`;
  const cached = stationsCache.get<ChargerStation[]>(cacheKey);
  if (cached) {
    console.log(`[BFF Cache Hit] ${cacheKey} (TTL 300s)`);
    return cached;
  }

  try {
    const rawItems = await fetchRawChargerInfo({
      zcode,
      zscode,
      pageNo: String(pageNo),
      numOfRows: String(numOfRows),
    });
    let stations = groupItemsByStation(rawItems);

    // KEPCO 병합 (목록에 한전 충전소가 1개 이상 존재할 때만 핀포인트 호출)
    const hasKepcoStation = stations.some(
      (s) => s.operatorName?.includes('한전') || s.operatorName?.includes('한국전력') || s.operatorName?.includes('KEPCO')
    );

    if (kepcoKey && hasKepcoStation) {
      try {
        const regionCode = mapZcodeToMetro(zcode);
        const [installList, manageList] = await Promise.all([
          getKepcoInstallStatus(kepcoKey, regionCode),
          getKepcoManageStatus(kepcoKey),
        ]);
        stations = enrichWithKepcoInstall(stations, installList);
        stations = enrichWithKepcoManage(stations, manageList);
        console.log(`[BFF KEPCO] Enriched KEPCO stations`);
      } catch (err) {
        console.warn('[BFF KEPCO] Enrichment failed in live mode:', err);
      }
    }

    // Merge nationwide pre-indexed ChargEV apartment stations
    const chargevList = getAllChargevStations();
    if (chargevList.length > 0) {
      const existingIds = new Set(stations.map((s) => s.statId));
      for (const cv of chargevList) {
        if (!existingIds.has(cv.statId)) {
          stations.push(cv);
        }
      }
    }

    stationsCache.set(cacheKey, stations);
    return stations;
  } catch (err) {
    console.warn('[KECO API Error] Fallback to local mock data:', err);
    return loadLocalMockStations();
  }
}

export async function getStationDetail(statId: string, zcode?: string): Promise<ChargerStation | null> {
  // Support ChargEV private apartment stations
  if (statId.startsWith('CHARGEV_')) {
    const chargevStation = getChargevStationDetail(statId);
    if (chargevStation) return chargevStation;
  }

  if (zcode) {
    const regional = await getStations(zcode);
    const foundRegional = regional.find((s) => s.statId === statId);
    if (foundRegional) return foundRegional;
  }

  // Fast search in nationwide pre-cached stations (0ms delay)
  const nationwide = await getStations(undefined);
  const foundNationwide = nationwide.find((s) => s.statId === statId);
  if (foundNationwide) return foundNationwide;

  const fallbackAll = loadLocalMockStations();
  return fallbackAll.find((s) => s.statId === statId) || null;
}

export async function getChargerBatchStatus(keys: Array<{ statId: string; chgerId: string }>) {
  // 배치 상태 조회 캐시 (2분 TTL): 동일 키셋에 대한 반복 호출 방지
  const sortedKeys = [...keys].sort((a, b) => `${a.statId}:${a.chgerId}`.localeCompare(`${b.statId}:${b.chgerId}`));
  const cacheKey = `batch_${sortedKeys.map((k) => `${k.statId}:${k.chgerId}`).join('|')}`;
  const cached = statusCache.get<Record<string, any>>(cacheKey);
  if (cached) {
    console.log(`[BFF Cache Hit] batch status (${keys.length} keys, TTL 120s)`);
    return cached;
  }

  const mockList = loadLocalMockStations();
  const resultMap: Record<string, any> = {};

  for (const key of keys) {
    // Support ChargEV apartment chargers
    if (key.statId.startsWith('CHARGEV_')) {
      const chargevStation = getChargevStationDetail(key.statId);
      if (chargevStation) {
        let liveStatus;
        if (isAlfheimChargevStation(key.statId)) {
          const elecveryStatuses = await fetchAlfheimStatuses();
          const elecveryId = elecveryStatuses ? matchingElecveryId(key.chgerId, elecveryStatuses) : null;
          liveStatus = elecveryId ? elecveryStatuses?.get(elecveryId) : undefined;
        }

        const chg = chargevStation.chargers.find((c) => c.chgerId === key.chgerId);
        // A failed external lookup must not manufacture AVAILABLE or use the
        // first charger as a substitute for the requested terminal.
        if (liveStatus) {
          resultMap[`${key.statId}:${key.chgerId}`] = {
            statId: key.statId,
            chgerId: key.chgerId,
            status: liveStatus.status,
            statusCode: liveStatus.statusCode,
            statusUpdatedAt: liveStatus.fetchedAt,
            fetchedAt: liveStatus.fetchedAt,
          };
        } else if (chg) {
          resultMap[`${key.statId}:${key.chgerId}`] = {
            statId: key.statId,
            chgerId: key.chgerId,
            status: chg.status,
            statusCode: chg.statusCode,
            statusUpdatedAt: chg.statusUpdatedAt,
            fetchedAt: new Date().toISOString(),
          };
        }
        continue;
      }
    }

    const station = mockList.find((s) => s.statId === key.statId);
    if (station) {
      const chg = station.chargers.find((c) => c.chgerId === key.chgerId) || station.chargers[0];
      if (chg) {
        const item = {
          statId: key.statId,
          chgerId: key.chgerId,
          status: chg.status,
          statusCode: chg.statusCode,
          statusUpdatedAt: chg.statusUpdatedAt || new Date().toISOString(),
          fetchedAt: new Date().toISOString(),
        };
        // 클라이언트가 사용하는 단일 키 형식 (statId:chgerId)
        resultMap[`${key.statId}:${key.chgerId}`] = item;
      }
    }
  }

  statusCache.set(cacheKey, resultMap);
  return resultMap;
}

function groupItemsByStation(rawItems: RawKecoChargerItem[]): ChargerStation[] {
  const map = new Map<string, RawKecoChargerItem[]>();

  for (const item of rawItems) {
    if (!item.statId) continue;
    const existing = map.get(item.statId) || [];
    existing.push(item);
    map.set(item.statId, existing);
  }

  const result: ChargerStation[] = [];

  for (const [statId, items] of map.entries()) {
    const info = items[0];
    const chargers: Charger[] = items.map((item) => {
      const { type: statusType, code: statusCode } = parseStatusCode(item.stat);
      return {
        statId: item.statId,
        chgerId: item.chgerId,
        typeCode: item.chgerType,
        typeName: parseChargerTypeName(item.chgerType),
        outputKw: item.output,
        method: item.method,
        status: statusType,
        statusCode,
        statusUpdatedAt: parseKecoTimestamp(item.statUpdDt),
        lastChargeStartedAt: parseKecoTimestamp(item.nowTsdt || item.lastTsdt),
        lastChargeEndedAt: parseKecoTimestamp(item.lastTedt),
        isDeleted: item.delYn === 'Y',
      };
    });

    const summary = {
      total: chargers.length,
      available: chargers.filter((c) => c.status === 'AVAILABLE').length,
      charging: chargers.filter((c) => c.status === 'CHARGING').length,
      maintenance: chargers.filter((c) => c.status === 'COMM_ERROR' || c.status === 'MAINTENANCE' || c.status === 'SUSPENDED').length,
      unknown: chargers.filter((c) => c.status === 'UNKNOWN' || c.status === 'UNCONFIRMED').length,
    };

    result.push({
      statId,
      name: info.statNm,
      address: info.addr,
      addressDetail: info.location,
      lat: parseFloat(info.lat) || 0,
      lng: parseFloat(info.lng) || 0,
      useTime: info.useTime,
      operatorName: info.bnm,
      operatorCall: info.bcall,
      parkingFree: info.parkingFree === 'Y' ? true : info.parkingFree === 'N' ? false : undefined,
      note: info.note,
      zcode: info.zcode,
      zscode: info.zscode,
      updatedAt: new Date().toISOString(),
      chargers,
      summary,
    });
  }

  return result;
}

function parseStatusCode(stat: string): { type: ChargerStatusType; code: number } {
  const code = parseInt(stat, 10) || 0;
  let type: ChargerStatusType = 'UNKNOWN';

  switch (code) {
    case 1:
      type = 'COMM_ERROR';
      break;
    case 2:
      type = 'AVAILABLE';
      break;
    case 3:
      type = 'CHARGING';
      break;
    case 4:
      type = 'SUSPENDED';
      break;
    case 5:
      type = 'MAINTENANCE';
      break;
    case 9:
      type = 'UNCONFIRMED';
      break;
  }

  return { type, code };
}

function parseChargerTypeName(typeCode: string): string {
  switch (typeCode) {
    case '01':
      return 'DC차데모';
    case '02':
      return 'AC완속';
    case '03':
      return 'DC차데모+AC3상';
    case '04':
      return 'DC콤보';
    case '05':
      return 'DC차데모+DC콤보';
    case '06':
      return 'DC차데모+AC3상+DC콤보';
    case '07':
      return 'AC3상';
    case '08':
      return 'AC3상';
    default:
      return `충전기 (타입 ${typeCode})`;
  }
}

function parseKecoTimestamp(tsStr?: string): string | undefined {
  if (!tsStr || tsStr.length < 14) return undefined;
  const yyyy = tsStr.substring(0, 4);
  const mm = tsStr.substring(4, 6);
  const dd = tsStr.substring(6, 8);
  const hh = tsStr.substring(8, 10);
  const min = tsStr.substring(10, 12);
  const ss = tsStr.substring(12, 14);
  return `${yyyy}-${mm}-${dd}T${hh}:${min}:${ss}+09:00`;
}
