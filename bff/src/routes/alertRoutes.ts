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
 *   intervalSec,                            // (하위 호환) 실제 간격은 서버가 30~60초 무작위로 정한다
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
    res.json({
      success: true,
      data: {
        keys: sub.keys.length,
        notifyKeys: sub.keys.filter((k) => k.notify).length,
        pollIntervalSec: `${MIN_INTERVAL_SEC}~${MAX_INTERVAL_SEC} (random)`,
      },
    });
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
  res.json({
    success: true,
    data: { ...subscriptionStats(), pollMinSec: MIN_INTERVAL_SEC, pollMaxSec: MAX_INTERVAL_SEC },
  });
});
