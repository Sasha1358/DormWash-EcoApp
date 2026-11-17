# Архітектура (Architecture)

Проєкт складається з трьох основних компонентів:
1. **Mobile (Android/Kotlin):** Відповідає за UI, збір даних від користувача та відображення результатів. Використовує Retrofit для HTTP-запитів.
2. **Backend (Python/FastAPI):** Виступає посередником (BFF - Backend for Frontend). Приймає запити, валідує їх, формує правильний prompt і звертається до зовнішнього API.
3. **AI Component (Google Gemini API):** Велика мовна модель (gemini-3.5-flash-lite), яка аналізує характеристики одягу та генерує еко-рекомендації.

**Потік даних (Data Flow):**
User Input -> Android (Retrofit POST) -> FastAPI (`/api/eco-mode`) -> Prompt Formulation -> Gemini API -> JSON Response -> FastAPI (JSON Parsing & Validation) -> Android (UI Update).