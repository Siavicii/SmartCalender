import os, re, urllib.request
from PIL import Image, ImageDraw, ImageFont

manifest_path = "app/src/main/AndroidManifest.xml"
with open(manifest_path, "r", encoding="utf-8") as f:
    manifest_xml = f.read()

mipmap_refs = sorted(set(re.findall(r"@mipmap/([a-zA-Z0-9_]+)", manifest_xml)))
res_light_dir = "app/src/main/res/mipmap-xxhdpi"
res_night_dir = "app/src/main/res/mipmap-night-xxhdpi"
os.makedirs(res_light_dir, exist_ok=True)
os.makedirs(res_night_dir, exist_ok=True)

FA_MONTHS = [
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
]
KU_MONTHS = [
    "خاکەلێوە", "گوڵان", "جۆزەردان", "پووشپەڕ", "گەلاوێژ", "خەرمانان",
    "ڕەزبەر", "گەڵاڕێزان", "سەرماوەز", "بەفرانبار", "ڕێبەندان", "ڕەشەمە"
]
EN_MONTHS = [
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
]

def to_fa_digits(n):
    return str(n).translate(str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹"))

def _download_bytes(url, timeout=15):
    req = urllib.request.Request(url, headers={"User-Agent": "SmartCal-IconGen/70.5"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()

def _test_pillow_font(path):
    try:
        ImageFont.truetype(path, 48)
        return True
    except Exception:
        return False

def ensure_iransans_regular_font():
    local_ttf = "IRANSans-Regular-Icon.ttf"
    if os.path.exists(local_ttf) and os.path.getsize(local_ttf) > 10000 and _test_pillow_font(local_ttf):
        return local_ttf

    # Strictly Regular (non-bold) IRANSans font sources, with Vazirmatn Regular fallback
    iransans_urls = [
        "https://raw.githubusercontent.com/mhmmdq/IRANSans-Font/master/IRANSansWeb(FaNum).ttf",
        "https://raw.githubusercontent.com/mhmmdq/IRANSans-Font/master/IRANSansWeb.ttf",
        "https://raw.githubusercontent.com/Aryan1379/IranSans-Font/master/IRANSans(FaNum).ttf",
        "https://raw.githubusercontent.com/Aryan1379/IranSans-Font/master/IRANSans.ttf",
        "https://raw.githubusercontent.com/mortezamasoumi/iran-sans-font/master/IRANSansMobile.ttf",
        "https://raw.githubusercontent.com/saeed-b/iran-sans/master/fonts/ttf/IRANSansWeb.ttf",
        "https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/ttf/Vazirmatn-Regular.ttf",
    ]

    for url in iransans_urls:
        try:
            data = _download_bytes(url)
            if len(data) > 10000:
                with open(local_ttf, "wb") as out_f:
                    out_f.write(data)
                if _test_pillow_font(local_ttf):
                    print("Loaded IRANSans Regular font from:", url)
                    return local_ttf
        except Exception:
            continue

    return None

regular_font_path = ensure_iransans_regular_font()

font_regular_candidates = [
    regular_font_path,
    "IRANSans-Regular.ttf",
    "IRANSansWeb.ttf",
    "Vazirmatn-Regular.ttf",
    "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
]

f_reg = next((p for p in font_regular_candidates if p and os.path.exists(p)), None)
print(f"Using clean non-bold IRANSans icon font: {f_reg}")

# Supersampled 2x canvas (384 -> 144) for razor-sharp, readable non-bold typography
SCALE = 2
font_num_1d = ImageFont.truetype(f_reg, 102 * SCALE)
font_num_2d = ImageFont.truetype(f_reg, 90 * SCALE)
font_month_short = ImageFont.truetype(f_reg, 34 * SCALE)
font_month_long = ImageFont.truetype(f_reg, 28 * SCALE)

def make_icon(day_str, month_str, out_path, dark_mode=False, is_rtl=True):
    S = 192 * SCALE
    bg_col = (15, 23, 42, 255) if dark_mode else (255, 255, 255, 255)
    num_col = (248, 250, 252, 255) if dark_mode else (15, 23, 42, 255)
    mon_col = (110, 168, 255, 255) if dark_mode else (27, 110, 243, 255)

    img = Image.new("RGBA", (S, S), bg_col)
    draw = ImageDraw.Draw(img)

    kw = {"direction": "rtl", "language": "fa"} if is_rtl else {}

    # Clean, balanced vertical centering (non-bold, crisp & readable)
    f_num = font_num_2d if len(day_str) >= 2 else font_num_1d
    nb = draw.textbbox((0, 0), day_str, font=f_num, **kw)
    nx = (S - (nb[2] - nb[0])) / 2 - nb[0]
    ny = (74 * SCALE) - (nb[3] - nb[1]) / 2 - nb[1]
    draw.text((nx, ny), day_str, font=f_num, fill=num_col, **kw)

    f_mon = font_month_long if len(month_str) >= 7 else font_month_short
    mb = draw.textbbox((0, 0), month_str, font=f_mon, **kw)
    mx = (S - (mb[2] - mb[0])) / 2 - mb[0]
    my = (142 * SCALE) - (mb[3] - mb[1]) / 2 - mb[1]
    draw.text((mx, my), month_str, font=f_mon, fill=mon_col, **kw)

    final_img = img.resize((144, 144), Image.Resampling.LANCZOS)
    final_img.save(out_path, format="WEBP", quality=88, method=6)

def make_timeless_launcher_icon(out_path, dark_mode=False):
    S = 192
    bg_col = (31, 43, 59, 255)
    img = Image.new("RGBA", (S, S), bg_col)
    draw = ImageDraw.Draw(img)

    white = (255, 255, 255, 255)
    coral = (248, 113, 113, 255)
    blue = (96, 165, 250, 255)

    draw.rounded_rectangle([34, 46, 158, 158], radius=16, outline=white, width=9)
    draw.line([(34, 74), (158, 74)], fill=white, width=8)
    draw.rounded_rectangle([60, 28, 70, 56], radius=5, fill=white)
    draw.rounded_rectangle([122, 28, 132, 56], radius=5, fill=white)

    draw.rounded_rectangle([62, 92, 102, 132], radius=7, outline=coral, width=9)
    draw.rounded_rectangle([90, 104, 130, 144], radius=7, outline=blue, width=9)
    draw.line([(90, 92), (102, 92)], fill=coral, width=9)
    draw.line([(102, 92), (102, 118)], fill=coral, width=9)

    img.resize((144, 144), Image.Resampling.LANCZOS).save(out_path, format="WEBP", quality=90, method=6)

for ref in mipmap_refs:
    light_p = os.path.join(res_light_dir, f"{ref}.webp")
    night_p = os.path.join(res_night_dir, f"{ref}.webp")

    m_fa = re.match(r"^ic_m(\d\d)_d(\d\d)$", ref)
    m_ku = re.match(r"^ic_km(\d\d)_d(\d\d)$", ref)
    m_en = re.match(r"^ic_gm(\d\d)_d(\d\d)$", ref)

    if m_fa:
        m_idx, d_idx = int(m_fa.group(1)), int(m_fa.group(2))
        make_icon(to_fa_digits(d_idx), FA_MONTHS[m_idx - 1], light_p, dark_mode=False, is_rtl=True)
        make_icon(to_fa_digits(d_idx), FA_MONTHS[m_idx - 1], night_p, dark_mode=True, is_rtl=True)
    elif m_ku:
        m_idx, d_idx = int(m_ku.group(1)), int(m_ku.group(2))
        make_icon(to_fa_digits(d_idx), KU_MONTHS[m_idx - 1], light_p, dark_mode=False, is_rtl=True)
        make_icon(to_fa_digits(d_idx), KU_MONTHS[m_idx - 1], night_p, dark_mode=True, is_rtl=True)
    elif m_en:
        m_idx, d_idx = int(m_en.group(1)), int(m_en.group(2))
        make_icon(str(d_idx), EN_MONTHS[m_idx - 1], light_p, dark_mode=False, is_rtl=False)
        make_icon(str(d_idx), EN_MONTHS[m_idx - 1], night_p, dark_mode=True, is_rtl=False)
    else:
        make_timeless_launcher_icon(light_p, dark_mode=False)
        make_timeless_launcher_icon(night_p, dark_mode=True)

print(f"Generated clean IRANSans Regular icons for {len(mipmap_refs)} refs (FA/KU/EN).")


# ====== BUNDLE ALL APP FONTS LOCALLY INTO assets/www/fonts/ (v71.1) ======
def bundle_local_web_fonts():
    fonts_dir = "app/src/main/assets/www/fonts"
    os.makedirs(fonts_dir, exist_ok=True)
    font_specs = {
        "Vazirmatn-Regular.woff2": [
            "https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@v33.003/fonts/webfonts/Vazirmatn-Regular.woff2",
            "https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/webfonts/Vazirmatn-Regular.woff2",
        ],
        "Vazirmatn-Bold.woff2": [
            "https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@v33.003/fonts/webfonts/Vazirmatn-Bold.woff2",
            "https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/webfonts/Vazirmatn-Bold.woff2",
        ],
        "IRANSans-Regular.ttf": [
            "https://raw.githubusercontent.com/mhmmdq/IRANSans-Font/master/IRANSansWeb(FaNum).ttf",
            "https://raw.githubusercontent.com/mhmmdq/IRANSans-Font/master/IRANSansWeb.ttf",
            "https://raw.githubusercontent.com/Aryan1379/IranSans-Font/master/IRANSans(FaNum).ttf",
            "https://raw.githubusercontent.com/mortezamasoumi/iran-sans-font/master/IRANSansMobile.ttf",
        ],
        "IRANSans-Bold.ttf": [
            "https://raw.githubusercontent.com/mhmmdq/IRANSans-Font/master/IRANSansWeb(FaNum)_Bold.ttf",
            "https://raw.githubusercontent.com/mhmmdq/IRANSans-Font/master/IRANSansWeb_Bold.ttf",
            "https://raw.githubusercontent.com/Aryan1379/IranSans-Font/master/IRANSans(FaNum)_Bold.ttf",
            "https://raw.githubusercontent.com/mortezamasoumi/iran-sans-font/master/IRANSansMobile_Bold.ttf",
        ],
        "Estedad-Regular.woff2": [
            "https://cdn.jsdelivr.net/gh/aminabedi68/Estedad@master/fonts/webfonts/statics/Estedad-Regular.woff2",
        ],
        "Estedad-Bold.woff2": [
            "https://cdn.jsdelivr.net/gh/aminabedi68/Estedad@master/fonts/webfonts/statics/Estedad-Bold.woff2",
        ],
        "Sahel-Regular.woff2": [
            "https://cdn.jsdelivr.net/gh/rastikerdar/sahel-font@v3.4.0/dist/Sahel.woff2",
        ],
        "Sahel-Bold.woff2": [
            "https://cdn.jsdelivr.net/gh/rastikerdar/sahel-font@v3.4.0/dist/Sahel-Bold.woff2",
        ],
        "Dastkhat-Regular.woff2": [
            "https://cdn.jsdelivr.net/gh/dastyar-io/dastkhat-font@main/dist/Dastkhat-Regular.woff2",
            "https://raw.githubusercontent.com/dastyar-io/dastkhat-font/main/dist/Dastkhat-Regular.woff2",
        ],
        "Dastkhat-Bold.woff2": [
            "https://cdn.jsdelivr.net/gh/dastyar-io/dastkhat-font@main/dist/Dastkhat-Bold.woff2",
            "https://raw.githubusercontent.com/dastyar-io/dastkhat-font/main/dist/Dastkhat-Bold.woff2",
        ],
        "Inter-Regular.woff2": [
            "https://cdn.jsdelivr.net/gh/rsms/inter@v4.0/docs/font-files/Inter-Regular.woff2",
            "https://cdn.jsdelivr.net/npm/@fontsource/inter@5.0.16/files/inter-latin-400-normal.woff2",
        ],
        "Inter-Bold.woff2": [
            "https://cdn.jsdelivr.net/gh/rsms/inter@v4.0/docs/font-files/Inter-Bold.woff2",
            "https://cdn.jsdelivr.net/npm/@fontsource/inter@5.0.16/files/inter-latin-700-normal.woff2",
        ],
        "NotoSansArabic-Regular.ttf": [
            "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansarabic/NotoSansArabic%5Bwdth%2Cwght%5D.ttf",
        ],
        "NotoSansArabic-Bold.ttf": [
            "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansarabic/NotoSansArabic%5Bwdth%2Cwght%5D.ttf",
        ],
        "NotoNaskhArabic-Regular.ttf": [
            "https://raw.githubusercontent.com/google/fonts/main/ofl/notonaskharabic/NotoNaskhArabic%5Bwght%5D.ttf",
        ],
        "NotoNaskhArabic-Bold.ttf": [
            "https://raw.githubusercontent.com/google/fonts/main/ofl/notonaskharabic/NotoNaskhArabic%5Bwght%5D.ttf",
        ],
    }
    ok_count = 0
    for fname, urls in font_specs.items():
        out_p = os.path.join(fonts_dir, fname)
        if os.path.exists(out_p) and os.path.getsize(out_p) > 5000:
            ok_count += 1
            continue
        for u in urls:
            try:
                data = _download_bytes(u, timeout=20)
                if len(data) > 5000:
                    with open(out_p, "wb") as f:
                        f.write(data)
                    ok_count += 1
                    print(f"Bundled local font: {fname} ({len(data)} bytes)")
                    break
            except Exception:
                continue
    print(f"Local offline web fonts bundled: {ok_count}/{len(font_specs)}")

bundle_local_web_fonts()
