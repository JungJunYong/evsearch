import NodeCache from 'node-cache';
import { ChargerStatusType } from '../types/ev.js';

export interface ElecveryChargerStatus {
  chargerId: string;
  status: ChargerStatusType;
  statusCode: number;
  fetchedAt: string;
}

const ELECVERY_PLACE_ID = '148D13A4030C44F018C8287B7F39516D';
const CHARGEV_ES_KEY = '502616';
const ELECVERY_PLACE_URL = `https://www.elecvery.com/ko/map/place/${ELECVERY_PLACE_ID}?vendorTypeList=PI`;
const statusCache = new NodeCache({ stdTTL: 180, checkperiod: 30 });

function parseStatus(status: string): Pick<ElecveryChargerStatus, 'status' | 'statusCode'> | null {
  if (status === '충전가능') return { status: 'AVAILABLE', statusCode: 2 };
  if (status === '충전중') return { status: 'CHARGING', statusCode: 3 };
  return null;
}

/**
 * Reads the server-rendered public Elecvery place page.
 * The page contains escaped Next.js RSC data, so this intentionally parses
 * only the small stable chargerId/status pair rather than depending on the UI.
 */
export async function fetchAlfheimStatuses(): Promise<Map<string, ElecveryChargerStatus> | null> {
  const cached = statusCache.get<Map<string, ElecveryChargerStatus>>(ELECVERY_PLACE_ID);
  if (cached) return cached;

  try {
    const response = await fetch(ELECVERY_PLACE_URL, {
      headers: {
        Accept: 'text/html,application/xhtml+xml',
        'User-Agent': 'evsearch-bff/1.0',
      },
      signal: AbortSignal.timeout(7000),
    });
    if (!response.ok) return null;

    const html = await response.text();
    // Confirm the response is the requested place without depending on the
    // exact escaping used by a particular Next.js deployment.
    if (!html.includes(ELECVERY_PLACE_ID)) return null;

    const statuses = new Map<string, ElecveryChargerStatus>();
    const pattern = /chargerId.{0,80}?(\d+).{0,300}?status.{0,20}?(충전가능|충전중)/g;
    const fetchedAt = new Date().toISOString();
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(html)) !== null) {
      const parsed = parseStatus(match[2]);
      if (parsed) {
        statuses.set(String(Number(match[1])), {
          chargerId: String(Number(match[1])),
          ...parsed,
          fetchedAt,
        });
      }
    }

    if (statuses.size === 0) return null;
    statusCache.set(ELECVERY_PLACE_ID, statuses);
    return statuses;
  } catch (error) {
    console.warn('[Elecvery] Failed to fetch public place status:', error);
    return null;
  }
}

/** The only external station mapping enabled for now. */
export function isAlfheimChargevStation(statId: string): boolean {
  return statId === `CHARGEV_${CHARGEV_ES_KEY}`;
}

/**
 * ChargEV sometimes supplies a physical number such as 110537 while
 * Elecvery exposes the terminal sequence as 37. Compare the exact numeric
 * value first, then the final two digits for that known station format.
 */
export function matchingElecveryId(chgerId: string, statuses: Map<string, ElecveryChargerStatus>): string | null {
  const numeric = String(Number(chgerId));
  if (statuses.has(numeric)) return numeric;

  const suffix = chgerId.replace(/\D/g, '').slice(-2);
  const normalizedSuffix = String(Number(suffix));
  return statuses.has(normalizedSuffix) ? normalizedSuffix : null;
}
