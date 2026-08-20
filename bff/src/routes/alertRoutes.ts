import { Router, Request, Response } from 'express';
import {
  upsertSubscription,
  removeSubscription,
  subscriptionStats,
  AlertSubscription,
  MIN_INTERVAL_SEC,
  MAX_INTERVAL_SEC,
} from '../services/alertService.js';

export const alertRouter = Router();

/**
 * POST /v1/alerts/subscribe
 * body: {
 *   token,
 *   keys: [{ statId, chgerId, notify }],   // notify 생략 시 true (구버전 호환)
 *   startMin, endMin,                       // 감시 시간 범위 (start == end 이면 종일)
 *   intervalSec,                            // 확인 주기 (서버가 30~1800초로 clamp)
 *   enabled, silentSync
 * }
 * 앱이 FCM 토큰 + 감시 대상 + 시간 범위 + 주기를 등록/갱신한다.
 */
alertRouter.post('/subscribe', (req: Request, res: Response) => {
  try {
    const { token, keys, startMin, endMin, intervalSec, enabled, silentSync } = req.body || {};
    if (!token || typeof token !== 'string') {
      return res.status(400).json({ success: false, error: { code: 'INVALID_PARAMETER', message: 'token is required' } });
    }
    if (!Array.isArray(keys)) {
      return res.status(400).json({ success: false, error: { code: 'INVALID_PARAMETER', message: 'keys must be an array' } });
    }
    const sub: AlertSubscription = {
      token,
      keys: keys
        .filter((k: any) => k?.statId && k?.chgerId)
        .map((k: any) => ({
          statId: String(k.statId),
          chgerId: String(k.chgerId),
          notify: k?.notify !== false,
        })),
      startMin: Number.isFinite(startMin) ? startMin : 0,
      endMin: Number.isFinite(endMin) ? endMin : 0,
      intervalSec: Number.isFinite(intervalSec) ? intervalSec : 60,
      enabled: enabled !== false,
      silentSync: silentSync !== false,
      updatedAt: new Date().toISOString(),
    };
    upsertSubscription(sub);
    res.json({ success: true, data: { intervalSec: Math.min(MAX_INTERVAL_SEC, Math.max(MIN_INTERVAL_SEC, sub.intervalSec)), keys: sub.keys.length } });
  } catch (error: any) {
    console.error('Error in POST /v1/alerts/subscribe:', error);
    res.status(500).json({ success: false, error: { code: 'ALERT_ERROR', message: error.message || 'subscribe failed' } });
  }
});

/**
 * POST /v1/alerts/unsubscribe  body: { token }
 */
alertRouter.post('/unsubscribe', (req: Request, res: Response) => {
  try {
    const { token } = req.body || {};
    if (!token) return res.status(400).json({ success: false, error: { code: 'INVALID_PARAMETER', message: 'token is required' } });
    removeSubscription(token);
    res.json({ success: true });
  } catch (error: any) {
    console.error('Error in POST /v1/alerts/unsubscribe:', error);
    res.status(500).json({ success: false, error: { code: 'ALERT_ERROR', message: error.message || 'unsubscribe failed' } });
  }
});

/** GET /v1/alerts/stats — 감시 규모 확인용(운영 점검). */
alertRouter.get('/stats', (_req: Request, res: Response) => {
  res.json({ success: true, data: { ...subscriptionStats(), minIntervalSec: MIN_INTERVAL_SEC, maxIntervalSec: MAX_INTERVAL_SEC } });
});
