#!/usr/bin/env python3
"""
SmartCal AI Calendar & Emergency Holiday Updater (v70+)
1) Automatically checks time.ir for the current Jalali year and next year (امسال و سال آینده)
   and reconciles official holidays & occasions in holidays.json.
2) Scans official Iranian news RSS feeds every 3 hours and detects confirmed emergency
   closures via Gemini API (auto-discovering available models via ListModels).
"""
import datetime
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

RSS_FEEDS = [
    ("ایرنا", "https://www.irna.ir/rss"),
    ("ایسنا", "https://www.isna.ir/rss"),
    ("خبرگزاری مهر", "https://www.mehrnews.com/rss"),
    ("خبرآنلاین", "https://www.khabaronline.ir/rss"),
]

KEYWORDS = [
    "تعطیل", "تعطیلی", "غیرحضوری", "دورکاری", "آلودگی هوا",
    "برودت هوا", "مدیریت مصرف", "ادارات", "مدارس", "کمیته اضطرار"
]

TIME_IR_HOME = "https://www.time.ir/"
TIME_IR_YEAR_URL = "https://www.time.ir/fa/eventyear-%D8%AA%D9%82%D9%88%DB%8C%D9%85-%D8%B3%D8%A7%D9%84%DB%8C%D8%A7%D9%86%D9%87"
TIME_IR_CHECK_INTERVAL_SEC = 7 * 86400  # Re-verify weekly (or immediately on manual run / missing year)

# Minimal Jalali conversion (Gregorian -> Jalali)
def div(a, b): return int(a / b)
def mod(a, b): return a - int(a / b) * b

def jal_cal(jy):
    br = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178]
    gy = jy + 621
    leap_j = -14
    jp = br[0]
    for i in range(1, len(br)):
        jm = br[i]
        jump = jm - jp
        if jy < jm:
            break
        leap_j += div(jump, 33) * 8 + div(mod(jump, 33), 4)
        jp = jm
    n = jy - jp
    leap_j += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
    if mod(jump, 33) == 4 and jump - n == 4:
        leap_j += 1
    leap_g = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
    march = 20 + leap_j - leap_g
    if jump - n < 6:
        n = n - jump + div(jump + 4, 33) * 33
    leap = mod(mod(n + 1, 33) - 1, 4)
    if leap == -1:
        leap = 4
    return leap, gy, march

def g2d(gy, gm, gd):
    d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
    return d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752

def g2j(gy, gm, gd):
    jdn = g2d(gy, gm, gd)
    jy = gy - 621
    leap, _, march = jal_cal(jy)
    jdn1f = g2d(gy, 3, march)
    k = jdn - jdn1f
    if k >= 0:
        if k <= 185:
            return jy, 1 + div(k, 31), mod(k, 31) + 1
        k -= 186
        return jy, 7 + div(k, 30), mod(k, 30) + 1
    jy -= 1
    k += 179
    if leap == 1:
        k += 1
    return jy, 7 + div(k, 30), mod(k, 30) + 1

def discover_gemini_models(api_key):
    url = "https://generativelanguage.googleapis.com/v1beta/models"
    req = urllib.request.Request(url, headers={"x-goog-api-key": api_key}, method="GET")
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
    lite_models = [c for c in candidates if "flash-lite" in c.lower()]
    flash_models = [c for c in candidates if "flash" in c.lower() and c not in lite_models]
    other_models = [c for c in candidates if c not in lite_models and c not in flash_models]
    ordered = lite_models + flash_models + other_models
    print("Available Gemini models:", ", ".join(ordered[:6]))
    return ordered

def call_gemini_json(api_key, models, prompt):
    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.1, "responseMimeType": "application/json"}
    }).encode("utf-8")

    last_err = None
    for model in models[:5]:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json", "x-goog-api-key": api_key}, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                res = json.loads(resp.read().decode("utf-8"))
            print(f"Using Gemini model: {model}")
            raw_text = res["candidates"][0]["content"]["parts"][0]["text"].strip()
            raw_text = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw_text)
            return json.loads(raw_text)
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="ignore")[:200]
            last_err = f"{model} -> HTTP {e.code}: {body}"
            print(f"Model {model} failed: HTTP {e.code}, trying next...")
            continue
        except Exception as e:
            last_err = f"{model} -> {e}"
            print(f"Model {model} error: {e}, trying next...")
            continue
    raise RuntimeError(f"Failed on discovered models. Last: {last_err}")

