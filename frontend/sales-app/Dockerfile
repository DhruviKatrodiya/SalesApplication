# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app

# Install deps (cached unless lockfile changes)
COPY package.json package-lock.json ./
RUN npm ci

# Build the Angular app in production mode
COPY . ./
RUN npm run build -- --configuration production

# ---- Runtime stage (nginx) ----
FROM nginx:1.27-alpine AS final
# SPA + /api reverse proxy config
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Angular 22 emits the browser bundle under dist/sales-app/browser
COPY --from=build /app/dist/sales-app/browser /usr/share/nginx/html

EXPOSE 80
