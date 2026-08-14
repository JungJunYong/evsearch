import { Router, Request, Response } from 'express';
import { upsertSubscription, removeSubscription, AlertSubscription } from '../services/alertService.js';

export const alertRouter = Router();

/**
 * POST /v1/alerts/subscribe
 * body: { token, keys:[{statId,chgerId}], startMin, endMin, intervalSec, enabled }
 * 앱이 FCM 토큰 + 감시 대상 + 시간대를 등록/갱신한다.
 */
alertRouter.post('/subscribe', (req: Request, res: Response) => {
  try {
    const { token, keys, startMin, endMin, intervalSec, enabled } = req.body || {};
    if (!token || typeof token !== 'string') {
      return res.status(400).json({ success: false, error: { code: 'INVALID_PARAMETER', message: 'token is required' } });
    }
    if (!Array.isArray(keys)) {
      return res.status(400).json({ success: false, error: { code: 'INVALID_PARAMETER', message: 'keys must be an array' } });
    }
    const sub: AlertSubscription = {
      token,
      keys: keys.filter((k: any) => k?.statId && k?.chgerId).map((k: any) => ({ statId: String(k.statId), chgerId: String(k.chgerId) })),
      startMin: Number.isFinite(startMin) ? startMin : 0,
      endMin: Number.isFinite(endMin) ? endMin : 0,
      intervalSec: Number.isFinite(intervalSec) ? intervalSec : 90,
      enabled: enabled !== false,
      updatedAt: new Date().toISOString(),
    };
    upsertSubscription(sub);
    res.json({ success: true });
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
