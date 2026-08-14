import { Router, Request, Response } from 'express';
import {
  getStations,
  getStationDetail,
  getChargerBatchStatus,
} from '../services/kecoService.js';
import {
  searchChargevStations,
  getChargevByChargerNumber,
  getNearbyChargevStations,
  getAllChargevStations,
} from '../services/chargevService.js';

export const stationRouter = Router();

/**
 * GET /v1/stations/chargev/search?keyword=래미안
 */
stationRouter.get('/chargev/search', async (req: Request, res: Response) => {
  try {
    const keyword = (req.query.keyword as string) || '';
    const stations = await searchChargevStations(keyword);
    res.json({
      success: true,
      count: stations.length,
      data: stations,
    });
  } catch (error: any) {
    console.error('Error in GET /v1/stations/chargev/search:', error);
    res.status(500).json({
      success: false,
      error: {
        code: 'CHARGEV_API_ERROR',
        message: error.message || 'Failed to search ChargEV stations',
      },
    });
  }
});

/**
 * GET /v1/stations/chargev/poi
 * 전국 ChargEV 충전소 경량 마커 목록 (좌표 + 사용가능여부). 지도 클러스터링용.
 * 실시간 충전기 목록은 없다(상세/nearby로 조회). 응답은 좌표/식별 위주 경량 DTO.
 */
stationRouter.get('/chargev/poi', async (_req: Request, res: Response) => {
  try {
    const stations = getAllChargevStations();
    const markers = stations.map((s) => ({
      statId: s.statId,
      name: s.name,
      lat: s.lat,
      lng: s.lng,
      available: s.summary.available > 0, // POI charging_status 'Y'
      operatorName: s.operatorName,
    }));
    res.setHeader('Cache-Control', 'public, max-age=300');
    res.json({ success: true, count: markers.length, data: markers });
  } catch (error: any) {
    console.error('Error in GET /v1/stations/chargev/poi:', error);
    res.status(500).json({
      success: false,
      error: { code: 'CHARGEV_API_ERROR', message: error.message || 'Failed to fetch ChargEV POI markers' },
    });
  }
});

/**
 * GET /v1/stations/chargev/nearby?lat=..&lng=..&limit=..
 * 좌표 기반 주변 ChargEV 충전소 실시간 조회 (지도용).
 */
stationRouter.get('/chargev/nearby', async (req: Request, res: Response) => {
  try {
    const lat = parseFloat((req.query.lat as string) || '');
    const lng = parseFloat((req.query.lng as string) || '');
    const limit = parseInt((req.query.limit as string) || '20', 10);
    if (isNaN(lat) || isNaN(lng)) {
      return res.status(400).json({
        success: false,
        error: { code: 'INVALID_PARAMETER', message: 'lat and lng are required numbers' },
      });
    }
    const stations = await getNearbyChargevStations(lat, lng, limit);
    res.json({ success: true, count: stations.length, data: stations });
  } catch (error: any) {
    console.error('Error in GET /v1/stations/chargev/nearby:', error);
    res.status(500).json({
      success: false,
      error: { code: 'CHARGEV_API_ERROR', message: error.message || 'Failed to fetch nearby ChargEV stations' },
    });
  }
});

/**
 * GET /v1/stations/chargev/charger/:cNum
 */
stationRouter.get('/chargev/charger/:cNum', async (req: Request, res: Response) => {
  try {
    const { cNum } = req.params;
    const info = await getChargevByChargerNumber(cNum);
    if (!info) {
      return res.status(404).json({
        success: false,
        error: {
          code: 'NOT_FOUND',
          message: `Charger with number ${cNum} not found`,
        },
      });
    }
    res.json({
      success: true,
      data: info,
    });
  } catch (error: any) {
    console.error('Error in GET /v1/stations/chargev/charger/:cNum:', error);
    res.status(500).json({
      success: false,
      error: {
        code: 'CHARGEV_API_ERROR',
        message: error.message || 'Failed to get charger info',
      },
    });
  }
});

/**
 * GET /v1/stations/search?keyword=
 * 통합 검색: KECO(전국 캐시 이름/주소 필터) + ChargEV(실시간)를 하나의 목록으로.
 * 앱은 소스 구분 없이 이 엔드포인트만 사용한다.
 */
