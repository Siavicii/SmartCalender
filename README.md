<p align="center">
  <h1>📅 Smart Cal — Web & PWA</h1>
  <p><strong>A Fast, Installable, Multi-Calendar Progressive Web App (Persian, Kurdish & Gregorian)</strong></p>
  <p>
    <a href="#english">🇬🇧 English</a> •
    <a href="#persian">🇮🇷 فارسی</a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/Version-v64.3-blue?style=for-the-badge" alt="Version" />
    <img src="https://img.shields.io/badge/Platform-PWA%20%7C%20Web-1F2B3B?style=for-the-badge&logo=pwa&logoColor=white" alt="Platform" />
    <img src="https://img.shields.io/badge/Hosting-GitHub%20Pages-222222?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Pages" />
    <img src="https://img.shields.io/badge/Languages-FA%20%7C%20KU%20%7C%20EN-orange?style=for-the-badge" alt="Languages" />
  </p>
</p>

---

<a id="english"></a>

## 🇬🇧 Overview

**Smart Cal PWA** is a lightweight, offline-capable Progressive Web App supporting **Persian (Jalali)**, **Kurdish (Sorani)**, and **Gregorian** calendar systems. Designed for instant installation on Android, iOS, and Desktop directly from the browser via **GitHub Pages**, it delivers full feature parity with the native Android edition—including live financial rates, weather forecasts, horoscope & daily fal, smooth launch animations, and optional Google Calendar synchronization.

---

## 📸 Screenshots

> 💡 *Tip: Create a `screenshots/` folder in your repository root and upload your app screenshots (`home.png`, `styles.png`, `markets.png`, `settings.png`) so they appear automatically below.*

| Home & Calendar | Themes & UI Styles | Weather & Markets | Settings & Sync |
| :---: | :---: | :---: | :---: |
| <img src="screenshots/home.png" width="200" alt="Home Screen" /> | <img src="screenshots/styles.png" width="200" alt="UI Styles" /> | <img src="screenshots/markets.png" width="200" alt="Weather & Financial Rates" /> | <img src="screenshots/settings.png" width="200" alt="Settings & Google Sync" /> |

---

## ✨ Key Features

- **🌐 Tri-Calendar & Tri-Language Support:** Switch seamlessly between **Persian (Jalali)**, **Kurdish (Sorani)**, and **English (Gregorian)** with full localization.
- **📱 True PWA Experience (Offline & Installable):** Powered by a Service Worker (`sw.js`) and Web App Manifest (`manifest.json`) with the minimalist **LINKS** maskable icon (`#1F2B3B` theme) for clean home-screen integration on iOS and Android.
- **⚡ 60fps Launch & Lag-Free Startup:** Hardware-accelerated minimalist splash animation with staggered background network loading so the UI renders immediately without freezing.
- **💹 Real-Time Financial & Gold Rates:** Synchronized with the Android edition using TGJU (`call5` / `call4` fallback) and global markets via a **6-route parallel CORS proxy race** for fast, reliable updates on mobile networks.
- **📆 Optional Google Calendar Sync (`OAuth 2.0` + `.ics`):**
  - Connect directly to your Google Account using **Google Identity Services** for two-way event viewing and creation.
  - Built-in quick **"Add to Google Calendar"** button and standard **`.ics` export** that works immediately with zero API setup.
- **🔮 Horoscope & Daily Fal:** Fully restored and integrated daily horoscope and fal section.
- **🎨 8 Modern UI Styles & Live Weather:** Includes *iOS 26 Liquid Glass*, *Android 17 Expressive*, *Bento*, *Swiss Minimal*, automatic Dark/Light themes, and live weather forecasts.

---

## 📲 How to Install on Mobile

### Android (Chrome / Edge)
1. Open the hosted GitHub Pages URL in Chrome.
2. Tap the **⋮ (Menu)** button and select **Add to Home screen** / **Install app**.

