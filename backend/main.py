import os
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import requests
import json
from dotenv import load_dotenv

# Завантажуємо змінні з файлу .env
load_dotenv()
API_KEY = os.getenv("GEMINI_API_KEY")

app = FastAPI(title="DormWash AI API")

# --- НОВИЙ ЕНДПОІНТ ЗА ВИМОГОЮ МЕТОДИЧКИ ---
@app.get("/health")
def health_check():
    return {"status": "ok", "message": "Бекенд працює коректно"}

class WashRequest(BaseModel):
    fabric_type: str
    color: str
    dirt_level: str

class WashResponse(BaseModel):
    temperature: int
    spin_speed: int
    duration_minutes: int
    detergent_ml: int
    ai_explanation: str

@app.post("/api/eco-mode", response_model=WashResponse)
def get_eco_mode(request: WashRequest):
    if not request.fabric_type:
        raise HTTPException(status_code=400, detail="Тип тканини обов'язковий")
    
    prompt = f"""
    Ти експерт з екологічного прання. Дано: тканина - {request.fabric_type}, колір - {request.color}, забруднення - {request.dirt_level}.
    Поверни ТІЛЬКИ валідний JSON без форматування markdown.
    Формат: {{"temperature": 30, "spin_speed": 800, "duration_minutes": 45, "detergent_ml": 40, "ai_explanation": "<коротка еко-рекомендація українською>"}}
    """
    
    url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
    
    headers = {
        "Content-Type": "application/json",
        "x-goog-api-key": API_KEY 
    }
    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    
    try:
        response = requests.post(url, headers=headers, json=payload)
        response.raise_for_status() 
        
        result_data = response.json()
        ai_text = result_data['candidates'][0]['content']['parts'][0]['text']
        
        clean_text = ai_text.replace('```json', '').replace('```', '').strip()
        data = json.loads(clean_text)
        return WashResponse(**data)
        
    except requests.exceptions.HTTPError as e:
        raise HTTPException(status_code=e.response.status_code, detail=f"Помилка API Google: {e.response.text}")
    except json.JSONDecodeError as e:
        raise HTTPException(status_code=502, detail=f"ШІ повернув невалідний формат. Відповідь: {ai_text}. Деталі: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Внутрішня помилка сервера ({type(e).__name__}): {str(e)}")

@app.get("/api/models")
def list_models():
    url = "https://generativelanguage.googleapis.com/v1beta/models"
    headers = {
        "x-goog-api-key": API_KEY 
    }
    try:
        response = requests.get(url, headers=headers)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.HTTPError as e:
        raise HTTPException(status_code=e.response.status_code, detail=f"Помилка API: {e.response.text}")