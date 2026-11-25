#!/usr/bin/env python3
"""
SmartCal AI Daily Horoscope Updater (v75.1)
Generates fresh, eloquent, non-repetitive daily horoscopes for all 12 zodiac signs
in Persian (fa), Sorani Kurdish (ku), and English (en) via Gemini API
and writes daily_fal.json + app/src/main/assets/www/daily_fal.json.

Features:
- Smart idempotency on scheduled runs (backup cron slots exit early with 0 API usage
  once today's daily_fal.json is already complete for all 3 languages).
- Secure x-goog-api-key header (never places API key in URLs or logs).
- Fast stable Flash model prioritization + maxOutputTokens=8192 to prevent JSON truncation.
"""
import datetime
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

SIGNS = [
    ("حمل / فروردین", "کاوڕ / خاکەلێوە", "Aries"),
    ("ثور / اردیبهشت", "گا / گوڵان", "Taurus"),
    ("جوزا / خرداد", "دووانە / جۆزەردان", "Gemini"),
    ("سرطان / تیر", "قرژاڵ / پووشپەڕ", "Cancer"),
    ("اسد / مرداد", "شێر / گەلاوێژ", "Leo"),
    ("سنبله / شهریور", "فریشتە / خەرمانان", "Virgo"),
    ("میزان / مهر", "تەرازوو / رەزبەر", "Libra"),
    ("عقرب / آبان", "دووپشک / گەڵاڕێزان", "Scorpio"),
    ("قوس / آذر", "کەوان / سەرماوەز", "Sagittarius"),
    ("جدی / دی", "گیسک / بەفرانبار", "Capricorn"),
    ("دلو / بهمن", "سەتڵ / رێبەندان", "Aquarius"),
    ("حوت / اسفند", "نەهەنگ / رەشەمە", "Pisces"),
]


def load_existing_fal(path="daily_fal.json") -> dict:
    try:
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            if isinstance(data, dict):
                return data
    except Exception:
        pass
    return {}


def is_already_complete_for_date(existing: dict, date_str: str) -> bool:
    if not isinstance(existing, dict):
        return False
    if existing.get("date") != date_str:
        return False
    model = str(existing.get("model", "")).strip()
    if not model or model == "pending-first-action-run":
        return False
    langs = existing.get("langs")
    if not isinstance(langs, dict):
        return False
    for lang in ("fa", "ku", "en"):
        lmap = langs.get(lang)
        if not isinstance(lmap, dict) or len(lmap) < 10:
            return False
    return True


def discover_gemini_models(api_key: str):
    url = "https://generativelanguage.googleapis.com/v1beta/models"
    req = urllib.request.Request(url, headers={"x-goog-api-key": api_key}, method="GET")
    with urllib.request.urlopen(req, timeout=20) as resp:
        data = json.loads(resp.read().decode("utf-8"))

    excluded = (
        "image", "audio", "tts", "embedding", "vision",
        "robotics", "computer-use", "deep-research", "veo", "imagen", "gemma"
    )
    candidates = []
    for m in data.get("models", []):
        name = m.get("name", "")
        methods = m.get("supportedGenerationMethods", [])
        low = name.lower()
        if "generateContent" in methods and "gemini" in low and not any(x in low for x in excluded):
            candidates.append(name.replace("models/", ""))

    def rank(model_name: str):
        low = model_name.lower()
        is_preview = ("preview" in low or "exp" in low or "thinking" in low)
        if "flash" in low and "lite" not in low and not is_preview:
            return (0, model_name)
        if "flash-lite" in low and not is_preview:
            return (1, model_name)
        if "flash" in low:
            return (2, model_name)
        if not is_preview:
            return (3, model_name)
        return (4, model_name)

    ordered = sorted(candidates, key=rank)
    print("Available Gemini models:", ", ".join(ordered[:6]))
    return ordered


