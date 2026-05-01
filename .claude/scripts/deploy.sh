#!/bin/bash
# =============================================================
# PocketWardrobe — Deploy Script
# Usage: ./scripts/deploy.sh [--skip-push] [--server-only]
# =============================================================

set -e  # exit on any error

# ── Colours ──────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

log()     { echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $1"; }
success() { echo -e "${GREEN}✓${NC} $1"; }
warn()    { echo -e "${YELLOW}⚠${NC} $1"; }
error()   { echo -e "${RED}✗ ERROR:${NC} $1"; exit 1; }
section() { echo -e "\n${BOLD}━━━ $1 ━━━${NC}"; }

# ── Config ───────────────────────────────────────────────────
DOCKERHUB_USER="yura91191"
SERVER="root@194.87.190.248"
PLATFORMS="linux/amd64,linux/arm64"

PROJECT_ROOT="/Users/chenigovtsev2001mail.ru/Wardrobe"

# Map: DockerHub image name → local directory inside PROJECT_ROOT
declare -A SERVICE_DIRS=(
  ["pocketwardrobe-backend"]="PocketWardrobeBackend"
  ["recommendation-service"]="RecommendationService"
  ["removebg-service"]="RemoveBgServiceAi"
  ["fashion-analyzer"]="ai_analyzer"
)

IMAGES=(
  "pocketwardrobe-backend"
  "recommendation-service"
  "removebg-service"
  "fashion-analyzer"
)

# ── Flags ────────────────────────────────────────────────────
SKIP_PUSH=false
SERVER_ONLY=false

for arg in "$@"; do
  case $arg in
    --skip-push)   SKIP_PUSH=true ;;
    --server-only) SERVER_ONLY=true ;;
  esac
done

# ── Detect git tag / version ──────────────────────────────────
GIT_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
GIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "local")
TAG="${GIT_TAG:-latest}"

echo -e "\n${BOLD}╔══════════════════════════════════════╗"
echo -e "║   PocketWardrobe Deploy — ${TAG}   "
echo -e "╚══════════════════════════════════════╝${NC}"
log "Commit: ${GIT_SHA} | Platforms: ${PLATFORMS}"

# ═══════════════════════════════════════
# STEP 1 — Git status check
# ═══════════════════════════════════════
if [ "$SERVER_ONLY" = false ]; then
  section "1 / 4  Git"

  if ! git diff-index --quiet HEAD -- 2>/dev/null; then
    warn "You have uncommitted changes:"
    git status --short
    echo ""
    read -p "Continue anyway? (y/N): " confirm
    [[ "$confirm" =~ ^[Yy]$ ]] || error "Aborted. Commit your changes first."
  else
    success "Working tree clean (${GIT_SHA})"
  fi

  # ═════════════════════════════════════
  # STEP 2 — Build multi-arch images
  # ═════════════════════════════════════
  section "2 / 4  Docker Build (${PLATFORMS})"

  # Ensure buildx builder exists
  if ! docker buildx inspect pw-builder &>/dev/null; then
    log "Creating buildx builder..."
    docker buildx create --name pw-builder --use
    docker buildx inspect --bootstrap
  else
    docker buildx use pw-builder
  fi

  BUILD_FAILED=()

  for IMAGE in "${IMAGES[@]}"; do
    log "Building ${DOCKERHUB_USER}/${IMAGE}:${TAG} ..."

    # Resolve the build context from the map
    CONTEXT_DIR="${PROJECT_ROOT}/${SERVICE_DIRS[$IMAGE]}"

    if docker buildx build \
        --platform "${PLATFORMS}" \
        --tag "${DOCKERHUB_USER}/${IMAGE}:${TAG}" \
        --tag "${DOCKERHUB_USER}/${IMAGE}:latest" \
        $( [ "$SKIP_PUSH" = false ] && echo "--push" || echo "--load" ) \
        "${CONTEXT_DIR}"; then
      success "${IMAGE}"
    else
      BUILD_FAILED+=("${IMAGE}")
      error "Build failed for ${IMAGE}"
    fi
  done

  if [ ${#BUILD_FAILED[@]} -gt 0 ]; then
    error "These services failed to build: ${BUILD_FAILED[*]}"
  fi

  # ═════════════════════════════════════
  # STEP 3 — Push (if --skip-push not set)
  # ═════════════════════════════════════
  if [ "$SKIP_PUSH" = true ]; then
    section "3 / 4  Push — SKIPPED (--skip-push)"
    warn "Images were built locally only (--load). Push skipped."
  else
    section "3 / 4  Push to DockerHub — DONE"
    success "All images pushed during build step (--push flag)"
  fi
fi  # end SERVER_ONLY check

# ═══════════════════════════════════════
# STEP 4 — Deploy on server
# ═══════════════════════════════════════
section "4 / 4  Deploy on Server (${SERVER})"

log "Connecting to server..."

ssh "${SERVER}" bash << EOF
  set -e
  cd ~

  echo "── Pulling latest images ──"
  docker compose pull

  echo "── Restarting containers ──"
  docker compose up -d --remove-orphans

  echo "── Pruning old images ──"
  docker image prune -f

  echo "── Container status ──"
  docker compose ps
EOF

# ═══════════════════════════════════════
# DONE
# ═══════════════════════════════════════
echo -e "\n${GREEN}${BOLD}✓ Deploy complete!${NC}"
log "Tag: ${TAG} | Server: ${SERVER}"
echo -e "  Check logs: ${YELLOW}ssh ${SERVER} 'docker compose logs -f --tail=50'${NC}\n"
