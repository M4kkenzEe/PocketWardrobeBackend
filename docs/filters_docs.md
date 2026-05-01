# Документация фичи: фильтрация одежды

Описание всех изменений, добавленных в ветке `feature/add_filtering`.

---

## 1. Изменения базы данных

### Новые колонки в таблице `clothes`

| Колонка | Тип         | NULL | Описание |
|---------|-------------|------|----------|
| `brand` | VARCHAR(100)| YES  | Бренд одежды (заполняется вручную через форму загрузки) |
| `colors`| TEXT        | YES  | JSON-массив доминирующих цветов, определённых AI-анализом |

Миграция выполняется **автоматически при старте** приложения через безопасные `ALTER TABLE`:

```sql
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS colors TEXT;
```

Эти команды идемпотентны — на работающем сервере пересоздавать БД или накатывать SQL вручную **не нужно**. Достаточно задеплоить новую версию бэкенда.

### Формат хранения `colors`

Колонка `colors` хранит JSON-строку — массив HEX-строк:

```json
["#FFFFFF", "#0F145A"]
```

AI-сервис анализа (`ai_analyzer`) возвращает цвета в формате RGB. Бэкенд конвертирует их в HEX перед сохранением. Для вещей, загруженных **до** этого обновления, поле будет `null`.

---

## 2. Изменения API

### 2.1 `GET /api/v1/clothes` — расширен фильтрами

**Авторизация:** Bearer JWT (access token)

#### Логика ответа

| Условие | Форма ответа |
|---------|-------------|
| Нет `limit` **и** нет фильтров | `Clothe[]` — простой массив (обратная совместимость) |
| Есть `limit` **или** хотя бы один фильтр | `PaginatedClothesResponse` |

#### Параметры пагинации

| Параметр | Тип | Описание |
|----------|-----|----------|
| `limit`  | int | Количество элементов на странице. Зажато в `[1, 50]`, по умолчанию `20`. |
| `cursor` | int | `id` последнего полученного элемента. На первый запрос не передаётся. |

**Алгоритм пагинации:** сервер возвращает элементы с `id > cursor`, отсортированные по `id ASC`. Повторять запросы, подставляя `nextCursor`, пока `hasMore == false`.

#### Параметры фильтрации

Фильтры между разными параметрами комбинируются через **AND**.  
Несколько значений одного параметра — через **OR**.

| Параметр | Логика | Тип совпадения | Пример |
|----------|--------|----------------|--------|
| `category` | OR | точное | `?category=tops&category=pants` |
| `season` | OR | точное | `?season=summer&season=spring` |
| `material` | OR | подстрока (LIKE) | `?material=cotton` — находит `"cotton + polyester"` |
| `fit` | OR | подстрока (LIKE) | `?fit=slim` — находит `"slim-regular"` |
| `style` | OR | подстрока в `styleTags` | `?style=casual` |
| `brand` | OR | точное | `?brand=Nike&brand=Adidas` |
| `q` | — | подстрока в `name`, без учёта регистра | `?q=shirt` |
| `color` | — | евклидово расстояние в RGB ≤ `color_tolerance` | `?color=%23FF0000` |
| `color_tolerance` | — | порог расстояния, по умолчанию `50.0` | `?color_tolerance=30` |

> **Цветовой фильтр:** параметр `color` принимает HEX-цвет в формате `#RRGGBB` (URL-encoded: `%23RRGGBB`). Если значение не соответствует формату — фильтр **молча игнорируется**. Диапазон `color_tolerance`: `0` (точное совпадение) до `441` (любой цвет).

> **Важно:** цветовой фильтр применяется **in-memory** после выборки из БД. Из-за этого сервер делает over-fetch (запрашивает `limit * 3` строк из БД), а затем обрезает до нужного `limit`. Это нормальное поведение.

#### Структура ответа `Clothe` (новые поля)

```json
{
  "id": 42,
  "name": "White Oxford Shirt",
  "imageUrl": "http://server/images/uuid.png",
  "storeUrl": "https://store.example.com/item",
  "season": "summer",
  "fit": "slim",
  "material": "cotton",
  "category": "tops",
  "styleTags": "casual,minimalist",
  "brand": "Nike",
  "colors": ["#FFFFFF", "#C8B4A0"]
}
```

Поля `brand` и `colors` — **новые**. У старых вещей могут быть `null`.

#### Структура `PaginatedClothesResponse`

```json
{
  "data": [ /* массив Clothe */ ],
  "nextCursor": 34,
  "hasMore": true
}
```

`nextCursor` — `null`, если это последняя страница.

---

### 2.2 `GET /api/v1/clothes/filters` — новый эндпоинт

**Авторизация:** Bearer JWT (access token)

