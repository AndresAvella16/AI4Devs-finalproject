# syntax=docker/dockerfile:1
# Imagen del frontend (JP-OPS-01). Build multi-stage: Vite con Node 22 → nginx sirviendo los estáticos.
# Los tests y el lint no se ejecutan aquí: los ejecuta la CI.

# ---- Build ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
# Ruta relativa por defecto: nginx reenvía /api al backend (ver nginx.conf), así que no hay CORS.
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---- Runtime ----
FROM nginx:1.28-alpine

# El HEALTHCHECK usa wget. La imagen base lo trae (busybox), pero no lo garantiza: si falta, se instala.
RUN command -v wget >/dev/null || apk add --no-cache wget

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
