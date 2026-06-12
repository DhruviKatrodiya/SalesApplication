---
name: docker-deploy-workflow
description: How the SalesApp runs in Docker and the stale-build gotcha when redeploying frontend changes
metadata:
  type: project
---

The app runs via `docker-compose.yml` at repo root: SQL Server (`db`) + .NET API (`backend`, port 8080) + Angular/nginx (`frontend`). Frontend is served on host port **80** at `http://192.168.1.26` (LAN IP, DHCP — can change). nginx proxies `/api` → `backend:8080`, so the prod build's relative `apiBaseUrl: '/api'` works same-origin. A "SalesApp Docker" Windows firewall rule (TCP 80,8080) allows LAN access.

**Stale-build gotcha (hit twice):** after editing frontend source, `docker compose build frontend` (and even `--no-cache` once) sometimes ships the OLD bundle — the deployed `styles-*.css`/JS lacks the change. Verify by exec-ing into the container and grepping the bundle (`docker exec salesapp-frontend cat /usr/share/nginx/html/styles-*.css`) or checking `docker images salesapplication-frontend` CreatedSince. **Reliable fix:** stop+rm the container, `docker rmi salesapplication-frontend:latest`, then `docker compose build frontend` (watch for the `npm run build` step actually running) and `docker compose up -d frontend`. Always hard-refresh the browser (Ctrl+Shift+R) after — the old hashed bundle is cached.