Возвращает доступные значения всех фильтров, вычисленные **из текущего гардероба пользователя**. Используется для наполнения UI (чипсы, дропдауны).

#### Пример ответа

```json
{
  "categories": ["accessories", "outerwear", "pants", "tops"],
  "materials":  ["canvas", "cotton", "denim", "polyester"],
  "fits":       ["loose", "regular", "slim"],
  "seasons":    ["all-season", "spring", "summer", "winter"],
  "styles":     ["casual", "formal", "streetwear"],
  "brands":     ["Adidas", "Nike", "Zara"],
  "colors": ["#FFFFFF", "#0F0F0F"]
}
```

#### Правила разбивки (важно для фронта)

| Поле | Хранится в БД как | Разбивается по | Пример |
|------|-------------------|----------------|--------|
| `materials` | `"cotton + polyester"` | ` + ` | → `["cotton", "polyester"]` |
| `fits` | `"slim-regular"` | `-` | → `["slim", "regular"]` |
| `styles` | `"casual,streetwear"` | `,` | → `["casual", "streetwear"]` |

Все списки дедуплицированы и отсортированы по алфавиту. `brands` не включает вещи с `brand: null`.

---

## 3. Рекомендуемый сценарий использования на фронте

```
1. Открыть экран гардероба
   → GET /api/v1/clothes/filters
   → Заполнить UI фильтров актуальными значениями

2. Пользователь применяет фильтры / вводит поиск
   → GET /api/v1/clothes?limit=20&category=tops&brand=Nike&q=shirt
   → Отобразить data[]

3. Пользователь скроллит вниз (infinite scroll)
   → GET /api/v1/clothes?limit=20&category=tops&brand=Nike&q=shirt&cursor=<nextCursor>
   → Дополнить список, остановиться при hasMore=false

4. Поиск по цвету (color picker → HEX)
   → GET /api/v1/clothes?limit=20&color=%23783CC8&color_tolerance=40
```

---

## 4. Деплой на сервер

> Основан на стеке из CLAUDE.md: Docker Compose, PostgreSQL, Ktor fat JAR.

### 4.1 Что нужно сделать при деплое этой ветки

1. **Собрать и запушить Docker-образ:**

```bash
# На локальной машине или CI
./gradlew buildFatJar
./gradlew buildImage

# Тегировать и пушить в registry (пример для Docker Hub)
docker tag pocket-wardrobe-backend <registry>/pocket-wardrobe-backend:latest
docker push <registry>/pocket-wardrobe-backend:latest
```

2. **На сервере — обновить контейнер:**

```bash
# Скачать новый образ и перезапустить только бэкенд (БД не трогается)
docker-compose pull backend
docker-compose up -d --no-deps backend
```

Флаг `--no-deps` — гарантия, что PostgreSQL и другие сервисы **не перезапустятся**.

3. **Миграция БД произойдёт автоматически** при старте контейнера — приложение само выполнит `ALTER TABLE clothes ADD COLUMN IF NOT EXISTS brand/colors`.

4. **Проверить:**

```bash
docker-compose logs -f backend   # убедиться в "✓ Database configured successfully"
curl http://localhost:8080/health
```

### 4.2 Откат (если что-то пошло не так)

Новые колонки `brand` и `colors` — nullable, поэтому **старый образ бэкенда** продолжит работать с обновлённой БД без ошибок (просто не будет их использовать).

```bash
# Откатиться на предыдущий тег образа
docker-compose stop backend
docker tag <registry>/pocket-wardrobe-backend:<prev-tag> pocket-wardrobe-backend:latest
docker-compose up -d --no-deps backend
```

### 4.3 Переменные окружения

Новых переменных окружения эта ветка **не добавляет**. Файл `.env` менять не нужно.

---

## 5. Краткая сводка изменённых файлов

| Файл | Что изменилось |
|------|---------------|
| `database/data/model/Clothe.kt` | Новые поля `brand`, `colors` в DTO и таблице; классы `ClotheFilter`, `AvailableFiltersResponse`; маппер `rowToClothe()`; `colors` хранится как `List<String>` (HEX) |
| `database/domain/repository/ClotheRepository.kt` | Новые методы `getClothesPaginatedFiltered()`, `getAvailableFilters()` |
| `database/data/repository/ClotheRepositoryImpl.kt` | Реализация фильтрованной пагинации (SQL), реализация `getAvailableFilters()` |
| `routes/ClothesRoute.kt` | Разбор query-параметров фильтров; in-memory цветовой фильтр; новый route `GET /clothes/filters` |
| `routes/Database.kt` | Миграция: `ALTER TABLE clothes ADD COLUMN IF NOT EXISTS brand/colors` |
| `services/ClotheAnalysisService.kt` | Поле `colors: List<ColorInfo>?` теперь передаётся в результат анализа (ранее игнорировалось) |
