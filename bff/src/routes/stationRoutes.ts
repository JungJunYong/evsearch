import { Router, Request, Response } from 'express';
import {
  getStations,
  getStationDetail,
  getChargerBatchStatus,
} from '../services/kecoService.js';
import {
  searchChargevStations,
  getChargevByChargerNumber,
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