### iOS (Safari)
1. Open the hosted GitHub Pages URL in Safari.
2. Tap the **Share** icon (`⬆️`) at the bottom and select **Add to Home Screen**.

---

## 🔑 Optional: Enabling Live Google Calendar OAuth

If you want direct two-way Google Account login on your GitHub Pages domain:
1. Open [Google Cloud Console](https://console.cloud.google.com) and create/select a project.
2. Go to **APIs & Services > Library** and enable **Google Calendar API**.
3. Go to **APIs & Services > Credentials > Create Credentials > OAuth client ID** and choose **Web application**.
4. Under **Authorized JavaScript origins**, add your GitHub Pages domain (e.g., `https://<username>.github.io`).
5. Paste the generated **Client ID** inside **Settings > Google Calendar Sync** in Smart Cal PWA and sign in.

---

## 🏗️ Repository Structure

```text
SmartCal-PWA/
├── index.html          # Complete single-file application core (v64.3)
├── manifest.json       # PWA metadata, theme colors (#1F2B3B), and icon definitions
├── sw.js               # Service Worker for offline support and automatic cache updates
├── version.json        # Version metadata for update checks (64.3)
├── icon-192.png        # Minimalist LINKS PWA icon (192x192)
├── icon-512.png        # Minimalist LINKS PWA icon (512x512)
└── .nojekyll           # Ensures clean static deployment on GitHub Pages
```

---

## 🚀 Deploying to GitHub Pages

1. Upload all 7 files from the release archive directly to the root of your repository.
2. Go to **Settings > Pages** in your GitHub repository.
3. Under **Build and deployment**, select **Deploy from a branch**, choose `main` and `/ (root)`, then click **Save**.
4. Your PWA will be live within a couple of minutes.

---

<br />

<a id="persian"></a>
<div dir="rtl">

## 🇮🇷 معرفی نسخه وب و PWA (فارسی)

**Smart Cal PWA** نسخه تحت‌وب و قابل‌نصب اپلیکیشن تقویم هوشمند است که تقویم‌های **شمسی، کوردی و میلادی** را بدون نیاز به دانلود فایل نصبی، مستقیماً از طریق مرورگر روی گوشی‌های **اندروید، آیفون (iOS) و دسکتاپ** اجرا می‌کند. این نسخه از نظر امکانات و طراحی کاملاً با نسخه بومی اندروید هماهنگ است و از طریق **GitHub Pages** میزبانی می‌شود.

---

## 📸 تصاویر برنامه

> 💡 *راهنما: پوشه‌ای با نام `screenshots` در ریشه مخزن گیت‌هاب بسازید و تصاویر برنامه را با نام‌های `home.png`، `styles.png`، `markets.png` و `settings.png` در آن آپلود کنید تا در جدول زیر نمایش داده شوند.*

| صفحه اصلی و تقویم | سبک‌های طراحی | آب‌وهوا و بازار مالی | تنظیمات و همگام‌سازی |
| :---: | :---: | :---: | :---: |
| <img src="screenshots/home.png" width="200" alt="صفحه اصلی" /> | <img src="screenshots/styles.png" width="200" alt="سبک‌های طراحی" /> | <img src="screenshots/markets.png" width="200" alt="آب‌وهوا و نرخ ارز" /> | <img src="screenshots/settings.png" width="200" alt="تنظیمات" /> |

---

## ✨ امکانات کلیدی (نسخه `v64.3`)

- **🌐 پشتیبانی از ۳ تقویم و ۳ زبان:** جابه‌جایی سریع میان تقویم‌های **شمسی (فارسی)**، **کوردی (سورانی)** و **میلادی (انگلیسی)** با ترجمه کامل رابط کاربری.
- **📱 تجربه کامل PWA (آفلاین و قابل‌نصب):** بهره‌گیری از Service Worker (`sw.js`) و آیکون مینیمال و مفهومی **LINKS** روی پس‌زمینه سرمه‌ای `#1F2B3B` (سازگار با آیکون‌های Maskable در اندروید و iOS) به همراه بروزرسانی خودکار کش پس از انتشار نسخه جدید.
- **⚡ شروع روان و بدون لگ (۶۰ فریم):** انیمیشن خوش‌آمدگویی مینیمال با شتاب سخت‌افزاری و انتقال درخواست‌های شبکه به پس‌زمینه پس از پایان انیمیشن.
- **💹 نرخ لحظه‌ای طلا، سکه و ارز (هماهنگ با اندروید):** دریافت نرخ زنده از سرورهای TGJU (`call5` و جایگزین `call4`) و بازار جهانی با **مکانیزم مسابقه موازی ۶ مسیر پروکسی (CORS Race)** برای پایداری بالا روی اینترنت موبایل.
- **📆 همگام‌سازی اختیاری با تقویم گوگل (`OAuth 2.0` + فایل `.ics`):**
  - اتصال زنده و دوطرفه به اکانت گوگل کاربر از طریق **Google Identity Services** برای مشاهده و ثبت رویدادها.
  - دکمه سریع **«ثبت در Google Calendar»** و **خروجی استاندارد `.ics`** بدون نیاز به کلید API.
- **🔮 بخش طالع و فال:** نمایش کامل و اصلاح‌شده بخش طالع‌بینی و فال روزانه در نسخه `v64.3`.
- **🎨 ۸ سبک طراحی مدرن و آب‌وهوای زنده:** پشتیبانی از استایل‌های متنوع (*Liquid Glass*، *Expressive*، *Bento*، *Swiss Minimal* و...)، حالت شب و روز خودکار و پیش‌بینی وضعیت هوا.

---

## 📲 راهنمای نصب روی گوشی

### اندروید (Chrome / Edge)
1. لینک GitHub Pages برنامه را در مرورگر کروم باز کنید.
2. روی منوی **⋮** بزنید و گزینه **Install app** یا **Add to Home screen** را انتخاب کنید.

### آیفون / iOS (Safari)
1. لینک برنامه را در مرورگر سافاری باز کنید.
2. روی دکمه **Share** (`⬆️`) در پایین صفحه بزنید و گزینه **Add to Home Screen** را انتخاب کنید.

---

## 🔑 راهنمای فعال‌سازی اتصال زنده به اکانت گوگل (اختیاری)

برای فعال‌کردن ورود مستقیم با اکانت گوگل روی دامنه GitHub Pages (فقط یک‌بار):
1. وارد [Google Cloud Console](https://console.cloud.google.com) شوید و یک پروژه بسازید.
2. از بخش **APIs & Services > Library**، سرویس **Google Calendar API** را فعال (**Enable**) کنید.
3. در **APIs & Services > Credentials**، روی **Create Credentials > OAuth client ID** بزنید و نوع آن را **Web application** انتخاب کنید.
4. در بخش **Authorized JavaScript origins**، آدرس دامنه GitHub Pages خود (مثلاً `https://siavicii.github.io`) را ثبت و ذخیره کنید.
5. شناسه `Client ID` را در بخش **تنظیمات > همگام‌سازی با تقویم گوگل** داخل برنامه وارد کرده و لاگین کنید.

---

## 🚀 راه‌اندازی روی GitHub Pages

1. هر ۷ فایل موجود در بسته نهایی (`index.html`، `manifest.json`، `sw.js`، `version.json`، `icon-192.png`، `icon-512.png` و `.nojekyll`) را در ریشه مخزن گیت‌هاب آپلود و **Commit** کنید.
2. وارد **Settings > Pages** در مخزن خود شوید.
3. در بخش **Build and deployment**، گزینه **Deploy from a branch** را انتخاب کرده، شاخه `main` و مسیر `/ (root)` را تنظیم و ذخیره کنید.

---

<p align="center">
  ساخته‌شده با ❤️ برای تجربه‌ای سریع، سبک و هوشمند در وب و موبایل
</p>

</div>