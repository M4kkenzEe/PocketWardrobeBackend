---
description: Deploy PocketWardrobe backend services to production. Runs git check, multi-arch Docker build, DockerHub push, and server update via SSH. Supports full deploy, build-only, server-only, log check, and container status.
allowed-tools: Bash
---

# PocketWardrobe — Deploy

## Project Overview

| Component | DockerHub image |
|---|---|
| Backend (Go/Node) | `yura91191/pocketwardrobe-backend` |
| Recommendation service | `yura91191/recommendation-service` |
| Remove BG service | `yura91191/removebg-service` |
| Fashion analyzer | `yura91191/fashion-analyzer` |
| Database | postgres (no build needed) |

**Server:** `root@194.87.190.248`
**Platforms:** `linux/amd64,linux/arm64`

---

## Deploy Workflow (full)

Run this when the user wants to deploy. Execute steps in order, stop and report on any failure.

### Step 1 — Git check
```bash
git status --short
git log --oneline -3
```
- If there are uncommitted changes → warn the user and ask whether to proceed
- Show the current commit SHA that will be deployed

### Step 2 — Build multi-arch images
```bash
# Ensure buildx builder exists
docker buildx inspect pw-builder || docker buildx create --name pw-builder --use

# Build all 4 images (one command per service, run sequentially)
docker buildx build --platform linux/amd64,linux/arm64 \
  -t yura91191/pocketwardrobe-backend:latest --push \
  /Users/chenigovtsev2001mail.ru/Wardrobe/PocketWardrobeBackend

docker buildx build --platform linux/amd64,linux/arm64 \
  -t yura91191/recommendation-service:latest --push \
  /Users/chenigovtsev2001mail.ru/Wardrobe/RecommendationService

docker buildx build --platform linux/amd64,linux/arm64 \
  -t yura91191/removebg-service:latest --push \
  /Users/chenigovtsev2001mail.ru/Wardrobe/RemoveBgServiceAi

docker buildx build --platform linux/amd64,linux/arm64 \
  -t yura91191/fashion-analyzer:latest --push \
  /Users/chenigovtsev2001mail.ru/Wardrobe/ai_analyzer
```
- Report each image as ✓ or ✗

### Step 3 — Update server
```bash
ssh root@194.87.190.248 '
  cd ~ &&
  docker compose pull &&
  docker compose up -d --remove-orphans &&
  docker image prune -f &&
  docker compose ps
'
```

### Step 4 — Verify
```bash
ssh root@194.87.190.248 'docker compose logs --tail=30'
```
- Show container status table
- Highlight any containers that are not in `running` state

---

## Partial workflows

| Command | What to do |
|---|---|
| "собери образы" / "build only" | Only Steps 1–2, skip server |
| "обнови сервер" / "server only" | Only Step 3–4, skip build |
| "проверь логи" / "check logs" | SSH and show last 50 lines of logs |
| "статус контейнеров" | SSH and run `docker compose ps` |

---

## One-liner (uses deploy.sh)
```bash
/Users/chenigovtsev2001mail.ru/Wardrobe/PocketWardrobeBackend/.claude/scripts/deploy.sh              # full deploy
/Users/chenigovtsev2001mail.ru/Wardrobe/PocketWardrobeBackend/.claude/scripts/deploy.sh --skip-push  # build only, no push
/Users/chenigovtsev2001mail.ru/Wardrobe/PocketWardrobeBackend/.claude/scripts/deploy.sh --server-only # update server only
```

---

## Error handling rules

1. **Build fails** → stop immediately, show full error, do NOT proceed to push/deploy
2. **Push fails** → check `docker login` status, remind user to run `docker login`
3. **SSH fails** → check if server is reachable: `ping 194.87.190.248 -c 3`
4. **Container unhealthy after deploy** → run `docker compose logs <service>` automatically

---

## Security note
Never print or log the SSH private key. Never commit `.env` files.
If asked to add secrets → remind user to use `docker secret` or `.env` excluded in `.gitignore`.
