#!/usr/bin/env python3
"""
SmartCal AI Daily Horoscope Updater (v75.2)
Generates fresh, eloquent, non-repetitive daily horoscopes for all 12 zodiac signs
in Persian (fa), Sorani Kurdish (ku), and English (en) via Gemini API
and writes daily_fal.json (and app/src/main/assets/www/daily_fal.json if present).
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


def is_lang_complete(existing: dict, date_str: str, lang: str) -> bool:
    if not isinstance(existing, dict) or existing.get("date") != date_str:
        return False
    model = str(existing.get("model", "")).strip()
    if not model or model == "pending-first-action-run":
        return False
    langs = existing.get("langs")
    if not isinstance(langs, dict):
        return False
    lmap = langs.get(lang)
    return isinstance(lmap, dict) and len(lmap) >= 10


def is_already_complete_for_date(existing: dict, date_str: str) -> bool:
    return all(is_lang_complete(existing, date_str, lang) for lang in ("fa", "ku", "en"))


def extract_version_tuple(model_name: str):
    m = re.search(r"gemini-(\d+)(?:\.(\d+))?", model_name.lower())
    if not m:
        return (0, 0)
    return (int(m.group(1)), int(m.group(2) or 0))


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
        maj, minor = extract_version_tuple(low)
        is_preview = ("preview" in low or "exp" in low or "thinking" in low)
        is_latest_alias = ("-latest" in low)
        if "flash" in low and "lite" not in low and not is_preview and not is_latest_alias:
            tier = 0
        elif "flash-lite" in low and not is_preview:
            tier = 1
        elif "flash" in low and not is_preview:
            tier = 2
        elif "flash" in low:
            tier = 3
        elif not is_preview:
            tier = 4
        else:
            tier = 5
        return (tier, -maj, -minor, model_name)

    ordered = sorted(candidates, key=rank)
    print("Available Gemini models:", ", ".join(ordered[:8]))
    return ordered


def call_gemini_json(api_key: str, models: list, prompt: str):
    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.85,
            "maxOutputTokens": 32768,
            "responseMimeType": "application/json",
        },
    }).encode("utf-8")

    last_err = None
    dead_models = []

    for round_idx in range(2):
        round_candidates = list(models[:8])
        if not round_candidates:
            break
        for model in round_candidates:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
            req = urllib.request.Request(
                url,
                data=payload,
                headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
                method="POST",
            )
            try:
                with urllib.request.urlopen(req, timeout=90) as resp:
                    res = json.loads(resp.read().decode("utf-8"))
                parts = res["candidates"][0]["content"]["parts"]
                text_parts = [p.get("text", "") for p in parts if isinstance(p, dict) and not p.get("thought")]
                raw = "".join(text_parts).strip() or str(parts[-1].get("text", "")).strip()
                raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.IGNORECASE).strip()
                parsed = json.loads(raw)

                for dm in dead_models:
                    if dm in models:
                        models.remove(dm)
                if model in models:
                    models.remove(model)
                    models.insert(0, model)
                return parsed, model
            except urllib.error.HTTPError as e:
                body = ""
                try:
                    body = e.read().decode("utf-8", errors="ignore")[:180]
                except Exception:
                    pass
                last_err = f"HTTP {e.code}: {body}"
                print(f"Model {model} failed: HTTP {e.code}")
                if e.code == 404:
                    dead_models.append(model)
                    if model in models:
                        models.remove(model)
                else:
                    time.sleep(1.5)
            except Exception as e:
                last_err = str(e)
                print(f"Model {model} failed: {e}")
                time.sleep(1.0)
        if round_idx == 0:
            time.sleep(4.0)

    raise RuntimeError(f"All Gemini models failed: {last_err}")


def build_prompt(lang: str, date_str: str) -> str:
    if lang == "fa":
        sign_list = ", ".join([f'{i}: "{s[0]}"' for i, s in enumerate(SIGNS)])
        return f"""امروز {date_str} است. برای هر ۱۲ برج فلکی ({sign_list}) دقیقاً ۳ طالع روزانه کاملاً متفاوت، روان، ادبی، بدون غلط املایی و کاربردی به فارسی معیار بنویس.
خروجی باید فقط یک شیء JSON با کلیدهای رشته‌ای "0" تا "11" باشد. مقدار هر کلید آرایه‌ای با دقیقاً ۳ آبجکت به این شکل است:
{{
  "general": "یک تا دو جمله کوتاه، شفاف و الهام‌بخش درباره انرژی کلی امروز",
  "work": "یک جمله کاربردی درباره کار، تصمیم‌گیری و مسائل مالی",
  "emotion": "یک جمله گرم و صمیمی درباره روابط، خانواده و احساسات",
  "lucky_num": 7,
  "lucky_color": "سبز زمردی"
}}
قوانین: هیچ عبارت بی‌معنی یا ترجمه ماشینی ننویس، lucky_num عددی بین 1 تا 9 باشد، و هر ۳ نسخه هر برج از نظر موضوع و جمله‌بندی کاملاً متمایز باشند."""

    if lang == "ku":
        sign_list = ", ".join([f'{i}: "{s[1]}"' for i, s in enumerate(SIGNS)])
        return f"""ئەمڕۆ {date_str}ە. بۆ هەر ١٢ بورجەکە ({sign_list}) بە کوردیی سۆرانیی پاراو، ڕەوان و بێ هەڵەی ڕێنووس، بۆ هەر بورجێک ٣ بەختی ڕۆژانەی جیاواز و نوێ بنووسە.
دەرەنجام تەنها JSON بێت بە کلیلەکانی "0" تا "11". هەر کلیلێک لیستی ٣ دانە ئۆبجێکتی تێدابێت بەم شێوەیە:
{{
  "general": "یەک بۆ دوو ڕستەی کورت و ئەرێنی دەربارەی وزەی ئەمڕۆ",
  "work": "یەک ڕستەی کارا دەربارەی کار و دارایی",
  "emotion": "یەک ڕستەی گەرم دەربارەی پەیوەندی و هەست",
  "lucky_num": 5,
  "lucky_color": "سەوزی زمردی"
}}
یاسا: lucky_num ژمارەیەک بێت لە نێوان 1 تا 9 و هەر ٣ وەشانەکە جیاواز بن."""

    sign_list = ", ".join([f'{i}: "{s[2]}"' for i, s in enumerate(SIGNS)])
    return f"""Today is {date_str}. Write 3 distinct, concise, practical daily horoscopes in natural English for each of the 12 zodiac signs ({sign_list}).
Return ONLY a JSON object with string keys "0" through "11". Each value must be an array of 3 objects:
{{
  "general": "One or two concise, uplifting sentences about today's overall energy.",
  "work": "One practical sentence about career or finances.",
  "emotion": "One warm sentence about relationships and emotions.",
  "lucky_num": 7,
  "lucky_color": "Emerald Green"
}}
Rules: Keep each field concise (12-28 words), lucky_num must be an integer 1-9, and all 3 variants per sign must be genuinely distinct."""


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
        "model": existing.get("model", "") if existing.get("date") == date_str else "",
        "langs": {},
    }

    existing_langs = existing.get("langs", {}) if isinstance(existing.get("langs"), dict) else {}
    used_model = output["model"]
    fresh_count = 0

    for lang in ("fa", "ku", "en"):
        if event_name == "schedule" and not force_flag and is_lang_complete(existing, date_str, lang):
            output["langs"][lang] = existing_langs[lang]
            print(f"Reusing already-generated {lang} for {date_str}.")
            continue

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
