import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';
import { stationRouter } from './routes/stationRoutes.js';

// Load .env from root or bff directory
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config();

const app = express();
const PORT = process.env.PORT || 4000;
const API_SECRET_KEY = process.env.API_SECRET_KEY || 'evsearch-sec-2026-v1-key';

app.use(cors());
app.use(express.json());

// API Key Authentication Middleware
const apiKeyAuthMiddleware = (req: express.Request, res: express.Response, next: express.NextFunction) => {
  const clientKey = req.headers['x-api-key'] || req.headers['X-API-Key'];
  if (!clientKey || clientKey !== API_SECRET_KEY) {
    return res.status(401).json({
      success: false,
      error: {
        code: 'UNAUTHORIZED',
        message: 'Invalid or missing X-API-Key header',
      },
    });
  }
  next();
};

// Health check endpoint (Public)
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Protect API routes with API Key middleware
app.use('/v1/stations', apiKeyAuthMiddleware, stationRouter);

app.listen(PORT, () => {
  console.log(`⚡ EV Search BFF API Server running on http://localhost:${PORT}`);
});
