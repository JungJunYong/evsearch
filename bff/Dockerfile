# ---------- Build Stage ----------
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies (use lockfile for reproducible builds)
COPY package.json package-lock.json ./
RUN npm ci

# Copy source and compile TypeScript (+ copy mock data into dist)
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# ---------- Runtime Stage ----------
FROM node:20-alpine AS runner

ENV NODE_ENV=production
WORKDIR /app

# Run as non-root user for container hardening
RUN addgroup -S nodejs && adduser -S nodejs -G nodejs

# Production dependencies only
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

# Compiled output + bundled mock data
COPY --from=builder /app/dist ./dist

USER nodejs

EXPOSE 4000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:4000/health || exit 1

CMD ["node", "dist/index.js"]
