#!/usr/bin/env bash
set -euo pipefail

cd /opt/juejin

SHA="${1:?usage: deploy.sh <git-sha>}"
REPO_DIR=/opt/juejin/repo
TAG="local-${SHA:0:12}"
IMAGE="juejin-backend:${TAG}"
PREV_IMAGE="$(grep '^APP_IMAGE=' .env 2>/dev/null | cut -d= -f2- || true)"
PREV_TAG="$(cat .current_tag 2>/dev/null || true)"

echo "==> deploy ${SHA} -> ${IMAGE}"

cd "${REPO_DIR}"
if ! git fetch --depth=1 origin "${SHA}"; then
  git fetch --depth=50 origin dev
fi
git checkout -f "${SHA}"

# 构建期间先停掉应用，把内存让给 Maven
docker compose -f /opt/juejin/compose.yml stop app >/dev/null 2>&1 || true

echo "==> maven build"
mvn -B -ntp clean package -DskipTests

echo "==> docker build"
docker build -t "${IMAGE}" "${REPO_DIR}"

cd /opt/juejin
if grep -q '^APP_IMAGE=' .env; then
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=${IMAGE}|" .env
else
  echo "APP_IMAGE=${IMAGE}" >> .env
fi

echo "==> start app"
docker compose up -d

for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "${TAG}" > .current_tag
    echo "==> deploy success: ${SHA}"
    # 保留最近 3 个本地镜像，清理更早的
    docker images "juejin-backend" --format '{{.Tag}}' \
      | grep '^local-' | tail -n +4 \
      | xargs -r -I{} docker rmi "juejin-backend:{}" >/dev/null 2>&1 || true
    docker image prune -f --filter "until=72h" >/dev/null 2>&1 || true
    exit 0
  fi
  sleep 2
done

echo "==> health check failed, recent app logs:"
docker compose logs --tail=100 app || true

if [[ "${PREV_IMAGE}" == juejin-backend:* ]]; then
  echo "==> rollback to ${PREV_IMAGE}"
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=${PREV_IMAGE}|" .env
  docker compose up -d
fi

exit 1
