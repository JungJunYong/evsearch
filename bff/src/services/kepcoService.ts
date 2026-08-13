import NodeCache from 'node-cache';
import { Charger, ChargerStation, ChargerStatusType } from '../types/ev.js';

const stallsCache = new NodeCache({ stdTTL: 600, checkperiod: 120 }); // 설치현황 10분
const statusCacheKepco = new NodeCache({ stdTTL: 120, checkperiod: 30 }); // 운영정보 2분

const BASE_URL = 'https://bigdata.kepco.co.kr/openapi/v1';

interface RawKepcoInstallItem {
  metro: string;
  city: string;
  stnPlace: string;
  stnAddr: string;
  rapidCnt: number;
  slowCnt: number;
  carType?: string;
}

interface RawKepcoManageItem {
  addr: string;
  chargeTp: string; // "1" 완속, "2" 급속
  cpId: string;
  cpNm: string;
  cpStat: string; // "1" 가능, "2" 충전중, "3" 고장/점검, "4" 통신장애, "5" 미연결, "6" 종료, "7" 계획정지
  cpTp: string; // "1"~ "8" 충전방식
  csId: string;
  csNm: string;
  lat: string;
  longi: string;
  statUpdateDatetime: string;
}

/**
 * 한전 설치현황(설치 목록) 조회.
 * metroCd/cityCd 미입력 시 전체 조회, 캐시를 사용해 10분 유지.
 */
export async function getKepcoInstallStatus(
  apiKey: string,
  metroCd?: string,
  cityCd?: string
): Promise<RawKepcoInstallItem[]> {
  const cacheKey = `install_${metroCd || 'all'}_${cityCd || 'all'}`;
  const cached = stallsCache.get<RawKepcoInstallItem[]>(cacheKey);
  if (cached) {
    console.log(`[KEPCO Cache Hit] install (${cacheKey})`);
    return cached;
  }

  const params = new URLSearchParams({
    apiKey,
    returnType: 'json',
  });
  if (metroCd) params.append('metroCd', metroCd);
  if (cityCd) params.append('cityCd', cityCd);

  const url = `${BASE_URL}/EVcharge.do?${params.toString()}`;
  console.log(`[KEPCO] Fetching install status: ${url.replace(/apiKey=[^&]+/, 'apiKey=***')}`);

  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`KEPCO Install API HTTP ${res.status}`);
    const json = await res.json() as { data?: RawKepcoInstallItem[] };
    const items = json.data || [];
    stallsCache.set(cacheKey, items);
    console.log(`[KEPCO] Install status: ${items.length} items cached`);
    return items;
  } catch (err) {
    console.error('[KEPCO] Install status fetch failed:', err);
    return [];
  }
}

/**
 * 한전 운영정보(충전기 상태) 조회.
 * addr 미입력 시 전체 조회, 캐시를 사용해 2분 유지.
 */
export async function getKepcoManageStatus(
  apiKey: string,
  addr?: string
): Promise<RawKepcoManageItem[]> {
  const cacheKey = `manage_${addr || 'all'}`;
  const cached = statusCacheKepco.get<RawKepcoManageItem[]>(cacheKey);
  if (cached) {
    console.log(`[KEPCO Cache Hit] manage (${cacheKey})`);
    return cached;
  }

  const params = new URLSearchParams({
    apiKey,
    returnType: 'json',
  });
  if (addr) params.append('addr', addr);

  const url = `${BASE_URL}/EVchargeManage.do?${params.toString()}`;
  console.log(`[KEPCO] Fetching manage status: ${url.replace(/apiKey=[^&]+/, 'apiKey=***')}`);

  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`KEPCO Manage API HTTP ${res.status}`);
    const json = await res.json() as { data?: RawKepcoManageItem[] };
    const items = json.data || [];
    statusCacheKepco.set(cacheKey, items);
    console.log(`[KEPCO] Manage status: ${items.length} items cached`);
    return items;
  } catch (err) {
    console.error('[KEPCO] Manage status fetch failed:', err);
    return [];
  }
}

/**
 * 한전 상태코드(cpStat)를 KECO 상태(ChargerStatusType)로 변환.
 */
