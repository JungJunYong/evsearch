import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { initializeApp, cert, getApps } from 'firebase-admin/app';
import { getMessaging, Messaging } from 'firebase-admin/messaging';
import { getChargerBatchStatus } from './kecoService.js';

/**
 * 빈자리 알림 + 위젯 실시간 동기화 (서버 주도 + FCM).
 *
 * 폴링 규칙
 * - **알림을 켠 구독만** 조회한다(앱은 알림을 끄면 구독을 해지한다).
 * - 사용자가 정한 **감시 시간 범위 안에서만** 조회한다.
 * - 조회 간격은 매 회차마다 **30~60초 무작위**. 실시간성을 유지하면서 업스트림 과호출과
 *   동시 요청 몰림(thundering herd)을 함께 피한다.
 * - 조회 결과가 **이전과 달라졌을 때만** 푸시를 보낸다.
 *   · notify=true 대상이 빈자리로 전환 → 알림 푸시(+data)
 *   · 그 밖의 상태 변화 → 데이터 전용 푸시(type=widget_sync)로 앱이 위젯만 갱신
 *
 * 시간 범위 밖이거나 알림이 꺼져 있으면 서버는 아무것도 조회하지 않는다. 그 구간의 갱신은
 * 앱의 15분 고정 주기와 사용자의 새로고침이 담당한다.
 *
 * Firebase 서비스계정 키는 FIREBASE_SERVICE_ACCOUNT(파일 경로 또는 JSON 문자열)로 주입한다.
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ALERTS_FILE = path.join(__dirname, '..', 'data', 'alerts.json');

/** 회차별 무작위 조회 간격(초). 실시간성 ↔ 과호출 방지의 균형점. */
export const MIN_INTERVAL_SEC = 30;
export const MAX_INTERVAL_SEC = 60;
/** 폴링 루프 tick. 다음 조회 시점은 tick 안에서 판정한다. */
const TICK_MS = 5_000;

/** 30~60초 사이 무작위 지연(ms). */
function nextDelayMs(): number {
  const span = MAX_INTERVAL_SEC - MIN_INTERVAL_SEC;
  return (MIN_INTERVAL_SEC + Math.random() * span) * 1000;
}

export interface WatchKey {
  statId: string;
  chgerId: string;
  /** true 면 빈자리 전환 시 알림 푸시. false 면 위젯 동기화 전용. */
  notify: boolean;
}

export interface AlertSubscription {
  token: string;                 // FCM 등록 토큰
  keys: WatchKey[];              // 감시 단말기 (즐겨찾기 + 위젯 목록)
  startMin: number;              // 감시 시작 (0~1439, KST 분). start == end 면 종일
  endMin: number;                // 감시 종료
  intervalSec: number;           // (하위 호환) 앱이 보내는 값. 실제 간격은 서버가 30~60초 무작위로 정한다
  enabled: boolean;
  /** 상태 변화 시 데이터 전용 푸시로 위젯을 깨울지 여부 */
  silentSync: boolean;
  updatedAt: string;
}

const subscriptions = new Map<string, AlertSubscription>();
// 직전 상태 (token|statId:chgerId -> status). 빈자리 '전환'만 알리기 위함.
const lastStatus = new Map<string, string>();
/** token -> 다음 조회 예정 시각(epoch ms). 회차마다 무작위로 재설정한다. */
const nextPollAt = new Map<string, number>();
// 데이터 푸시 과다 전송 방지 (token -> epoch ms)
const lastSyncPushAt = new Map<string, number>();
const SYNC_PUSH_MIN_GAP_MS = 20_000;

// ---- 영속 ----------------------------------------------------------------
function normalizeSub(raw: any): AlertSubscription {
  const keys: WatchKey[] = Array.isArray(raw?.keys)
    ? raw.keys
        .filter((k: any) => k?.statId && k?.chgerId)
        .map((k: any) => ({
          statId: String(k.statId),
          chgerId: String(k.chgerId),
          // 구버전 페이로드(notify 없음)는 알림 대상으로 본다.
          notify: k.notify !== false,
        }))
    : [];
  return {
    token: String(raw.token),
    keys,
    startMin: Number.isFinite(raw?.startMin) ? Number(raw.startMin) : 0,
    endMin: Number.isFinite(raw?.endMin) ? Number(raw.endMin) : 0,
    intervalSec: clampInterval(raw?.intervalSec),
    enabled: raw?.enabled !== false,
    silentSync: raw?.silentSync !== false,
    updatedAt: raw?.updatedAt || new Date().toISOString(),
  };
}

function clampInterval(v: any): number {
  // 보관만 한다(진단용). 실제 조회 간격은 nextDelayMs()가 정한다.
  const n = Number.isFinite(v) ? Number(v) : 60;
  return Math.min(1800, Math.max(1, n));
}

