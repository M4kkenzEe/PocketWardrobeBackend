# 🐳 Docker Setup для PocketWardrobe Backend

Полная инструкция по запуску и развертыванию PocketWardrobe Backend с использованием Docker.

---

## 📋 Содержание

- [Быстрый старт](#-быстрый-старт-mac-os)
- [Требования](#-требования)
- [Структура файлов](#-структура-файлов)
- [Запуск на Mac OS](#-запуск-на-mac-os-разработка)
- [Production деплой](#-production-деплой-сервер-19487190248)
- [Команды управления](#-команды-управления)
- [Мониторинг и отладка](#-мониторинг-и-отладка)
- [Backup и восстановление](#-backup-и-восстановление)
- [Troubleshooting](#-troubleshooting)

---

## 🚀 Быстрый старт (Mac OS)

```bash
# 1. Убедитесь что внешние сервисы запущены
# RemoveBG: http://localhost:8000
# Analysis: http://localhost:8088

# 2. Проверьте что .env файл существует
ls .env

# 3. Соберите и запустите
docker-compose up -d

# 4. Проверьте логи
docker-compose logs -f backend

# 5. Протестируйте API
curl http://localhost:8080/health
```

---

## 📦 Требования

### Mac OS / Windows
- Docker Desktop 4.0+
- Docker Compose V2
- 4 GB свободной RAM
- 5 GB свободного места на диске

### Linux Ubuntu
- Docker Engine 20.10+
- Docker Compose V2
- 4 GB свободной RAM
- 5 GB свободного места на диске

### Внешние зависимости
- RemoveBG service (порт 8000)
- Analysis service (порт 8088)

---

## 📁 Структура файлов

```
PocketWardrobeBackend/
├── Dockerfile                 # Multi-stage build (Gradle + JRE)
├── .dockerignore              # Исключения для build context
├── docker-compose.yml         # Основная конфигурация (Mac OS)
├── docker-compose.prod.yml    # Production overrides (Linux Ubuntu)
├── .env                       # Переменные окружения (не коммитится)
├── init-scripts/
│   └── 01-init.sql           # Инициализация PostgreSQL
├── uploads/                   # Пользовательские изображения одежды
├── looks/                     # Пользовательские изображения образов
└── DOCKER_README.md          # Эта инструкция
```

---

## 💻 Запуск на Mac OS (Разработка)

### 1. Подготовка

```bash
# Убедитесь что вы в корне проекта
cd /path/to/PocketWardrobeBackend

# Проверьте .env файл
cat .env

# Запустите внешние сервисы (если они не в Docker)
# RemoveBG должен быть доступен на http://localhost:8000
# Analysis должен быть доступен на http://localhost:8088
```

### 2. Сборка и запуск

```bash
# Собрать образы
docker-compose build

# Запустить все сервисы в фоновом режиме
docker-compose up -d

# Или запустить с выводом логов
docker-compose up
```

### 3. Проверка работоспособности

```bash
# Проверить статус контейнеров
docker-compose ps

# Проверить логи backend
docker-compose logs -f backend

# Проверить логи PostgreSQL
docker-compose logs -f postgres

# Проверить health endpoint
curl http://localhost:8080/health

# Ожидаемый ответ:
# {"status":"ok"}
```

### 4. Тестирование API

```bash
# Регистрация пользователя
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"password123","gender":"MALE"}'

# Логин
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password123"}'
```

---

## 🐧 Production деплой (сервер `194.87.190.248`)

Деплой полностью автоматизирован через скрипт. Образы публикуются на DockerHub (`yura91191`), сервер их пуллит.

### Быстрый деплой

```bash
# Из корня монорепо:
/deploy                          # полный деплой (build → push → server update)
.claude/scripts/deploy.sh --server-only  # только обновить сервер без пересборки
```

Скрипт автоматически синхронизирует `docker-compose.yml` и `nginx/nginx.conf` на сервер перед рестартом.

### Структура на сервере (`/root/`)

```
/root/
├── docker-compose.yaml     # синкается из PocketWardrobeBackend/docker-compose.yml
├── nginx/
│   └── nginx.conf          # синкается из PocketWardrobeBackend/nginx/nginx.conf
├── .env                    # на сервере вручную, не коммитится
├── uploads/                # пользовательские изображения одежды
└── looks/                  # пользовательские изображения образов
```

### Nginx + SSL

Nginx работает как Docker-контейнер внутри compose. SSL-сертификаты (Let's Encrypt) хранятся на хосте и монтируются в контейнер read-only.

- Домены: `clothis.tech`, `www.clothis.tech`, `api.clothis.tech`
- HTTP автоматически редиректится на HTTPS
- Certbot обновляет сертификаты автоматически (2x/день), после чего перезагружает nginx контейнер

**Изменить nginx конфиг:**
```bash
# 1. Отредактируйте локально
nano PocketWardrobeBackend/nginx/nginx.conf

# 2. Задеплойте
.claude/scripts/deploy.sh --server-only
```

### Первоначальная настройка сервера (однократно)

```bash
# 1. Установить Docker
apt update && apt install -y docker.io docker-compose-plugin

# 2. Установить certbot и получить сертификат
apt install -y certbot python3-certbot-nginx
certbot certonly --standalone -d clothis.tech -d www.clothis.tech -d api.clothis.tech

# 3. Создать .env
cp .env.example /root/.env && nano /root/.env

# 4. Запустить стек
cd /root && docker compose up -d
```

---


## 🎮 Команды управления

### Основные операции

```bash
# Запустить все сервисы
docker-compose up -d

# Остановить все сервисы
docker-compose down

# Остановить и удалить volumes (удалит данные!)
docker-compose down -v

# Перезапустить конкретный сервис
docker-compose restart backend

# Пересобрать после изменения кода
docker-compose up -d --build backend
```

### Просмотр информации

```bash
# Статус контейнеров
docker-compose ps

# Логи всех сервисов
docker-compose logs

# Логи конкретного сервиса с follow
docker-compose logs -f backend

# Последние 100 строк логов
docker-compose logs --tail=100 backend

# Использование ресурсов
docker stats
```

### Работа с контейнерами

```bash
# Войти в shell backend контейнера
docker-compose exec backend sh

# Войти в PostgreSQL shell
docker-compose exec postgres psql -U postgres -d wardrobe

# Выполнить команду в контейнере
docker-compose exec backend ls -la /app
```

---

## 📊 Мониторинг и отладка

### Health Checks

```bash
# Backend health check
curl http://localhost:8080/health

# PostgreSQL health check
docker-compose exec postgres pg_isready -U postgres

# Проверить все containers
docker-compose ps
```

### Просмотр логов

```bash
# Логи с timestamp
docker-compose logs -f -t backend

# Логи с фильтрацией
docker-compose logs backend | grep ERROR

# Логи PostgreSQL
docker-compose logs postgres | grep FATAL
```

### Отладка проблем с сетью

```bash
# Проверить сети
docker network ls

# Проверить подключения к сети
docker network inspect pocketwardrobebackend_pocketwardrobe_network

# Проверить доступность внешних сервисов
docker-compose exec backend curl http://host.docker.internal:8000/health
```

---

## 💾 Backup и восстановление

### Backup PostgreSQL

```bash
# Backup базы данных
docker-compose exec postgres pg_dump -U postgres wardrobe > backup_$(date +%Y%m%d_%H%M%S).sql

# Backup с сжатием
docker-compose exec postgres pg_dump -U postgres wardrobe | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz

# Backup всех баз
docker-compose exec postgres pg_dumpall -U postgres > backup_all_$(date +%Y%m%d_%H%M%S).sql
```

### Восстановление PostgreSQL

```bash
# Восстановление из backup
docker-compose exec -T postgres psql -U postgres wardrobe < backup_20231209_120000.sql

# Восстановление из сжатого backup
gunzip < backup_20231209_120000.sql.gz | docker-compose exec -T postgres psql -U postgres wardrobe
```

### Backup файлов (uploads, looks)

```bash
# Backup директорий с пользовательскими данными
tar -czf uploads_backup_$(date +%Y%m%d).tar.gz uploads/
tar -czf looks_backup_$(date +%Y%m%d).tar.gz looks/

# Или все вместе
tar -czf user_data_backup_$(date +%Y%m%d).tar.gz uploads/ looks/
```

### Backup Docker volumes

```bash
# Backup PostgreSQL volume
docker run --rm \
  -v pocketwardrobebackend_postgres_data:/data \
  -v $(pwd):/backup \
  alpine tar czf /backup/postgres_volume_backup.tar.gz -C /data .

# Восстановление volume
docker run --rm \
  -v pocketwardrobebackend_postgres_data:/data \
  -v $(pwd):/backup \
  alpine tar xzf /backup/postgres_volume_backup.tar.gz -C /data
```

---

## 🔧 Troubleshooting

### Проблема: Backend не может подключиться к PostgreSQL

**Решение:**
```bash
# Проверьте что PostgreSQL health check прошел
docker-compose ps

# Проверьте логи PostgreSQL
docker-compose logs postgres

# Проверьте переменные окружения
docker-compose exec backend env | grep DB_

# Убедитесь что используется правильный DB_URL
# Должно быть: jdbc:postgresql://postgres:5432
```

### Проблема: Не могу подключиться к RemoveBG/Analysis сервисам

**На Mac/Windows:**
```bash
# Проверьте что host.docker.internal работает
docker-compose exec backend ping host.docker.internal

# Проверьте что сервисы запущены на хосте
curl http://localhost:8000/health
curl http://localhost:8088/health
```

**На Linux:**
```bash
# Добавьте в docker-compose.prod.yml extra_hosts (уже добавлено)
# Или используйте IP адрес хоста:
# docker inspect bridge | grep Gateway
```

### Проблема: Ошибка "Permission denied" для uploads/looks

**Решение:**
```bash
# Создайте директории если их нет
mkdir -p uploads looks

# Установите правильные права
chmod 755 uploads looks

# Если проблема persist, проверьте ownership в контейнере
docker-compose exec backend ls -la /app/
```

### Проблема: Out of memory

**Решение:**
```bash
# Увеличьте memory limit в docker-compose.prod.yml
# deploy:
#   resources:
#     limits:
#       memory: 2G

# Или увеличьте JVM heap в .env:
# Отредактируйте JAVA_OPTS в Dockerfile
```

### Проблема: Медленная сборка

**Решение:**
```bash
# Используйте BuildKit
DOCKER_BUILDKIT=1 docker-compose build

# Очистите кеш если нужно
docker builder prune

# Проверьте .dockerignore - убедитесь что build/ и .gradle/ исключены
```

### Проблема: Port already in use

**Решение:**
```bash
# Найдите процесс использующий порт
lsof -i :8080
lsof -i :5432

# Остановите конфликтующий процесс или измените порт в docker-compose.yml
# ports:
#   - "8081:8080"  # используйте другой внешний порт
```

---

## 📚 Дополнительные ресурсы

- [Официальная документация Ktor](https://ktor.io/)
- [Docker Compose документация](https://docs.docker.com/compose/)
- [PostgreSQL Docker Hub](https://hub.docker.com/_/postgres)
- [CLAUDE.md](./CLAUDE.md) - Документация проекта для Claude Code

---

## 🆘 Получение помощи

Если возникли проблемы:

1. Проверьте логи: `docker-compose logs -f`
2. Проверьте статус: `docker-compose ps`
3. Проверьте переменные окружения: `docker-compose exec backend env`
4. Создайте issue в репозитории проекта
5. Проверьте [Troubleshooting](#-troubleshooting) раздел выше

---

**Удачного деплоя! 🚀**
