#!/usr/bin/env bash
set -euo pipefail

# Smoke test pour le service Vert.x AsciiDoc de ce repo.
# Il construit le JAR, lance docker compose (web + kroki), rend HTML+PDF
# via l'API /render, puis vérifie les artefacts générés.

# --- Options -------------------------------------------------------------------
DOWN="${DOWN:-false}"                 # true: docker compose down à la fin
TIMEOUT="${TIMEOUT:-60}"              # secondes max pour attendre le /health
RENDER_FORMATS="${RENDER_FORMATS:-html,pdf}"

# --- Helpers -------------------------------------------------------------------
log() { printf "[%s] %s\n" "$(date +%H:%M:%S)" "$*"; }
die() { printf "ERROR: %s\n" "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<EOF
Smoke test AsciiDoc (Vert.x):

  DOWN=true TIMEOUT=90 ./scripts/smoke_test.sh

Vars:
  DOWN            Stoppe et supprime les conteneurs après le test (defaut: false)
  TIMEOUT         Délai max pour attendre /health (defaut: 60)
  RENDER_FORMATS  Formats demandés à /render (defaut: html,pdf)

Pré-requis: docker, docker compose, curl, Java/Gradle wrapper (fourni)
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then usage; exit 0; fi

have docker || die "Docker est requis."
have curl || die "curl est requis."

# --- Build JAR -----------------------------------------------------------------
log "Construction du fat-jar (shadowJar)…"
./gradlew -q shadowJar

if [ ! -f build/libs/app-fat.jar ]; then
  die "Jar absent: build/libs/app-fat.jar"
fi
log "Jar OK: build/libs/app-fat.jar"

# --- Démarrage des services ----------------------------------------------------
log "Démarrage docker compose (web + kroki)…"
docker compose up -d --build

# --- Attente /health -----------------------------------------------------------
log "Attente de /health (timeout=${TIMEOUT}s)…"
deadline=$(( $(date +%s) + TIMEOUT ))
until curl -sfS http://localhost:8082/health >/dev/null 2>&1; do
  if [ $(date +%s) -ge $deadline ]; then
    log "Logs du service web (pour debug):" || true
    docker compose logs --no-color --tail=200 web || true
    die "/health indisponible après ${TIMEOUT}s"
  fi
  sleep 1
done
log "Service web OK."

# --- Rendu ---------------------------------------------------------------------
IFS=',' read -r -a formats <<< "$RENDER_FORMATS"
formats_json=$(printf '"%s",' "${formats[@]}")
formats_json="[${formats_json%,}]"

log "POST /render entry=docs/index.adoc formats=${RENDER_FORMATS}…"
resp=$(curl -sfS -H 'Content-Type: application/json' \
  -d "{\"entry\":\"docs/index.adoc\",\"formats\":${formats_json}}" \
  http://localhost:8082/render)
log "Réponse: ${resp}"

# --- Vérifs artefacts ----------------------------------------------------------
HTML_PATH="build_artifacts/index.html"
PDF_PATH="build_artifacts/index.pdf"

ok=true
if [[ "${RENDER_FORMATS}" == *html* ]]; then
  if [ -s "$HTML_PATH" ]; then log "OK HTML: $HTML_PATH"; else log "Manquant: $HTML_PATH"; ok=false; fi
fi
if [[ "${RENDER_FORMATS}" == *pdf* ]]; then
  if [ -s "$PDF_PATH" ]; then log "OK PDF : $PDF_PATH"; else log "Manquant: $PDF_PATH"; ok=false; fi
fi

if [ "$ok" != true ]; then
  log "Logs du service web (pour debug):" || true
  docker compose logs --no-color --tail=200 web || true
  die "Artefacts manquants."
fi

log "Smoke test réussi. Artefacts dans ./build_artifacts"

# --- Teardown optionnel --------------------------------------------------------
if [ "$DOWN" = "true" ]; then
  log "Arrêt et nettoyage docker compose…"
  docker compose down -v
fi