function loadSubs(): void {
  try {
    if (fs.existsSync(ALERTS_FILE)) {
      const raw = JSON.parse(fs.readFileSync(ALERTS_FILE, 'utf-8')) as any[];
      for (const s of raw) {
        if (!s?.token) continue;
        subscriptions.set(String(s.token), normalizeSub(s));
      }
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
  const normalized = normalizeSub({ ...sub, updatedAt: new Date().toISOString() });
  subscriptions.set(normalized.token, normalized);
  persistSubs();
}

export function removeSubscription(token: string): void {
  subscriptions.delete(token);
  for (const k of Array.from(lastStatus.keys())) {
    if (k.startsWith(token + '|')) lastStatus.delete(k);
  }
  nextPollAt.delete(token);
  lastSyncPushAt.delete(token);
  persistSubs();
}

export function subscriptionStats() {
  let notifyKeys = 0;
  let watchKeys = 0;
  for (const s of subscriptions.values()) {
    for (const k of s.keys) {
      watchKeys += 1;
      if (k.notify) notifyKeys += 1;
    }
  }
  return { subscriptions: subscriptions.size, watchKeys, notifyKeys };
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
    // 알림을 켠 구독만, 그리고 감시 시간 범위 안에서만 조회한다.
    if (!sub.enabled || sub.keys.length === 0) continue;
    if (!sub.keys.some((k) => k.notify)) continue;
    if (!inWindow(sub.startMin, sub.endMin, now)) {
      // 시간대를 벗어나면 다음 진입 시 곧바로 한 번 보도록 예정 시각을 비운다.
      nextPollAt.delete(sub.token);
      continue;
    }

    // 회차마다 30~60초 무작위 간격
    const due = nextPollAt.get(sub.token);
    if (due !== undefined && nowMs < due) continue;
    nextPollAt.set(sub.token, nowMs + nextDelayMs());

    try {
      // 캐시 나이를 최소 간격의 절반으로 제한해 준실시간을 보장한다.
      const results = await getChargerBatchStatus(
        sub.keys.map((k) => ({ statId: k.statId, chgerId: k.chgerId })),
        { maxAgeMs: (MIN_INTERVAL_SEC * 1000) / 2 }
      );

      const vacancies: Array<{ statId: string; chgerId: string }> = [];
      let changed = false;

      for (const key of sub.keys) {
        const r = results[`${key.statId}:${key.chgerId}`];
        if (!r) continue;
        const k = `${sub.token}|${key.statId}:${key.chgerId}`;
        const prev = lastStatus.get(k);
        if (prev !== undefined && prev !== r.status) changed = true;
        // non-AVAILABLE -> AVAILABLE 전환만 알림 (첫 관측은 baseline)
        if (key.notify && prev !== undefined && prev !== 'AVAILABLE' && r.status === 'AVAILABLE') {
          vacancies.push({ statId: key.statId, chgerId: key.chgerId });
        }
        lastStatus.set(k, r.status);
      }

      // 변화가 있을 때만 내려보낸다.
      if (vacancies.length > 0) {
        await sendVacancyPush(sub.token, vacancies);
        lastSyncPushAt.set(sub.token, nowMs);
      } else if (changed && sub.silentSync) {
        const lastSync = lastSyncPushAt.get(sub.token) || 0;
        if (nowMs - lastSync >= SYNC_PUSH_MIN_GAP_MS) {
          lastSyncPushAt.set(sub.token, nowMs);
          await sendWidgetSyncPush(sub.token);
        }
      }
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
  const title = count === 1 ? '충전기 빈자리' : `빈자리 ${count}대`;
  const body =
    count === 1
      ? `${first.chgerId} 단말기가 지금 충전 가능합니다.`
      : `${first.chgerId} 외 ${count - 1}대가 지금 충전 가능합니다.`;

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
        title,
        body,
      },
    });
    console.log(`[Alert] pushed to ${token.slice(0, 8)}…: ${body}`);
  } catch (e: any) {
    handleSendError(token, e);
  }
}

/** 데이터 전용 푸시: 알림 표시 없이 앱이 위젯만 즉시 갱신한다. */
async function sendWidgetSyncPush(token: string): Promise<void> {
  const m = ensureMessaging();
  if (!m) return;
  try {
    await m.send({
      token,
      android: { priority: 'high' },
      data: { type: 'widget_sync', at: String(Date.now()) },
    });
    console.log(`[Alert] widget_sync pushed to ${token.slice(0, 8)}…`);
  } catch (e: any) {
    handleSendError(token, e);
  }
}

function handleSendError(token: string, e: any): void {
  // 토큰 만료/무효 시 구독 제거
  const code = e?.errorInfo?.code || e?.code;
  if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-argument') {
    console.warn(`[Alert] stale token removed: ${token.slice(0, 8)}…`);
    removeSubscription(token);
  } else {
    console.warn('[Alert] send failed:', e);
  }
}

let pollTimer: NodeJS.Timeout | null = null;
/** 5초 tick으로 돌며 구독별 다음 조회 시점(30~60초 무작위)을 지킨다. */
export function startAlertPolling(): void {
  if (pollTimer) return;
  ensureMessaging(); // 초기화 시도(로그)
  pollTimer = setInterval(() => {
    pollAndNotify().catch((e) => console.warn('[Alert] poll cycle error:', e));
  }, TICK_MS);
  console.log(
    `[Alert] polling started (${TICK_MS / 1000}s tick, ${MIN_INTERVAL_SEC}~${MAX_INTERVAL_SEC}s randomized per subscription, alert-on only)`
  );
}
