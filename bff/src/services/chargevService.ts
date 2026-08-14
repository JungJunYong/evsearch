import NodeCache from 'node-cache';
import { ChargerStation, Charger, ChargerStatusType } from '../types/ev.js';

// Cache station search results and details
const chargevCache = new NodeCache({ stdTTL: 600, checkperiod: 60 });
const stationDetailMap = new Map<string, ChargerStation>();

const CHARGEV_BASE_URL = 'https://app.gschargev.co.kr';

export interface ChargevStationItem {
  es_key: string;
  station_name: string;
  road_addr: string;
  latitude: string;
  longitude: string;
  use_type: string;
}

export interface ChargevSearchResponse {
  result: string;
  result_message: string;
  data: ChargevStationItem[];
}

/**
 * Search ChargEV stations by keyword (Apartment name, region, complex name, etc.)
 * e.g., '래미안', '자이', '힐스테이트', 'DMC파크뷰자이', 'GS타워'
 */
export async function searchChargevStations(keyword: string): Promise<ChargerStation[]> {
  if (!keyword || keyword.trim().length === 0) {
    return [];
  }

  const trimmed = keyword.trim();
  const cacheKey = `chargev_search_${trimmed}`;
  const cached = chargevCache.get<ChargerStation[]>(cacheKey);
  if (cached) {
    return cached;
  }

  const encodedKeyword = encodeURIComponent(trimmed);
  const url = `${CHARGEV_BASE_URL}/api/station/${encodedKeyword}`;

  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: {
        'User-Agent': 'Dart/3.0 (dart:io)',
        'Accept': 'application/json',
      },
      signal: AbortSignal.timeout(5000),
    });

    if (!res.ok) {
      throw new Error(`ChargeV API returned HTTP ${res.status}`);
    }

    const json = (await res.json()) as ChargevSearchResponse;
    if (json.result !== '0000' || !Array.isArray(json.data)) {
      return [];
    }

    const stations: ChargerStation[] = json.data.map((item) => {
      const lat = parseFloat(item.latitude) || 0;
      const lng = parseFloat(item.longitude) || 0;
      const statId = `CHARGEV_${item.es_key}`;

      // Default chargers representation for the apartment station
      const chargers: Charger[] = [
        {
          statId,
          chgerId: '01',
          typeCode: '02',
          typeName: 'AC완속 (7kW)',
          outputKw: '7',
          status: 'AVAILABLE',
          statusCode: 2,
          statusUpdatedAt: new Date().toISOString(),
          isDeleted: false,
        },
      ];

      const stationObj: ChargerStation = {
        statId,
        name: item.station_name,
        address: item.road_addr,
        lat,
        lng,
        operatorName: 'GS차지비 (ChargEV)',
        useTime: item.use_type === '2' ? '입주민/회원 전용' : '24시간 운영',
        updatedAt: new Date().toISOString(),
        chargers,
        summary: {
          total: chargers.length,
          available: 1,
          charging: 0,
          maintenance: 0,
          unknown: 0,
        },
      };

      // Store in memory for instant retrieval
      stationDetailMap.set(statId, stationObj);
      return stationObj;
    });

    chargevCache.set(cacheKey, stations);
    return stations;
  } catch (err) {
    console.warn(`[ChargeV Service] Failed to search keyword '${keyword}':`, err);
    return [];
  }
}

/**
 * Get station detail by charger number (c_num) printed on the charger
 */
export async function getChargevByChargerNumber(cNum: string): Promise<ChargerStation | null> {
  const url = `${CHARGEV_BASE_URL}/api/v2/chargerStation/${encodeURIComponent(cNum)}`;

  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: {
        'User-Agent': 'Dart/3.0 (dart:io)',
        'Accept': 'application/json',
      },
      signal: AbortSignal.timeout(5000),
    });

    if (!res.ok) return null;
    const json = await res.json();
    if (json.result === '0000' && json.data) {
      const data = json.data;
      const statId = `CHARGEV_CNUM_${data.c_num}`;
      const station: ChargerStation = {
        statId,
        name: data.station_name,
        address: data.road_addr,
        lat: 0,
        lng: 0,
        operatorName: 'GS차지비 (ChargEV)',
        useTime: '아파트 입주민 전용',
        updatedAt: new Date().toISOString(),
        chargers: [
          {
            statId,
            chgerId: data.c_num,
            typeCode: '02',
            typeName: `완속 (${data.c_num}호기)`,
            outputKw: '7',
            status: 'AVAILABLE',
            statusCode: 2,
            statusUpdatedAt: new Date().toISOString(),
            isDeleted: false,
          },
        ],
        summary: {
          total: 1,
          available: 1,
          charging: 0,
          maintenance: 0,
          unknown: 0,
        },
      };

      stationDetailMap.set(statId, station);
      return station;
    }
    return null;
  } catch (err) {
    console.warn(`[ChargeV Service] Failed to get charger number '${cNum}':`, err);
    return null;
  }
}

/**
 * Lookup station detail by statId (e.g. CHARGEV_5637 or CHARGEV_CNUM_3586)
 */
export function getChargevStationDetail(statId: string): ChargerStation | null {
  return stationDetailMap.get(statId) || null;
}
