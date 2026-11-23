#!/usr/bin/env python3
"""
SmartCal AI Daily Horoscope Updater (v72)
Generates fresh, eloquent, non-repetitive daily horoscopes for all 12 zodiac signs
in Persian (fa), Sorani Kurdish (ku), and English (en) via Gemini API
and writes daily_fal.json + app/src/main/assets/www/daily_fal.json.
"""
import datetime
import json
import os
import re
import sys
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


def discover_gemini_models(api_key: str):
    url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, timeout=20) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    excluded = ("image", "audio", "tts", "embedding", "vision")
    candidates = []
    for m in data.get("models", []):
        name = m.get("name", "")
        methods = m.get("supportedGenerationMethods", [])
        low = name.lower()
        if "generateContent" in methods and "gemini" in low and not any(x in low for x in excluded):
            candidates.append(name.replace("models/", ""))
    flash = [c for c in candidates if "flash" in c.lower() and "lite" not in c.lower()]
    lite = [c for c in candidates if "flash-lite" in c.lower()]
    other = [c for c in candidates if c not in flash and c not in lite]
    ordered = flash + lite + other
    print("Available Gemini models:", ", ".join(ordered[:6]))
    return ordered


def call_gemini_json(api_key: str, models: list, prompt: str):
    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.85,
            "responseMimeType": "application/json",
        },
    }).encode("utf-8")

    last_err = None
    for model in models[:5]:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
        req = urllib.request.Request(
            url, data=payload, headers={"Content-Type": "application/json"}, method="POST"
        )
        try:
            with urllib.request.urlopen(req, timeout=90) as resp:
                res = json.loads(resp.read().decode("utf-8"))
            raw = res["candidates"][0]["content"]["parts"][0]["text"].strip()
            raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.IGNORECASE).strip()
            return json.loads(raw), model
        except Exception as e:
            last_err = e
            print(f"Model {model} failed: {e}")
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
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        print("GEMINI_API_KEY is not set; skipping AI horoscope update.")
        sys.exit(0)

    models = discover_gemini_models(api_key)
    if not models:
        print("No compatible Gemini models found.")
        sys.exit(1)

    now_utc = datetime.datetime.now(datetime.timezone.utc)
    now_irst = now_utc + datetime.timedelta(hours=3, minutes=30)
    date_str = now_irst.strftime("%Y-%m-%d")

    output = {
        "updated_at": now_irst.strftime("%Y-%m-%d %H:%M IRST"),
        "date": date_str,
        "provider": "Gemini AI",
        "model": "",
        "langs": {},
    }

    used_model = ""
    for lang in ("fa", "ku", "en"):
        try:
            raw_map, model = call_gemini_json(api_key, models, build_prompt(lang, date_str))
            cleaned = validate_lang_map(raw_map)
            if len(cleaned) >= 10:
                output["langs"][lang] = cleaned
                used_model = model
                print(f"Generated {lang}: {len(cleaned)}/12 signs OK via {model}")
            else:
                print(f"Warning: {lang} returned only {len(cleaned)} valid signs; skipped.")
        except Exception as e:
            print(f"Error generating {lang}: {e}")

    if not output["langs"]:
        print("No valid language data generated; keeping existing daily_fal.json.")
        sys.exit(1)

    output["model"] = used_model
    serialized = json.dumps(output, ensure_ascii=False, indent=2)

    targets = ["daily_fal.json", os.path.join("app", "src", "main", "assets", "www", "daily_fal.json")]
    for path in targets:
        parent = os.path.dirname(path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(serialized)
        print("Wrote:", path)


if __name__ == "__main__":
    main()
