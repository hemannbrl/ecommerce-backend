# Build context is the repo root (see docker-compose), so shared api/ is available.
FROM node:22-slim

# Prisma needs OpenSSL at runtime.
RUN apt-get update && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY express/package*.json ./
RUN npm ci --omit=dev

COPY express/prisma ./prisma
RUN npx prisma generate

COPY express/src ./src
COPY api ./api
ENV OPENAPI_PATH=/app/api/openapi.yaml

EXPOSE 3000
CMD ["node", "src/server.js"]