def call_gemini_json(api_key: str, models: list, prompt: str):
    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.85,
            "maxOutputTokens": 8192,
            "responseMimeType": "application/json",
        },
    }).encode("utf-8")

    last_err = None
    for model in models[:6]:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        req = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=75) as resp:
                res = json.loads(resp.read().decode("utf-8"))
            raw = res["candidates"][0]["content"]["parts"][0]["text"].strip()
            raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.IGNORECASE).strip()
            return json.loads(raw), model
        except urllib.error.HTTPError as e:
            body = ""
            try:
                body = e.read().decode("utf-8", errors="ignore")[:180]
            except Exception:
                pass
            last_err = f"HTTP {e.code}: {body}"
            print(f"Model {model} failed: HTTP {e.code}")
            time.sleep(1.5)
        except Exception as e:
            last_err = str(e)
            print(f"Model {model} failed: {e}")
            time.sleep(1.0)
    raise RuntimeError(f"All Gemini models failed: {last_err}")


def build_prompt(lang: str, date_str: str) -> str:
    if lang == "fa":
        sign_list = ", ".join([f'{i}: "{s[0]}"' for i, s in enumerate(SIGNS)])
        return f"""امروز {date_str} است. برای هر ۱۲ برج فلکی ({sign_list}) دقیقاً ۳ طالع روزانه کاملاً متفاوت، روان، ادبی، بدون غلط املایی و کاربردی به فارسی معیار بنویس.
خروجی باید فقط یک شیء JSON با کلیدهای رشته‌ای "0" تا "11" باشد. مقدار هر کلید آرایه‌ای با دقیقاً ۳ آبجکت به این شکل است:
{{
  "general": "دو جمله شفاف، الهام‌بخش و روان درباره انرژی کلی امروز",
  "work": "یک تا دو جمله کاربردی درباره کار، تصمیم‌گیری و مسائل مالی",
  "emotion": "یک تا دو جمله گرم و صمیمی درباره روابط، خانواده و احساسات",
  "lucky_num": 7,
  "lucky_color": "سبز زمردی"
}}
قوانین: هیچ عبارت بی‌معنی یا ترجمه ماشینی ننویس، lucky_num عددی بین 1 تا 9 باشد، و هر ۳ نسخه هر برج از نظر موضوع و جمله‌بندی کاملاً متمایز باشند."""

    if lang == "ku":
        sign_list = ", ".join([f'{i}: "{s[1]}"' for i, s in enumerate(SIGNS)])
        return f"""ئەمڕۆ {date_str}ە. بۆ هەر ١٢ بورجەکە ({sign_list}) بە کوردیی سۆرانیی پاراو، ڕەوان و بێ هەڵەی ڕێنووس، بۆ هەر بورجێک ٣ بەختی ڕۆژانەی جیاواز و نوێ بنووسە.
دەرەنجام تەنها JSON بێت بە کلیلەکانی "0" تا "11". هەر کلیلێک لیستی ٣ دانە ئۆبجێکتی تێدابێت بەم شێوەیە:
{{
  "general": "دوو ڕستەی ڕوون و ئەرێنی دەربارەی وزەی ئەمڕۆ",
  "work": "یەک بۆ دوو ڕستە دەربارەی کار و دارایی",
  "emotion": "یەک بۆ دوو ڕستەی گەرم دەربارەی پەیوەندی و هەست",
  "lucky_num": 5,
  "lucky_color": "سەوزی زمردی"
}}
یاسا: lucky_num ژمارەیەک بێت لە نێوان 1 تا 9 و هەر ٣ وەشانەکە جیاواز بن."""

    sign_list = ", ".join([f'{i}: "{s[2]}"' for i, s in enumerate(SIGNS)])
    return f"""Today is {date_str}. Write 3 distinct, eloquent, practical daily horoscopes in natural English for each of the 12 zodiac signs ({sign_list}).
Return ONLY a JSON object with string keys "0" through "11". Each value must be an array of 3 objects:
{{
  "general": "Two clear, uplifting sentences about today's overall energy.",
  "work": "One or two practical sentences about career and finances.",
  "emotion": "One or two warm sentences about relationships and emotions.",
  "lucky_num": 7,
  "lucky_color": "Emerald Green"
}}
Rules: lucky_num must be an integer 1-9, and all 3 variants per sign must be genuinely distinct."""


