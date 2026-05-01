# Pagination — документация

## Что было добавлено

В ветке `feature/add_pagination` (коммит `1c3beac`) в эндпоинт `GET /api/v1/clothes` добавлена **опциональная курсорная пагинация**. Без параметра `limit` эндпоинт работает в прежнем режиме — возвращает полный список одежды.

---

## Изменения в коде

### `database/data/model/Clothe.kt`

Добавлена новая модель ответа:

```kotlin
@Serializable
data class PaginatedClothesResponse(
    val data: List<Clothe>,
    val nextCursor: Int?,
    val hasMore: Boolean
)
```

Изменений в схеме таблицы `ClotheTable` **не было**.

### `database/domain/repository/ClotheRepository.kt`

В интерфейс добавлен метод:

```kotlin
suspend fun getClothesPaginated(userId: Int, limit: Int, afterId: Int?): List<Clothe>
```

### `database/data/repository/ClotheRepositoryImpl.kt`

Реализация метода — выборка вещей пользователя по курсору:

- Получает все `id` вещей пользователя, не помеченных как удалённые.
- Фильтрует `id > afterId` (если курсор передан).
- Берёт первые `limit` записей, возвращает их в порядке `ASC` по `id`.

> **Примечание:** в итоговом коде на ветке `develop` (после merge с `add_filtering`) метод называется `getClothesPaginatedFiltered` — он объединяет пагинацию и фильтрацию в одном вызове.

### `routes/ClothesRoute.kt`

Логика в `GET /api/v1/clothes` разделена:

| Условие | Поведение | Тип ответа |
|---|---|---|
| Нет `?limit` и нет фильтров | Возвращает весь список | `List<Clothe>` |
| Есть `?limit` или хотя бы один фильтр | Возвращает страницу | `PaginatedClothesResponse` |

---

## API — описание для фронтенда

### Эндпоинт

```
GET /api/v1/clothes
Authorization: Bearer <token>
```

### Параметры запроса

| Параметр | Тип | Обязательный | Описание |
|---|---|---|---|
| `limit` | `integer` | Нет | Кол-во элементов на странице. Диапазон `[1, 50]`. Невалидные значения → `20`. |
| `cursor` | `integer` | Нет | `id` последнего элемента предыдущей страницы. На первом запросе не передаётся. |

### Режим 1 — полный список (без `limit`)

Запрос:
```
GET /api/v1/clothes
```

Ответ — обычный массив `Clothe[]`:
```json
[
  {
    "id": 42,
    "imageUrl": "http://host/images/abc.png",
    "name": "White Oxford Shirt",
    "storeUrl": "https://store.example.com/shirt",
    "season": "SUMMER",
    "fit": "SLIM",
    "material": "COTTON",
    "category": "TOPS",
    "styleTags": "casual,minimalist",
    "brand": "Zara",
    "colors": [{"r": 255, "g": 255, "b": 255}]
  }
]
```

### Режим 2 — пагинация

Первый запрос:
```
GET /api/v1/clothes?limit=20
```

Ответ — объект `PaginatedClothesResponse`:
```json
{
  "data": [ ...список Clothe... ],
  "nextCursor": 34,
  "hasMore": true
}
```

Следующая страница:
```
GET /api/v1/clothes?limit=20&cursor=34
```

Последняя страница (`hasMore: false`, `nextCursor: null`):
```json
{
  "data": [ ...список Clothe... ],
  "nextCursor": null,
  "hasMore": false
}
```

### Алгоритм перелистывания страниц

```
1. GET /api/v1/clothes?limit=20
   → сохрани nextCursor из ответа

2. GET /api/v1/clothes?limit=20&cursor=<nextCursor>
   → снова сохрани nextCursor

3. Повторяй шаг 2, пока hasMore == true
```

### Структура `Clothe`

```json
{
  "id": 42,                          // Int — используется как курсор
  "imageUrl": "http://host/...",     // String? — URL изображения
  "name": "White Oxford Shirt",      // String
  "storeUrl": "https://...",         // String?
  "season": "SUMMER",                // String? (SUMMER / WINTER / ...)
  "fit": "SLIM",                     // String? (SLIM / REGULAR / ...)
  "material": "COTTON",              // String?
  "category": "TOPS",               // String?
  "styleTags": "casual,minimalist",  // String? (теги через запятую)
  "brand": "Zara",                   // String?
  "colors": [{"r":255,"g":255,"b":255}] // List<RgbColor>?
}
```

---

## Изменения в схеме БД

**Миграций не требуется.**

Пагинация реализована на уровне приложения — курсор строится по существующему полю `id` таблицы `clothes` (первичный ключ, индекс уже есть). Новых колонок или таблиц добавлено не было.

---

## Деплой на сервер

### Шаги

1. Получить актуальный код:
   ```bash
   git pull origin develop
   ```

2. Пересобрать и перезапустить бекенд:
   ```bash
   # Через Docker Compose (рекомендуется)
   docker-compose build backend
   docker-compose up -d backend

   # Проверка логов
   docker-compose logs -f backend
   ```

3. Проверить работоспособность:
   ```bash
   curl http://localhost:8080/health
   ```

### Миграции БД

**Не требуются.** Схема таблиц не изменилась. Exposed через `SchemaUtils.create()` создаёт таблицы при отсутствии — на существующей БД ничего не затронет.

### Переменные окружения

Новых переменных добавлено не было. Убедись, что `.env` содержит все ранее требуемые переменные (см. `.env.example`):

```
JWT_SECRET
DB_URL / DB_USER / DB_PASSWORD / DB_NAME
REMOVE_BG_SERVICE_URL
ANALYSIS_SERVICE_URL
```

---

## Совместимость

Изменение **обратно совместимо**. Существующие вызовы `GET /api/v1/clothes` без параметров продолжают работать и возвращают `List<Clothe>` как прежде.

Переход на пагинацию на фронте — строго опциональный и может быть сделан постепенно.
