#!/usr/bin/env bash
set -euo pipefail

cd /opt/juejin

TAG="${1:?usage: deploy.sh <tag>}"
IMAGE="ghcr.io/youzipi12138/juejin-backend:${TAG}"
PREV_TAG="$(cat .current_tag 2>/dev/null || true)"

echo "==> deploying ${IMAGE}"

if grep -q '^APP_IMAGE=' .env; then
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=${IMAGE}|" .env
else
  echo "APP_IMAGE=${IMAGE}" >> .env
fi

docker compose pull app
# 首次部署会同时拉起 mysql/redis；后续只重建 app
docker compose up -d

for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "${TAG}" > .current_tag
    echo "==> deploy success: ${TAG}"
    docker image prune -f --filter "until=72h" >/dev/null 2>&1 || true
    exit 0
  fi
  sleep 2
done

echo "==> health check failed, recent app logs:"
docker compose logs --tail=100 app || true

if [ -n "${PREV_TAG}" ]; then
  echo "==> rollback to ${PREV_TAG}"
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=ghcr.io/youzipi12138/juejin-backend:${PREV_TAG}|" .env
  docker compose up -d
fi

exit 1