stationRouter.get('/search', async (req: Request, res: Response) => {
  try {
    const keyword = ((req.query.keyword as string) || '').trim();
    if (!keyword) return res.json({ success: true, count: 0, data: [] });

    const [chargevMatches, allKeco] = await Promise.all([
      searchChargevStations(keyword),
      getStations(undefined, undefined, 1, 3000).catch(() => []),
    ]);
    const kw = keyword.toLowerCase();
    const kecoMatches = allKeco.filter(
      (s) => s.name?.toLowerCase().includes(kw) || s.address?.toLowerCase().includes(kw)
    );

    // ChargEV 우선, statId 기준 중복 제거
    const seen = new Set<string>();
    const combined = [...chargevMatches, ...kecoMatches].filter((s) => {
      if (seen.has(s.statId)) return false;
      seen.add(s.statId);
      return true;
    });

    res.json({ success: true, count: combined.length, data: combined });
  } catch (error: any) {
    console.error('Error in GET /v1/stations/search:', error);
    res.status(500).json({
      success: false,
      error: { code: 'SEARCH_ERROR', message: error.message || 'Failed to search stations' },
    });
  }
});

/**
 * GET /v1/stations/map?swLat=&swLng=&neLat=&neLng=
 * 통합 지도 마커: 화면 영역(bounds) 내 KECO + ChargEV 경량 마커. 클러스터링용.
 */
stationRouter.get('/map', async (req: Request, res: Response) => {
  try {
    const swLat = parseFloat(req.query.swLat as string);
    const swLng = parseFloat(req.query.swLng as string);
    const neLat = parseFloat(req.query.neLat as string);
    const neLng = parseFloat(req.query.neLng as string);
    if ([swLat, swLng, neLat, neLng].some((v) => isNaN(v))) {
      return res.status(400).json({
        success: false,
        error: { code: 'INVALID_PARAMETER', message: 'swLat, swLng, neLat, neLng are required' },
      });
    }
    const inBounds = (lat: number, lng: number) =>
      lat >= swLat && lat <= neLat && lng >= swLng && lng <= neLng;

    const [allKeco] = await Promise.all([getStations(undefined, undefined, 1, 3000).catch(() => [])]);
    const kecoMarkers = allKeco
      .filter((s) => s.lat && s.lng && inBounds(s.lat, s.lng))
      .map((s) => ({
        statId: s.statId,
        name: s.name,
        lat: s.lat,
        lng: s.lng,
        available: s.summary.available > 0,
        operatorName: s.operatorName,
        source: 'keco' as const,
      }));

    const chargevMarkers = getAllChargevStations()
      .filter((s) => s.lat && s.lng && inBounds(s.lat, s.lng))
      .map((s) => ({
        statId: s.statId,
        name: s.name,
        lat: s.lat,
        lng: s.lng,
        available: s.summary.available > 0,
        operatorName: s.operatorName,
        source: 'chargev' as const,
      }));

    const markers = [...chargevMarkers, ...kecoMarkers];
    res.setHeader('Cache-Control', 'public, max-age=60');
    res.json({ success: true, count: markers.length, data: markers });
  } catch (error: any) {
    console.error('Error in GET /v1/stations/map:', error);
    res.status(500).json({
      success: false,
      error: { code: 'MAP_ERROR', message: error.message || 'Failed to fetch map markers' },
    });
  }
});

/**
 * GET /v1/stations
 * Query params: zcode, zscode, page, numOfRows
 */
stationRouter.get('/', async (req: Request, res: Response) => {
  try {
    const zcode = req.query.zcode as string | undefined;
    const zscode = req.query.zscode as string | undefined;
    const pageNo = parseInt((req.query.page as string) || '1', 10);
    const numOfRows = parseInt((req.query.numOfRows as string) || '50', 10);

    const stations = await getStations(zcode, zscode, pageNo, numOfRows);
    res.json({
      success: true,
      count: stations.length,
      page: pageNo,
      data: stations,
    });
  } catch (error: any) {
    console.error('Error in GET /v1/stations:', error);
    res.status(500).json({
      success: false,
      error: {
        code: 'UPSTREAM_ERROR',
        message: error.message || 'Failed to fetch stations',
      },
    });
  }
});

/**
 * GET /v1/stations/:statId
 */