def strip_html_to_lines(html_text):
    # Remove scripts/styles and convert tags to line breaks
    txt = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", html_text, flags=re.S | re.I)
    txt = re.sub(r"</?(li|div|p|tr|br|h[1-6])[^>]*>", "\n", txt, flags=re.I)
    txt = re.sub(r"<[^>]+>", " ", txt)
    lines = []
    for ln in txt.splitlines():
        clean = re.sub(r"\s+", " ", ln).strip()
        if clean:
            lines.append(clean)
    return lines

def fetch_pnldev_calendar_for_year(year_int):
    """Reliable JSON API fallback (accessible from GitHub Actions IPs, exact Jalali + Hijri holidays)."""
    months_fa = [
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    ]
    headers = {
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Accept": "application/json",
    }
    lines = []
    for m in range(1, 13):
        url = f"https://pnldev.com/api/calender?year={year_int}&month={m}"
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=12) as resp:
                payload = json.loads(resp.read().decode("utf-8", errors="ignore"))
            days = payload.get("result") if isinstance(payload, dict) else None
            if not isinstance(days, dict):
                continue
            for d_str, info in days.items():
                if not isinstance(info, dict):
                    continue
                is_hol = bool(info.get("holiday"))
                evs = info.get("event") or []
                if isinstance(evs, list):
                    for ev in evs:
                        title = str(ev).strip()
                        if not title:
                            continue
                        prefix = "[تعطیل رسمی] " if is_hol else ""
                        lines.append(f"{prefix}{d_str} {months_fa[m - 1]}: {title}")
        except Exception:
            continue
    return lines


def fetch_github_persian_calendar_fallback():
    """Secondary GitHub-hosted fallback (official persian-calendar events.json, zero 403 risk)."""
    months_fa = [
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    ]
    url = "https://raw.githubusercontent.com/persian-calendar/persian-calendar/main/PersianCalendar/src/main/res/raw/events.json"
    lines = []
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8", errors="ignore"))
        for item in data.get("Persian Calendar", []):
            if item.get("type") != "Iran":
                continue
            m = int(item.get("month", 0) or 0)
            d = int(item.get("day", 0) or 0)
            title = str(item.get("title", "")).strip()
            if 1 <= m <= 12 and 1 <= d <= 31 and title:
                prefix = "[تعطیل رسمی] " if item.get("holiday") else ""
                lines.append(f"{prefix}{d} {months_fa[m - 1]}: {title}")
    except Exception as e:
        print(f"Warning: GitHub persian-calendar fallback failed: {e}")
    return lines


