# API Контракт (API Contract)

**Базовий URL:** `http://<local-ip>:8000`

### 1. `GET /health`
Перевірка доступності сервера.
- **Відповідь (200 OK):** `{"status": "ok", "message": "Бекенд працює коректно"}`

### 2. `POST /api/eco-mode`
Генерація ШІ-рекомендацій для прання.
- **Request Body (WashRequest):**
  - `fabric_type` (string, обов'язкове) - тип тканини (напр. "бавовна").
  - `color` (string, обов'язкове) - колір.
  - `dirt_level` (string, обов'язкове) - рівень забруднення.
- **Response Body (WashResponse - 200 OK):**
  - `temperature` (int) - градуси Цельсія.
  - `spin_speed` (int) - оберти на хвилину.
  - `duration_minutes` (int) - час у хвилинах.
  - `detergent_ml` (int) - об'єм засобу.
  - `ai_explanation` (string) - текстове пояснення.
- **Помилки:**
  - `400 Bad Request` - відсутні обов'язкові поля.
  - `500/502` - помилка парсингу від ШІ або збій на сервері Gemini.