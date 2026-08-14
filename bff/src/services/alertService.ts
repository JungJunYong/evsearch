import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { initializeApp, cert, getApps } from 'firebase-admin/app';
import { getMessaging, Messaging } from 'firebase-admin/messaging';
import { getChargerBatchStatus } from './kecoService.js';

/**
 * 빈자리 알림(서버 주도 + FCM).
 *
 * 앱이 FCM 토큰 + 감시 대상 단말기 + 시간대를 등록하면, BFF가 주기적으로
 * 상태를 조회해 '빈자리(AVAILABLE) 전환'을 감지하고 해당 토큰으로 FCM 푸시를 보낸다.
 * Firebase 서비스계정 키는 FIREBASE_SERVICE_ACCOUNT(파일 경로 또는 JSON 문자열)로 주입한다.
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ALERTS_FILE = path.join(__dirname, '..', 'data', 'alerts.json');

export interface AlertSubscription {
  token: string;                                   // FCM 등록 토큰
  keys: Array<{ statId: string; chgerId: string }>; // 감시 단말기 (즐겨찾기)
  startMin: number;                                // 감시 시작 (0~1439, 로컬 분)
  endMin: number;                                  // 감시 종료
  intervalSec: number;                             // 조회 간격(60~180)
  enabled: boolean;
  updatedAt: string;
}

const subscriptions = new Map<string, AlertSubscription>();
// 직전 상태 (token|statId:chgerId -> status). 빈자리 '전환'만 알리기 위함.
const lastStatus = new Map<string, string>();
const lastPolledAt = new Map<string, number>();

// ---- 영속 ----------------------------------------------------------------
function loadSubs(): void {
  try {
    if (fs.existsSync(ALERTS_FILE)) {
      const raw = JSON.parse(fs.readFileSync(ALERTS_FILE, 'utf-8')) as AlertSubscription[];
      for (const s of raw) subscriptions.set(s.token, s);
      console.log(`[Alert] loaded ${subscriptions.size} subscriptions`);
    }
  } catch (e) {
    console.warn('[Alert] load failed:', e);
  }
}
let saveTimer: NodeJS.Timeout | null = null;
function persistSubs(): void {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      fs.mkdirSync(path.dirname(ALERTS_FILE), { recursive: true });
      fs.writeFileSync(ALERTS_FILE, JSON.stringify(Array.from(subscriptions.values())), 'utf-8');
    } catch (e) {
      console.warn('[Alert] persist failed:', e);
    }
  }, 1500);
}
loadSubs();

// ---- Firebase Admin (lazy) ----------------------------------------------
let messaging: Messaging | null = null;
let firebaseTried = false;

function ensureMessaging(): Messaging | null {
  if (firebaseTried) return messaging;
  firebaseTried = true;
  const key = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!key) {
    console.warn('[Alert] FIREBASE_SERVICE_ACCOUNT not set — push disabled (subscriptions still stored)');
    return null;
  }
  try {
    const cred = key.trim().startsWith('{')
      ? JSON.parse(key)
      : JSON.parse(fs.readFileSync(key, 'utf-8'));
    if (getApps().length === 0) {
      initializeApp({ credential: cert(cred) });
    }
    messaging = getMessaging();
    console.log('[Alert] Firebase Admin initialized — FCM push enabled');
  } catch (e) {
    console.warn('[Alert] Firebase init failed:', e);
    messaging = null;
  }
  return messaging;
}

// ---- 구독 관리 -----------------------------------------------------------
export function upsertSubscription(sub: AlertSubscription): void {
  sub.updatedAt = new Date().toISOString();
  sub.intervalSec = Math.min(180, Math.max(60, sub.intervalSec || 90));
  subscriptions.set(sub.token, sub);
  persistSubs();
}

export function removeSubscription(token: string): void {
  subscriptions.delete(token);
  for (const k of Array.from(lastStatus.keys())) {
    if (k.startsWith(token + '|')) lastStatus.delete(k);
  }
  lastPolledAt.delete(token);
  persistSubs();
}

// ---- 시간대 판정 ---------------------------------------------------------
function nowLocalMinutes(): number {
  // 서버 TZ 무관하게 KST 기준 분
  const kst = new Date(Date.now() + 9 * 60 * 60 * 1000);
  return kst.getUTCHours() * 60 + kst.getUTCMinutes();
}

function inWindow(startMin: number, endMin: number, now: number): boolean {
  if (startMin === endMin) return true; // 종일
  if (startMin < endMin) return now >= startMin && now < endMin;
  return now >= startMin || now < endMin; // 자정 넘김
}

// ---- 폴링 & 알림 ---------------------------------------------------------
export async function pollAndNotify(): Promise<void> {
  if (subscriptions.size === 0) return;
  const now = nowLocalMinutes();
  const nowMs = Date.now();

  for (const sub of subscriptions.values()) {
    if (!sub.enabled || sub.keys.length === 0) continue;
    if (!inWindow(sub.startMin, sub.endMin, now)) continue;

    // 구독별 조회 간격 준수
    const last = lastPolledAt.get(sub.token) || 0;
    if (nowMs - last < sub.intervalSec * 1000) continue;
    lastPolledAt.set(sub.token, nowMs);

    try {
      const results = await getChargerBatchStatus(sub.keys);
      const fresh: Array<{ statId: string; chgerId: string; name?: string }> = [];

      for (const key of sub.keys) {
        const r = results[`${key.statId}:${key.chgerId}`];
        if (!r) continue;
        const k = `${sub.token}|${key.statId}:${key.chgerId}`;
        const prev = lastStatus.get(k);
        // non-AVAILABLE -> AVAILABLE 전환만 알림 (첫 관측은 baseline)
        if (prev !== undefined && prev !== 'AVAILABLE' && r.status === 'AVAILABLE') {
          fresh.push({ statId: key.statId, chgerId: key.chgerId });
        }
        lastStatus.set(k, r.status);
      }

      if (fresh.length > 0) await sendVacancyPush(sub.token, fresh);
    } catch (e) {
      console.warn(`[Alert] poll failed for token ${sub.token.slice(0, 8)}…:`, e);
    }
  }
}

async function sendVacancyPush(
  token: string,
  chargers: Array<{ statId: string; chgerId: string }>
): Promise<void> {
  const m = ensureMessaging();
  const count = chargers.length;
  const first = chargers[0];
  const title = count === 1 ? '🔌 충전기 빈자리 발생' : `🔌 빈자리 ${count}대 발생`;
  const body =
    count === 1
      ? `${first.chgerId}번 단말기가 지금 충전 가능합니다.`
      : `${first.chgerId}번 외 ${count - 1}대가 지금 충전 가능합니다.`;

  if (!m) {
    console.log(`[Alert] (push disabled) would notify ${token.slice(0, 8)}…: ${body}`);
    return;
  }
  try {
    await m.send({
      token,
      notification: { title, body },
      android: {
        priority: 'high',
        notification: { channelId: 'vacancy_alert', sound: 'default' },
      },
      data: {
        type: 'vacancy',
        statId: first.statId,
        chgerId: first.chgerId,
        count: String(count),
      },
    });
    console.log(`[Alert] pushed to ${token.slice(0, 8)}…: ${body}`);
  } catch (e: any) {
    // 토큰 만료/무효 시 구독 제거
    const code = e?.errorInfo?.code || e?.code;
    if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-argument') {
      console.warn(`[Alert] stale token removed: ${token.slice(0, 8)}…`);
      removeSubscription(token);
    } else {
      console.warn('[Alert] send failed:', e);
    }
  }
}

let pollTimer: NodeJS.Timeout | null = null;
/** 60초마다 폴링(구독별 간격은 pollAndNotify 내부에서 준수). */
export function startAlertPolling(): void {
  if (pollTimer) return;
  ensureMessaging(); // 초기화 시도(로그)
  pollTimer = setInterval(() => {
    pollAndNotify().catch((e) => console.warn('[Alert] poll cycle error:', e));
  }, 60_000);
  console.log('[Alert] polling started (60s cycle)');
}