def validate_lang_map(obj: dict) -> dict:
    clean = {}
    if not isinstance(obj, dict):
        return clean
    for i in range(12):
        arr = obj.get(str(i))
        if not isinstance(arr, list) or not arr:
            continue
        valid_items = []
        for item in arr[:4]:
            if not isinstance(item, dict):
                continue
            g = str(item.get("general", "")).strip()
            w = str(item.get("work", "")).strip()
            e = str(item.get("emotion", "")).strip()
            c = str(item.get("lucky_color", "")).strip()
            try:
                n = int(item.get("lucky_num", 5))
            except Exception:
                n = 5
            n = max(1, min(9, n))
            if len(g) >= 20 and len(w) >= 12 and len(e) >= 12 and "\ufffd" not in (g + w + e):
                valid_items.append({
                    "general": g,
                    "work": w,
                    "emotion": e,
                    "lucky_num": n,
                    "lucky_color": c or "Emerald",
                })
        if valid_items:
            clean[str(i)] = valid_items
    return clean


def main():
    now_utc = datetime.datetime.now(datetime.timezone.utc)
    now_irst = now_utc + datetime.timedelta(hours=3, minutes=30)
    date_str = now_irst.strftime("%Y-%m-%d")

    event_name = os.environ.get("GITHUB_EVENT_NAME", "").strip().lower()
    force_flag = os.environ.get("FORCE_UPDATE", "").strip().lower() in ("1", "true", "yes")
    existing = load_existing_fal("daily_fal.json")

    if event_name == "schedule" and not force_flag and is_already_complete_for_date(existing, date_str):
        print(f"daily_fal.json is already complete for {date_str} (model={existing.get('model')}); skipping backup cron run.")
        sys.exit(0)

    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        print("GEMINI_API_KEY is not set; skipping AI horoscope update.")
        sys.exit(0)

    models = discover_gemini_models(api_key)
    if not models:
        print("No compatible Gemini models found.")
        sys.exit(1)

    output = {
        "updated_at": now_irst.strftime("%Y-%m-%d %H:%M IRST"),
        "date": date_str,
        "provider": "Gemini AI",
        "model": "",
        "langs": {},
    }

    existing_langs = existing.get("langs", {}) if isinstance(existing.get("langs"), dict) else {}
    used_model = ""
    fresh_count = 0

    for lang in ("fa", "ku", "en"):
        try:
            raw_map, model = call_gemini_json(api_key, models, build_prompt(lang, date_str))
            cleaned = validate_lang_map(raw_map)
            if len(cleaned) >= 10:
                output["langs"][lang] = cleaned
                used_model = model
                fresh_count += 1
                print(f"Generated {lang}: {len(cleaned)}/12 signs OK via {model}")
            else:
                print(f"Warning: {lang} returned only {len(cleaned)} valid signs; keeping fallback if present.")
                if lang in existing_langs and existing_langs[lang]:
                    output["langs"][lang] = existing_langs[lang]
        except Exception as e:
            print(f"Error generating {lang}: {e}")
            if lang in existing_langs and existing_langs[lang]:
                output["langs"][lang] = existing_langs[lang]

    if fresh_count == 0 or not output["langs"]:
        print("No valid fresh language data generated; keeping existing daily_fal.json.")
        sys.exit(1)

    output["model"] = used_model
    serialized = json.dumps(output, ensure_ascii=False, indent=2)

    targets = ["daily_fal.json"]
    android_www = os.path.join("app", "src", "main", "assets", "www")
    if os.path.isdir(android_www):
        targets.append(os.path.join(android_www, "daily_fal.json"))
    for path in targets:
        with open(path, "w", encoding="utf-8") as f:
            f.write(serialized)
        print("Wrote:", path)


if __name__ == "__main__":
    main()