stationRouter.get('/map/view', async (req: Request, res: Response) => {
  const zcode = (req.query.zcode as string) || '11';
  const stations = await getStations(zcode, undefined, 1, 100);

  const regionCenters: Record<string, { lat: number; lng: number }> = {
    '11': { lat: 37.5665, lng: 126.9780 }, // 서울
    '41': { lat: 37.2750, lng: 127.0094 }, // 경기
    '28': { lat: 37.4563, lng: 126.7052 }, // 인천
    '26': { lat: 35.1796, lng: 129.0756 }, // 부산
    '27': { lat: 35.8714, lng: 128.6014 }, // 대구
    '30': { lat: 36.3504, lng: 127.3845 }, // 대전
    '29': { lat: 35.1595, lng: 126.8526 }, // 광주
    '31': { lat: 35.5384, lng: 129.3114 }, // 울산
    '50': { lat: 33.4996, lng: 126.5312 }, // 제주
  };

  const center = regionCenters[zcode] || regionCenters['11'];

  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
      <style>
        html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #E2E8F0; }
        .station-pin {
          background: #10B981;
          color: #FFFFFF;
          border: 2px solid #FFFFFF;
          border-radius: 14px;
          padding: 4px 8px;
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          font-weight: bold;
          font-size: 11px;
          box-shadow: 0 4px 10px rgba(0,0,0,0.25);
          white-space: nowrap;
          cursor: pointer;
        }
        .station-pin.busy { background: #6B7280; }
        .leaflet-container { background: #F8FAFC; }
      </style>
    </head>
    <body>
      <div id="map"></div>
      <script>
        var map = L.map('map', { zoomControl: false }).setView([${center.lat}, ${center.lng}], 12);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19
        }).addTo(map);

        var stations = ${JSON.stringify(stations)};
        stations.forEach(function(s) {
          if (s.lat > 0 && s.lng > 0) {
            var isAvailable = s.summary.available > 0;
            var pinClass = isAvailable ? 'station-pin' : 'station-pin busy';
            var htmlStr = '<div class="' + pinClass + '">⚡ ' + s.name + ' (' + s.summary.available + '/' + s.summary.total + ')</div>';
            var customIcon = L.divIcon({
              className: '',
              html: htmlStr,
              iconSize: [120, 28],
              iconAnchor: [60, 14]
            });
            var marker = L.marker([s.lat, s.lng], { icon: customIcon }).addTo(map);
            marker.on('click', function() {
              if (window.AndroidBridge) {
                window.AndroidBridge.onStationClick(s.statId);
              }
            });
          }
        });
      </script>
    </body>
    </html>
  `;

  res.setHeader('Content-Type', 'text/html');
  res.send(html);
});

/**
 * GET /v1/stations/:statId
 */
stationRouter.get('/:statId', async (req: Request, res: Response) => {
  try {
    const { statId } = req.params;
    const zcode = (req.query.zcode as string) || '11';
    const station = await getStationDetail(statId, zcode);
    if (!station) {
      return res.status(404).json({
        success: false,
        error: {
          code: 'NOT_FOUND',
          message: `Station with statId ${statId} not found`,
        },
      });
    }
    res.json({
      success: true,
      data: station,
    });
  } catch (error: any) {
    console.error(`Error in GET /v1/stations/${req.params.statId}:`, error);
    res.status(500).json({
      success: false,
      error: {
        code: 'UPSTREAM_ERROR',
        message: error.message || 'Failed to fetch station detail',
      },
    });
  }
});

/**
 * POST /v1/charger-statuses/batch
 * Body: { keys: Array<{ statId: string, chgerId: string }> }
 */
stationRouter.post('/batch-status', async (req: Request, res: Response) => {
  try {
    const { keys } = req.body;
    if (!Array.isArray(keys)) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'INVALID_PARAMETER',
          message: 'keys must be an array of { statId, chgerId }',
        },
      });
    }

    const results = await getChargerBatchStatus(keys);
    res.json({
      success: true,
      data: results,
    });
  } catch (error: any) {
    console.error('Error in POST /v1/charger-statuses/batch-status:', error);
    res.status(500).json({
      success: false,
      error: {
        code: 'UPSTREAM_ERROR',
        message: error.message || 'Failed to fetch batch charger status',
      },
    });
  }
});
