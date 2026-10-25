#!/usr/bin/env bash
# Приводит прод-стек PocketWardrobe к compose-спеке при загрузке сервера.
#
# Зачем отдельный скрипт, а не просто `docker compose up -d` в юните:
# при гонке на загрузке контейнер поднимается через restart-политику без
# docker-сети, а значит и без публикации портов 80/443 — прод недоступен.
# Для compose такой контейнер выглядит актуальным ("Running"), config hash
# совпадает, и пересоздавать он его не станет. Поэтому факт публикации
# портов проверяем явно и лечим force-recreate.
set -uo pipefail

DOCKER=/usr/bin/docker
cd /root || { echo "нет /root" >&2; exit 1; }

$DOCKER compose up -d

for attempt in 1 2 3; do
  if [ -n "$($DOCKER port wardrobe-nginx 2>/dev/null)" ]; then
    echo "nginx публикует порты — стек в порядке"
    exit 0
  fi
  echo "nginx без опубликованных портов, пересоздаю (попытка ${attempt}/3)"
  $DOCKER compose up -d --force-recreate nginx
  sleep 5
done

echo "nginx так и не опубликовал порты после 3 попыток" >&2
exit 1