def fetch_time_ir_text_for_year(year_int, is_current_year=False):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "fa-IR,fa;q=0.9,en;q=0.8",
        "Referer": "https://www.time.ir/",
    }
    collected = []

    # 1) Try pnldev.com structured JSON API first (fast, accurate, GitHub Actions friendly)
    pnl_lines = fetch_pnldev_calendar_for_year(year_int)
    if pnl_lines:
        print(f"Fetched {len(pnl_lines)} official events for {year_int} from pnldev.com API.")
        collected.extend(pnl_lines)

    # 2) Also try time.ir annual page (with clean browser headers) if pnldev returned little/nothing
    if len(collected) < 20:
        try:
            form = urllib.parse.urlencode({"Year": str(year_int)}).encode("utf-8")
            req = urllib.request.Request(
                TIME_IR_YEAR_URL,
                data=form,
                headers={**headers, "Content-Type": "application/x-www-form-urlencoded"},
                method="POST"
            )
            with urllib.request.urlopen(req, timeout=20) as resp:
                raw = resp.read().decode("utf-8", errors="ignore")
            raw = re.sub(r'class="[^"]*eventHoliday[^"]*"[^>]*>', '>[تعطیل رسمی] ', raw, flags=re.I)
            lines = strip_html_to_lines(raw)
            months = ("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "امرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
            event_lines = [ln for ln in lines if any(m in ln for m in months) and len(ln) < 220]
            if event_lines:
                collected.extend(event_lines[:180])
        except Exception as e:
            print(f"Warning: time.ir eventyear fetch for {year_int} failed: {e}")

    # 3) Secondary GitHub-hosted fallback if both above failed
    if len(collected) < 15:
        gh_lines = fetch_github_persian_calendar_fallback()
        if gh_lines:
            print(f"Fetched {len(gh_lines)} events from GitHub persian-calendar fallback.")
            collected.extend(gh_lines)

    # Deduplicate preserving order
    seen, unique = set(), []
    for ln in collected:
        if ln not in seen:
            seen.add(ln)
            unique.append(ln)
    return "\n".join(unique[:260])

def sync_time_ir_calendar(api_key, models, data, current_jy, today_j):
    target_years = [current_jy, current_jy + 1]
    meta = data.setdefault("_meta", {})
    last_ts = int(meta.get("last_time_ir_check_ts", 0) or 0)
    now_ts = int(time.time())
    is_manual = (os.environ.get("GITHUB_EVENT_NAME", "") == "workflow_dispatch")
    missing_year = any(str(y) not in data or not data.get(str(y)) for y in target_years)

    if not is_manual and not missing_year and (now_ts - last_ts) < TIME_IR_CHECK_INTERVAL_SEC:
        print(f"time.ir check skipped (last checked on {meta.get('last_time_ir_check', 'unknown')}).")
        return False

    print(f"Checking time.ir for years {target_years} (امسال و سال آینده)...")
    changed = False
    checked_ok = []

    for yr in target_years:
        yr_str = str(yr)
        snippet = fetch_time_ir_text_for_year(yr, is_current_year=(yr == current_jy))
        if not snippet or len(snippet) < 80:
            print(f"Skipping time.ir AI parse for {yr_str}: insufficient content fetched.")
            continue

        prompt = f"""
متن زیر از سایت رسمی time.ir برای سال {yr_str} شمسی استخراج شده است:

{snippet[:14000]}

وظیفه تو:
فقط **تعطیلات رسمی متغیر (غیر از تعطیلات ثابت شمسی مثل ۱ تا ۴ فروردین، ۱۲ و ۱۳ فروردین، ۱۴ و ۱۵ خرداد، ۲۲ بهمن و ۲۹ اسفند)** و **مناسبت‌های مهم متغیر (مثل ولادت حضرت فاطمه/روز مادر، ولادت حضرت معصومه/روز دختر، ولادت امام رضا، روز ازدواج، روز عرفه)** را که در متن time.ir برای سال {yr_str} آمده است استخراج کن.

خروجی را دقیقاً به صورت یک شیء JSON معتبر (بدون متن اضافی) با کلید `"ماه-روز"` (بدون صفر اضافی، مثل `"9-9"` یا `"10-2"`) برگردان:
{{
  "10-2": [["ولادت امام علی (ع) و روز پدر (تعطیل رسمی)", 1]],
  "9-9": [["ولادت حضرت فاطمه زهرا (س) و روز زن و مادر", 0]]
}}
قوانین مهم:
- عدد دوم آرایه فقط برای **تعطیل رسمی واقعی** برابر `1` و برای مناسبت‌های غیرتعطیل برابر `0` باشد.
- فقط مواردی که با اطمینان در متن بالا برای سال {yr_str} دیده می‌شوند را برگردان؛ اگر متن ناقص بود شیء خالی `{{}}` برگردان.
"""
        try:
            parsed = call_gemini_json(api_key, models, prompt)
        except Exception as e:
            print(f"time.ir Gemini check error for {yr_str}: {e}")
            continue

        if isinstance(parsed, dict) and parsed:
            yr_map = data.setdefault(yr_str, {})
            added_cnt = 0
            for dk, ev_list in parsed.items():
                if not re.match(r"^\d{1,2}-\d{1,2}$", str(dk)) or not isinstance(ev_list, list):
                    continue
                cur = yr_map.setdefault(str(dk), [])
                for ev in ev_list:
                    if isinstance(ev, list) and len(ev) >= 2 and isinstance(ev[0], str):
                        title = ev[0].strip()
                        is_hol = 1 if int(ev[1]) == 1 else 0
                        if title and not any(x[0] == title for x in cur):
                            cur.append([title, is_hol])
                            added_cnt += 1
                            changed = True
            print(f"time.ir check for {yr_str}: merged {added_cnt} new/verified items.")
            checked_ok.append(yr_str)

    if checked_ok:
        meta["source"] = "time.ir + SmartCal AI"
        meta["checked_years"] = [str(y) for y in target_years]
        meta["last_time_ir_check"] = today_j
        meta["last_time_ir_check_ts"] = now_ts
        changed = True

    return changed

def fetch_relevant_news():
    items = []
    headers = {"User-Agent": "Mozilla/5.0 (SmartCal-AI-Bot/70.0)"}
    for source_name, url in RSS_FEEDS:
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=15) as resp:
                xml_bytes = resp.read()
            root = ET.fromstring(xml_bytes)
            for item in root.findall(".//item")[:40]:
                title = (item.findtext("title") or "").strip()
                desc = re.sub(r"<[^>]+>", " ", (item.findtext("description") or "")).strip()
                text = f"{title} — {desc[:220]}"
                if any(kw in text for kw in KEYWORDS):
                    items.append(f"[{source_name}] {text}")
        except Exception as e:
            print(f"Warning: failed to fetch {source_name}: {e}")
    return items[:25]