function mapKepcoStatus(cpStat: string): { status: ChargerStatusType; code: number } {
  switch (cpStat) {
    case '1': return { status: 'AVAILABLE', code: 2 };
    case '2': return { status: 'CHARGING', code: 3 };
    case '3': return { status: 'MAINTENANCE', code: 5 };
    case '4': return { status: 'COMM_ERROR', code: 4 };
    case '5': return { status: 'COMM_ERROR', code: 4 };
    case '6': return { status: 'AVAILABLE', code: 2 }; // 충전종료 후 대기 가능으로 간주
    case '7': return { status: 'SUSPENDED', code: 0 };
    default: return { status: 'UNKNOWN', code: 9 };
  }
}

/**
 * 주소 기반 한전 데이터 매칭.
 * 주소 정규화 후 포함 관계로 찾아 충전소 상세 정보를 보강한다.
 */
export function enrichWithKepcoInstall(
  stations: ChargerStation[],
  installList: RawKepcoInstallItem[]
): ChargerStation[] {
  return stations.map((station) => {
    const match = installList.find((item) => {
      const a1 = station.address.replace(/\s+/g, '');
      const a2 = item.stnAddr.replace(/\s+/g, '');
      return a1.includes(a2) || a2.includes(a1) || item.stnPlace.includes(station.name);
    });

    if (match) {
      return {
        ...station,
        carType: match.carType,
        rapidCnt: match.rapidCnt,
        slowCnt: match.slowCnt,
      } as ChargerStation;
    }
    return station;
  });
}

/**
 * 한전 운영정보로 충전기 상태를 최신화.
 * 주소 기반으로 찾은 충전소의 각 충전기에 KEPCO 실시간 상태를 덮어쓴다.
 */
export function enrichWithKepcoManage(
  stations: ChargerStation[],
  manageList: RawKepcoManageItem[]
): ChargerStation[] {
  return stations.map((station) => {
    const stationMatch = manageList.find((item) => {
      const a1 = station.address.replace(/\s+/g, '');
      const a2 = item.addr.replace(/\s+/g, '');
      return a1.includes(a2) || a2.includes(a1) || item.csNm.includes(station.name);
    });

    if (!stationMatch) return station;

    const mapped = mapKepcoStatus(stationMatch.cpStat);
    const enrichedChargers = station.chargers.map((charger): Charger => {
      // 한전 cpId가 KECO chgerId와 일치하거나 유사하면 매칭
      const sameCharger =
        stationMatch.cpId === charger.chgerId ||
        stationMatch.cpId === `ev${charger.chgerId}` ||
        stationMatch.cpId.endsWith(charger.chgerId);
      if (!sameCharger) return charger;

      return {
        ...charger,
        status: mapped.status,
        statusCode: mapped.code,
        chargeTp: stationMatch.chargeTp,
        cpStat: stationMatch.cpStat,
        cpTp: stationMatch.cpTp,
        statusUpdatedAt: stationMatch.statUpdateDatetime
          ? parseKepcoDatetime(stationMatch.statUpdateDatetime)
          : charger.statusUpdatedAt,
      } as Charger;
    });

    return { ...station, chargers: enrichedChargers };
  });
}

/** 한전 날짜시간(YYYYMMDDHHMMSS)을 ISO로 변환 */
function parseKepcoDatetime(dt: string): string {
  if (dt.length === 14) {
    return `${dt.slice(0, 4)}-${dt.slice(4, 6)}-${dt.slice(6, 8)}T${dt.slice(8, 10)}:${dt.slice(10, 12)}:${dt.slice(12, 14)}+09:00`;
  }
  return new Date().toISOString();
}

/**
 * KECO zcode(지역코드)를 KEPCO metroCd(시도코드)로 변환.
 * KECO: 11-서울, 26-부산, 27-대구, 28-인천, 29-광주, 30-대전, 31-울산, 41-경기, 50-제주
 * KEPCO: 11-서울, 21-부산, 22-대구, 23-인천, 24-광주, 25-대전, 26-울산, 31-경기, 41-제주
 */
export function mapZcodeToMetro(zcode?: string): string | undefined {
  if (!zcode) return undefined;
  const map: Record<string, string> = {
    '11': '11', '26': '21', '27': '22', '28': '23',
    '29': '24', '30': '25', '31': '26', '41': '31', '50': '41',
  };
  return map[zcode] || zcode;
}
