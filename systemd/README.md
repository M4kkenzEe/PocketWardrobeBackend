# wardrobe-stack.service

Приводит прод-стек к спеке `docker-compose.yml` при загрузке сервера.

## Зачем

`restart: always` воскрешает контейнер **как есть** и не сверяет его с compose-спекой.
2026-10-25 после ребута `wardrobe-nginx` поднялся не подключённым ни к одной
docker-сети: порты 80/443 не опубликованы, DNAT-правил нет, прод недоступен —
при `docker ps`, показывающем контейнер `Up`.

Обычного `docker compose up -d` для лечения недостаточно: config hash совпадает,
compose считает контейнер актуальным («Running») и не пересоздаёт его. Поэтому
скрипт проверяет факт публикации портов и при необходимости делает force-recreate.

У юнита намеренно нет `ExecStop` — он умеет только поднимать стек, поэтому
случайный `systemctl stop/restart` не способен уронить прод.

## Установка

```bash
scp systemd/wardrobe-stack-up.sh root@194.87.190.248:/usr/local/bin/
scp systemd/wardrobe-stack.service root@194.87.190.248:/etc/systemd/system/
ssh root@194.87.190.248 'chmod +x /usr/local/bin/wardrobe-stack-up.sh &&
  systemctl daemon-reload && systemctl enable --now wardrobe-stack.service'
```

## Диагностика

```bash
systemctl status wardrobe-stack.service
journalctl -u wardrobe-stack.service -n 30
```