def main():
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        print("GEMINI_API_KEY secret is not set. Skipping AI scan.")
        return

    tehran_now = datetime.datetime.utcnow() + datetime.timedelta(hours=3, minutes=30)
    jy, jm, jd = g2j(tehran_now.year, tehran_now.month, tehran_now.day)
    today_j = f"{jy}-{jm}-{jd}"
    today_g = tehran_now.strftime("%Y-%m-%d")

    hol_path = "holidays.json"
    with open(hol_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    try:
        models = discover_gemini_models(api_key)
        if not models:
            print("No available Gemini models found.")
            return
    except Exception as e:
        print(f"Gemini ListModels error: {e}")
        return

    changed = False

    # Step 1: Check time.ir for current year and next year (امسال و سال آینده)
    if sync_time_ir_calendar(api_key, models, data, jy, today_j):
        changed = True

    # Step 2: Scan Iranian news RSS feeds for confirmed emergency closures
    news = fetch_relevant_news()
    print(f"Fetched {len(news)} relevant headlines on {today_j}")
    if news:
        prompt = f"""
امروز {today_j} شمسی (برابر با {today_g} میلادی) است.
تیتر و خلاصه خبرهای زیر از خبرگزاری‌های رسمی ایران استخراج شده است:

{chr(10).join(news)}

وظیفه تو:
فقط و فقط اطلاعیه‌های **قطعی و رسمی تعطیلی اضطراری** (ادارات، بانک‌ها، مدارس یا کل کشور) را که برای امروز یا روزهای آینده صادر شده‌اند استخراج کن.
- شایعات، پیشنهادها، «احتمال تعطیلی» یا اخبار تکذیب‌شده را کاملاً نادیده بگیر.
- اگر هیچ تعطیلی قطعی و رسمی در خبرها نیست، آرایه خالی `[]` برگردان.

خروجی را دقیقاً به صورت یک آرایه JSON معتبر (بدون هیچ متن اضافی یا Markdown) با این ساختار برگردان:
[
  {{
    "date": "1405-9-2",
    "title": "تعطیلی اضطراری ادارات و مدارس",
    "reason": "افزایش شدید آلودگی هوا",
    "scope": "all",
    "cities": ["تهران"],
    "isHoliday": true,
    "source": "ایرنا"
  }}
]
نکات:
- `date` تاریخ شمسی بدون صفر اضافی باشد (مثلاً 1405-9-2).
- `scope` یکی از مقادیر `"all"` (ادارات و مدارس)، `"offices"` (ادارات) یا `"schools"` (فقط مدارس/غیرحضوری) باشد.
- `isHoliday` برای `"all"` و `"offices"` برابر `true` و برای `"schools"` برابر `false` باشد.
- اگر تعطیلی سراسری است، `cities` برابر `["سراسری"]` باشد؛ در غیر این صورت نام شهرها یا استان‌های مشمول را بنویس.
"""
        try:
            detected = call_gemini_json(api_key, models, prompt)
            if not isinstance(detected, list):
                detected = []
        except Exception as e:
            print(f"Gemini emergency scan error: {e}")
            detected = []

        print(f"AI detected {len(detected)} confirmed closures.")
        emg = data.setdefault("emergency", {})

        for item in detected:
            dkey = str(item.get("date", "")).strip()
            if not re.match(r"^\d{4}-\d{1,2}-\d{1,2}$", dkey):
                continue
            entry = {
                "title": str(item.get("title", "تعطیلی اضطراری")).strip(),
                "reason": str(item.get("reason", "")).strip(),
                "scope": item.get("scope", "all") if item.get("scope") in ("all", "offices", "schools") else "all",
                "cities": item.get("cities") if isinstance(item.get("cities"), list) and item.get("cities") else ["سراسری"],
                "isHoliday": bool(item.get("isHoliday", True)),
                "source": str(item.get("source", "خبرگزاری رسمی")).strip(),
            }
            cur_list = emg.setdefault(dkey, [])
            if not any(x.get("title") == entry["title"] and x.get("cities") == entry["cities"] for x in cur_list):
                cur_list.append(entry)
                changed = True

    if changed:
        for path in ("holidays.json", "app/src/main/assets/www/holidays.json"):
            if os.path.exists(os.path.dirname(path) or "."):
                with open(path, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
        print("Updated holidays.json successfully.")
    else:
        print("No changes needed.")

if __name__ == "__main__":
    main()
