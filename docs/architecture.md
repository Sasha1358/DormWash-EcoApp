# Архітектура проєкту (Architecture)

## Компоненти системи
1. **Mobile Client (Android / Kotlin):**
    - `MainActivity`: форма вводу параметрів одягу, клієнтська валідація, спільний `ApiClient` (Retrofit).
    - `WashStorage.kt`: окремий шар локального сховища на базі SQLite (`WashDatabaseHelper`, `WashHistoryRecord`, `WashStatus`).
    - `HistoryActivity`: перегляд збережених записів у зворотному хронологічному порядку, обробка порожнього стану та очищення історії.
    - `DetailActivity`: перегляд вхідних параметрів та історичного результату ШІ, видалення запису з підтвердженням, ручна повторна відправка відкладених записів.
2. **Backend (FastAPI / Python):** приймає JSON-запит (`WashRequest`), валідує його через Pydantic, формує prompt і звертається до Google Gemini API.
3. **AI Component (Gemini 3.5 Flash Lite):** генерує еко-режим у форматі JSON.

## Потік даних
- **Онлайн-сценарій:** Android (`MainActivity`) → Retrofit `POST /api/eco-mode` → FastAPI → Gemini API → JSON (`WashResponse`) → збереження у локальну БД SQLite (`status = "готово"`) → відображення на екрані.
- **Офлайн-сценарій (підвищений рівень):** Android (`MainActivity`) → мережевий збій (`IOException`) → збереження запиту в SQLite (`status = "очікує"`) → після відновлення мережі користувач відкриває `DetailActivity` і запускає ручну відправку (`очікує` → `надсилається` → `готово` або `помилка`).
- **Перегляд історії:** `HistoryActivity` / `DetailActivity` ↔ `WashDatabaseHelper` (SQLite `dormwash_history.db`).