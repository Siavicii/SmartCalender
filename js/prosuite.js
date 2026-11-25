/* ========================================================================
   SMART CAL v74 PRO SUITE ENGINE
   (Portfolio + SVG Donut, Price Alerts, Converter, Recurring Reminders,
    Global Search, Prayer Times, Full JSON Backup, Privacy Lock, Haptics,
    Market Widget Sync)
   ======================================================================== */
(function(){
  const PF_KEY = "smartcal_portfolio_v74";
  const PA_KEY = "smartcal_price_alerts_v74";
  const REC_KEY = "smartcal_recurring_v74";
  const LOCK_KEY = "smartcal_privacy_pin_v74";
  const DONUT_COLORS = ["#2563EB","#16A34A","#F59E0B","#DC2626","#8B5CF6","#06B6D4","#EC4899","#14B8A6"];

  function safeJsonParse(key, fb){ try { return JSON.parse(localStorage.getItem(key) || "null") ?? fb; } catch(e){ return fb; } }
  function fmtToman(n){ return fa(Math.round(Number(n)||0).toLocaleString("en-US")); }

  // --- 1. HAPTIC FEEDBACK (Debounced + Delegated for Days, Tabs & Buttons) ---
  let _lastHapticMs = 0;
  window.triggerHaptic = function(kind = "light") {
    if (cfg && cfg.haptic === false) return;
    const now = Date.now();
    if (now - _lastHapticMs < 35) return;
    _lastHapticMs = now;
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.triggerHaptic === "function") {
        window.AndroidBridge.triggerHaptic(kind);
        return;
      }
      if (navigator && typeof navigator.vibrate === "function") {
        navigator.vibrate(kind === "heavy" ? 22 : (kind === "medium" ? 14 : 9));
      }
    } catch(e) {}
  };
  document.addEventListener("click", function(ev) {
    if (cfg && cfg.haptic === false) return;
    const t = ev.target && ev.target.closest
      ? ev.target.closest("#calCard .d, #calCard .cell, #calCard .m-day, .nbtn, .pill, .fchip, .ibtn, .w-city-btn, .sc-switch")
      : null;
    if (t) window.triggerHaptic("light");
  }, true);

  // --- 2. MARKET CATALOG HELPER ---
  function getAllLiveAssets() {
    const d = (typeof lastMarketPayload !== "undefined" && lastMarketPayload) ? lastMarketPayload : null;
    const list = [];
    if (d) {
      ["gold", "fx"].forEach(sec => {
        if (Array.isArray(d[sec])) {
          d[sec].forEach(it => {
            if (it && it.ico && Number(it.p) > 0 && it.ico !== "ounce" && it.ico !== "silver_oz") {
              list.push({ ico: it.ico, n: it.n || it.ico, p: Number(it.p), c: it.c || "0%" });
            }
          });
        }
      });
    }
    if (!list.length) {
      list.push(
        { ico: "g18", n: "طلای ۱۸ عیار (گرم)", p: 0, c: "0%" },
        { ico: "emami", n: "سکه امامی", p: 0, c: "0%" },
        { ico: "usd", n: "دلار آمریکا", p: 0, c: "0%" },
        { ico: "eur", n: "یورو اروپا", p: 0, c: "0%" }
      );
    }
    return list;
  }

  window.switchMktProTab = function(tab, btn) {
    triggerHaptic("light");
    ["pf","alert","conv"].forEach(t => {
      const el = document.getElementById("mktProTab-" + t);
      if (el) el.style.display = (t === tab) ? "flex" : "none";
    });
    document.querySelectorAll("#mktProTabs .fchip").forEach(c => c.classList.remove("act"));
    if (btn) btn.classList.add("act");
    if (tab === "conv") calcMarketConverter();
  };

  function populateAssetSelects() {
    const assets = getAllLiveAssets();
    const opts = assets.map(a => `<option value="${a.ico}">${a.n}${a.p > 0 ? " (" + fmtToman(a.p) + ")" : ""}</option>`).join("");
    ["pfAssetSel", "paAssetSel"].forEach(id => {
      const el = document.getElementById(id);
      if (el) {
        const prev = el.value;
        el.innerHTML = opts;
        if (prev && assets.some(a => a.ico === prev)) el.value = prev;
      }
    });
    const convOpts = `<option value="toman">${tr("تومان ایران", "تمەن", "Iranian Toman")}</option>` + opts;
    ["mcFromSel", "mcToSel"].forEach((id, idx) => {
      const el = document.getElementById(id);
      if (el) {
        const prev = el.value;
        el.innerHTML = convOpts;
        if (prev) el.value = prev;
        else el.value = idx === 0 ? (assets.some(a => a.ico === "usd") ? "usd" : "toman") : "toman";
      }
    });
  }

  // --- 3. PORTFOLIO TRACKER + SVG DONUT ---
  window.addPortfolioItem = function() {
    const ico = document.getElementById("pfAssetSel")?.value;
    const qty = Number(document.getElementById("pfQtyInp")?.value || 0);
    if (!ico || !isFinite(qty) || qty <= 0) {
      if (typeof showToast === "function") showToast(tr("مقدار معتبر وارد کنید", "بڕی دروست بنووسە", "Enter a valid quantity"));
      return;
    }
    const items = safeJsonParse(PF_KEY, []);
    const existing = items.find(x => x.ico === ico);
    if (existing) existing.qty = Number((Number(existing.qty) + qty).toFixed(4));
    else items.push({ ico, qty: Number(qty.toFixed(4)) });
    localStorage.setItem(PF_KEY, JSON.stringify(items));
    document.getElementById("pfQtyInp").value = "";
    triggerHaptic("medium");
    renderPortfolio();
  };

  window.removePortfolioItem = function(ico) {
    const items = safeJsonParse(PF_KEY, []).filter(x => x.ico !== ico);
    localStorage.setItem(PF_KEY, JSON.stringify(items));
    triggerHaptic("light");
    renderPortfolio();
  };

  window.renderPortfolio = function() {
    const assets = getAllLiveAssets();
    const map = Object.fromEntries(assets.map(a => [a.ico, a]));
    const items = safeJsonParse(PF_KEY, []);
    const listEl = document.getElementById("pfItemsList");
    const svgEl = document.getElementById("pfDonutSvg");
    const cntEl = document.getElementById("pfDonutCount");
    const totEl = document.getElementById("pfTotalToman");
    const usdEl = document.getElementById("pfTotalUsd");
    const chgEl = document.getElementById("pfDailyChg");
    if (!listEl || !svgEl) return;

    let totalToman = 0, weightedChg = 0;
    const rows = items.map((it, idx) => {
      const meta = map[it.ico] || { n: it.ico, p: 0, c: "0%" };
      const val = (Number(meta.p) || 0) * (Number(it.qty) || 0);
      const chgNum = Number(String(meta.c || "0").replace(/[^0-9.\-+]/g, "")) || 0;
      totalToman += val;
      weightedChg += val * chgNum;
      return { ico: it.ico, n: meta.n, qty: it.qty, val, color: DONUT_COLORS[idx % DONUT_COLORS.length], chg: meta.c || "0%" };
    }).sort((a, b) => b.val - a.val);

    const usdRate = (map.usd && map.usd.p > 0) ? map.usd.p : ((typeof lastMarketPayload !== "undefined" && lastMarketPayload && lastMarketPayload.usd) ? lastMarketPayload.usd : 0);
    if (totEl) totEl.textContent = fmtToman(totalToman) + " " + tr("تومان", "تمەن", "Toman");
    if (usdEl) usdEl.textContent = usdRate > 0 ? "≈ $" + fa((totalToman / usdRate).toFixed(1)) : "≈ $--";
    const avgChg = totalToman > 0 ? (weightedChg / totalToman) : 0;
    if (chgEl) {
      chgEl.textContent = tr("تغییر روزانه: ", "گۆڕانی ڕۆژانە: ", "Daily: ") + (avgChg >= 0 ? "+" : "") + fa(avgChg.toFixed(2)) + "٪";
      chgEl.style.color = avgChg >= 0 ? "var(--grn)" : "var(--hol)";
    }
    if (cntEl) cntEl.textContent = fa(rows.length) + " " + tr("دارایی", "دارایی", "assets");

    // Draw SVG Donut Chart
    if (totalToman <= 0 || !rows.length) {
      svgEl.innerHTML = `<circle cx="21" cy="21" r="15.915" fill="transparent" stroke="var(--brd)" stroke-width="5"/>`;
      listEl.innerHTML = `<div style="font-size:11px;color:var(--sec);text-align:center;padding:8px">${tr("هنوز دارایی ثبت نشده است. طلا، سکه یا ارز خود را اضافه کنید.", "هیچ داراییەک تۆمار نەکراوە.", "No holdings added yet.")}</div>`;
      return;
    }
    let offset = 0;
    svgEl.innerHTML = rows.map(r => {
      const pct = (r.val / totalToman) * 100;
      const seg = `<circle cx="21" cy="21" r="15.915" fill="transparent" stroke="${r.color}" stroke-width="5.2" stroke-dasharray="${pct.toFixed(2)} ${(100 - pct).toFixed(2)}" stroke-dashoffset="${(-offset).toFixed(2)}"/>`;
      offset += pct;
      return seg;
    }).join("");

    listEl.innerHTML = rows.map(r => {
      const share = totalToman > 0 ? ((r.val / totalToman) * 100).toFixed(1) : "0";
      return `<div class="pf-item">
        <div style="display:flex;align-items:center;gap:7px;min-width:0">
          <span class="pf-dot" style="background:${r.color}"></span>
          <div style="min-width:0">
            <b style="display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(r.n)}</b>
            <span style="font-size:10px;color:var(--sec)">${fa(r.qty)} × • ${fa(share)}٪</span>
          </div>
        </div>
        <div style="display:flex;align-items:center;gap:6px">
          <b>${fmtToman(r.val)}</b>
          <button type="button" class="ibtn" onclick="removePortfolioItem('${esc(r.ico)}')" style="height:24px;padding:0 6px;font-size:11px;color:var(--hol)">✕</button>
        </div>
      </div>`;
    }).join("");
  };

  // --- 4. PRICE ALERTS (with 3-Digit Formatting & Live Number-to-Words) ---
  function normalizePriceDigits(str) {
    return String(str || "")
      .replace(/[۰-۹]/g, d => String("۰۱۲۳۴۵۶۷۸۹".indexOf(d)))
      .replace(/[٠-٩]/g, d => String("٠١٢٣٤٥٦٧٨٩".indexOf(d)))
      .replace(/[^0-9]/g, "")
      .slice(0, 15);
  }

  function numberToWordsFa(n) {
    n = Math.floor(Number(n) || 0);
    if (n <= 0) return "";
    const ones = ["", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه"];
    const teens = ["ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"];
    const tens = ["", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"];
    const hundreds = ["", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد"];
    const scales = ["", "هزار", "میلیون", "میلیارد", "تریلیون"];
    function chunk(num) {
      const p = [];
      const h = Math.floor(num / 100), r = num % 100;
      if (h) p.push(hundreds[h]);
      if (r >= 10 && r < 20) p.push(teens[r - 10]);
      else {
        const t = Math.floor(r / 10), o = r % 10;
        if (t) p.push(tens[t]);
        if (o) p.push(ones[o]);
      }
      return p.join(" و ");
    }
    const parts = [];
    let idx = 0;
    while (n > 0 && idx < scales.length) {
      const c = n % 1000;
      if (c > 0) {
        const w = chunk(c);
        parts.unshift(scales[idx] ? (w + " " + scales[idx]) : w);
      }
      n = Math.floor(n / 1000);
      idx++;
    }
    return parts.join(" و ");
  }

  function numberToWordsKu(n) {
    n = Math.floor(Number(n) || 0);
    if (n <= 0) return "";
    const ones = ["", "یەک", "دوو", "سێ", "چوار", "پێنج", "شەش", "حەوت", "هەشت", "نۆ"];
    const teens = ["دە", "یازدە", "دوازدە", "سێزدە", "چواردە", "پازدە", "شازدە", "حەڤدە", "هەژدە", "نۆزدە"];
    const tens = ["", "", "بیست", "سی", "چل", "پەنجا", "شەست", "حەفتا", "هەشتا", "نەوەد"];
    const hundreds = ["", "سەد", "دووسەد", "سێسەد", "چوارسەد", "پێنجسەد", "شەشسەد", "حەوتسەد", "هەشتسەد", "نۆسەد"];
    const scales = ["", "هەزار", "ملیۆن", "ملیار", "ترلیۆن"];
    function chunk(num) {
      const p = [];
      const h = Math.floor(num / 100), r = num % 100;
      if (h) p.push(hundreds[h]);
      if (r >= 10 && r < 20) p.push(teens[r - 10]);
      else {
        const t = Math.floor(r / 10), o = r % 10;
        if (t) p.push(tens[t]);
        if (o) p.push(ones[o]);
      }
      return p.join(" و ");
    }
    const parts = [];
    let idx = 0;
    while (n > 0 && idx < scales.length) {
      const c = n % 1000;
      if (c > 0) {
        const w = chunk(c);
        parts.unshift(scales[idx] ? (w + " " + scales[idx]) : w);
      }
      n = Math.floor(n / 1000);
      idx++;
    }
    return parts.join(" و ");
  }

  function numberToWordsEn(n) {
    n = Math.floor(Number(n) || 0);
    if (n <= 0) return "";
    const ones = ["", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"];
    const teens = ["ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"];
    const tens = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"];
    const scales = ["", "thousand", "million", "billion", "trillion"];
    function chunk(num) {
      const p = [];
      const h = Math.floor(num / 100), r = num % 100;
      if (h) p.push(ones[h] + " hundred");
      if (r >= 10 && r < 20) p.push(teens[r - 10]);
      else {
        const t = Math.floor(r / 10), o = r % 10;
        if (t && o) p.push(tens[t] + "-" + ones[o]);
        else if (t) p.push(tens[t]);
        else if (o) p.push(ones[o]);
      }
      return p.join(" ");
    }
    const parts = [];
    let idx = 0;
    while (n > 0 && idx < scales.length) {
      const c = n % 1000;
      if (c > 0) {
        const w = chunk(c);
        parts.unshift(scales[idx] ? (w + " " + scales[idx]) : w);
      }
      n = Math.floor(n / 1000);
      idx++;
    }
    const out = parts.join(", ");
    return out.charAt(0).toUpperCase() + out.slice(1);
  }

  function formatNumberToWordsLocal(num) {
    const l = (cfg && cfg.lang) || "fa";
    if (l === "en") return numberToWordsEn(num) + " Toman";
    if (l === "ku") return numberToWordsKu(num) + " تمەن";
    return numberToWordsFa(num) + " تومان";
  }

  window.onPaTargetInput = function(inp) {
    if (!inp) return;
    const raw = normalizePriceDigits(inp.value);
    const wordsEl = document.getElementById("paTargetWords");
    if (!raw) {
      inp.value = "";
      if (wordsEl) { wordsEl.style.display = "none"; wordsEl.textContent = ""; }
      return;
    }
    const num = Number(raw);
    const formatted = isFinite(num) ? num.toLocaleString("en-US") : raw;
    if (inp.value !== formatted) inp.value = formatted;
    if (wordsEl) {
      if (isFinite(num) && num > 0) {
        wordsEl.textContent = "💬 " + formatNumberToWordsLocal(num);
        wordsEl.style.display = "block";
      } else {
        wordsEl.style.display = "none";
      }
    }
  };

  window.addPriceAlert = function() {
    const ico = document.getElementById("paAssetSel")?.value;
    const dir = document.getElementById("paDirSel")?.value || "above";
    const inp = document.getElementById("paTargetInp");
    const raw = normalizePriceDigits(inp?.value || "");
    const target = Number(raw || 0);
    if (!ico || !isFinite(target) || target <= 0) {
      if (typeof showToast === "function") showToast(tr("قیمت هدف معتبر وارد کنید", "نرخی دروست بنووسە", "Enter a valid target price"));
      return;
    }
    const alerts = safeJsonParse(PA_KEY, []);
    alerts.push({ id: "pa_" + Date.now(), ico, dir, target: Math.round(target), fired: false });
    localStorage.setItem(PA_KEY, JSON.stringify(alerts));
    if (inp) inp.value = "";
    const wordsEl = document.getElementById("paTargetWords");
    if (wordsEl) { wordsEl.style.display = "none"; wordsEl.textContent = ""; }
    triggerHaptic("medium");
    renderPriceAlerts();
    evaluatePriceAlerts();
  };

  window.removePriceAlert = function(id) {
    const alerts = safeJsonParse(PA_KEY, []).filter(a => a.id !== id);
    localStorage.setItem(PA_KEY, JSON.stringify(alerts));
    renderPriceAlerts();
  };

  window.renderPriceAlerts = function() {
    const el = document.getElementById("paList");
    if (!el) return;
    const assets = Object.fromEntries(getAllLiveAssets().map(a => [a.ico, a]));
    const alerts = safeJsonParse(PA_KEY, []);
    if (!alerts.length) {
      el.innerHTML = `<div style="font-size:11px;color:var(--sec);text-align:center;padding:6px">${tr("هشدار قیمتی فعالی ثبت نشده است.", "هیچ ئاگادارکەرەوەیەک نییە.", "No active price alerts.")}</div>`;
      return;
    }
    el.innerHTML = alerts.map(a => {
      const name = (assets[a.ico] && assets[a.ico].n) || a.ico;
      const cond = a.dir === "above" ? "📈 ≥" : "📉 ≤";
      const badge = a.fired ? `<span class="gs-badge" style="background:rgba(22,163,74,.15);color:var(--grn)">${tr("✅ فعال شد", "✅ چالاک بوو", "✅ Triggered")}</span>` : `<span class="gs-badge">${tr("در انتظار", "چاوەڕوانە", "Pending")}</span>`;
      return `<div class="pf-item">
        <div><b>${esc(name)}</b> <span style="color:var(--sec)">${cond} ${fmtToman(a.target)}</span></div>
        <div style="display:flex;align-items:center;gap:6px">${badge}<button type="button" class="ibtn" onclick="removePriceAlert('${esc(a.id)}')" style="height:24px;padding:0 6px;color:var(--hol)">✕</button></div>
      </div>`;
    }).join("");
  };

  function evaluatePriceAlerts() {
    const assets = Object.fromEntries(getAllLiveAssets().map(a => [a.ico, a]));
    const alerts = safeJsonParse(PA_KEY, []);
    let changed = false;
    alerts.forEach(a => {
      if (a.fired) return;
      const live = assets[a.ico];
      if (!live || live.p <= 0) return;
      const hit = (a.dir === "above" && live.p >= a.target) || (a.dir === "below" && live.p <= a.target);
      if (hit) {
        a.fired = true;
        changed = true;
        const msg = `${live.n} به ${fmtToman(live.p)} تومان رسید (هدف: ${fmtToman(a.target)})`;
        if (typeof showToast === "function") showToast("🔔 " + msg);
        try {
          if (window.AndroidBridge && typeof window.AndroidBridge.showAlertNotification === "function") {
            window.AndroidBridge.showAlertNotification("🔔 هشدار قیمت Smart Cal", msg);
          } else if ("Notification" in window && Notification.permission === "granted") {
            new Notification("🔔 هشدار قیمت Smart Cal", { body: msg });
          }
        } catch(e) {}
      }
    });
    if (changed) {
      localStorage.setItem(PA_KEY, JSON.stringify(alerts));
      renderPriceAlerts();
    }
  }

  // --- 5. QUICK ASSET CONVERTER ---
  window.calcMarketConverter = function() {
    const amt = Number(document.getElementById("mcAmtInp")?.value || 0);
    const fromKey = document.getElementById("mcFromSel")?.value || "usd";
    const toKey = document.getElementById("mcToSel")?.value || "toman";
    const resEl = document.getElementById("mcResultBox");
    if (!resEl) return;
    const assets = Object.fromEntries(getAllLiveAssets().map(a => [a.ico, a]));
    const pFrom = fromKey === "toman" ? 1 : (assets[fromKey]?.p || 0);
    const pTo = toKey === "toman" ? 1 : (assets[toKey]?.p || 0);
    if (!isFinite(amt) || pFrom <= 0 || pTo <= 0) {
      resEl.textContent = tr("در انتظار نرخ لحظه‌ای...", "چاوەڕوانی نرخی ڕاستەوخۆ...", "Waiting for live rates...");
      return;
    }
    const out = (amt * pFrom) / pTo;
    const toLabel = toKey === "toman" ? tr("تومان", "تمەن", "Toman") : (assets[toKey]?.n || toKey);
    const formatted = out >= 100 ? fmtToman(out) : fa(out.toFixed(4));
    resEl.textContent = `${fa(amt)} ⟵ ${formatted} ${toLabel}`;
  };

  window.swapMarketConverter = function() {
    const f = document.getElementById("mcFromSel");
    const t = document.getElementById("mcToSel");
    if (f && t) { const tmp = f.value; f.value = t.value; t.value = tmp; triggerHaptic("light"); calcMarketConverter(); }
  };

  // --- 6. RECURRING REMINDERS & INSTALLMENTS ---
  window.toggleRecurringForm = function() {
    const f = document.getElementById("recFormBox");
    if (f) f.style.display = (f.style.display === "none" || !f.style.display) ? "flex" : "none";
  };

  window.saveRecurringItem = function() {
    const title = (document.getElementById("recTitleInp")?.value || "").trim();
    const freq = document.getElementById("recFreqSel")?.value || "monthly";
    const day = Math.max(1, Math.min(31, Number(document.getElementById("recDayInp")?.value || 1)));
    const time = document.getElementById("recTimeInp")?.value || "09:00";
    const prevDay = !!document.getElementById("recNotifyPrev")?.checked;
    const sameDay = !!document.getElementById("recNotifySame")?.checked;
    if (!title) {
      if (typeof showToast === "function") showToast(tr("عنوان را وارد کنید", "ناونیشان بنووسە", "Enter a title"));
      return;
    }
    const list = safeJsonParse(REC_KEY, []);
    const item = { id: "rec_" + Date.now(), title, freq, day, month: (typeof tod !== "undefined" ? tod.jm : 1), time, prevDay, sameDay };
    list.push(item);
    localStorage.setItem(REC_KEY, JSON.stringify(list));
    document.getElementById("recTitleInp").value = "";
    toggleRecurringForm();
    triggerHaptic("medium");
    scheduleRecurringNativeAlarm(item);
    renderRecurringList();
    syncMarketAndTasksWidget();
  };

  window.deleteRecurringItem = function(id) {
    const list = safeJsonParse(REC_KEY, []).filter(x => x.id !== id);
    localStorage.setItem(REC_KEY, JSON.stringify(list));
    triggerHaptic("light");
    renderRecurringList();
    syncMarketAndTasksWidget();
  };

  function getNextOccurrenceDate(item) {
    const now = new Date();
    const [hh, mm] = String(item.time || "09:00").split(":").map(Number);
    if (item.freq === "weekly") {
      const targetDow = (item.day % 7);
      const d = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hh || 9, mm || 0, 0);
      while (d.getTime() <= now.getTime() || ((d.getDay() + 1) % 7) !== targetDow) d.setDate(d.getDate() + 1);
      return d;
    }
    if (typeof tod !== "undefined" && typeof toG === "function") {
      let jy = tod.jy, jm = item.freq === "yearly" ? (item.month || tod.jm) : tod.jm;
      for (let i = 0; i < 14; i++) {
        const maxD = typeof mLen === "function" ? mLen(jy, jm) : 30;
        const g = toG(jy, jm, Math.min(item.day, maxD));
        const cand = new Date(g.gy, g.gm - 1, g.gd, hh || 9, mm || 0, 0);
        if (cand.getTime() > now.getTime()) return cand;
        if (item.freq === "yearly") jy++;
        else { jm++; if (jm > 12) { jm = 1; jy++; } }
      }
    }
    return new Date(now.getTime() + 86400000);
  }

  function scheduleRecurringNativeAlarm(item) {
    if (!window.AndroidBridge || typeof window.AndroidBridge.scheduleNativeAlarm !== "function") return;
    try {
      const nextDt = getNextOccurrenceDate(item);
      const baseCode = Math.abs(String(item.id).split("").reduce((a, c) => ((a << 5) - a + c.charCodeAt(0)) | 0, 0)) % 40000 + 5000;
      if (item.sameDay) {
        window.AndroidBridge.scheduleNativeAlarm(nextDt.getTime(), "🔁 " + item.title, tr("موعد یادآور تکرارشونده امروز", "کاتی یادخەرەوەی ئەمڕۆ", "Recurring reminder due today"), baseCode);
      }
      if (item.prevDay && nextDt.getTime() - 86400000 > Date.now()) {
        window.AndroidBridge.scheduleNativeAlarm(nextDt.getTime() - 86400000, "⏰ فردا: " + item.title, tr("یادآور فردا (۱ روز مانده)", "یادخەرەوەی سبەینێ", "Due tomorrow"), baseCode + 1);
      }
    } catch(e) {}
  }

  window.renderRecurringList = function() {
    const el = document.getElementById("recurringList");
    if (!el) return;
    const list = safeJsonParse(REC_KEY, []);
    if (!list.length) {
      el.innerHTML = `<div style="font-size:11px;color:var(--sec);text-align:center;padding:6px">${tr("هنوز قسط یا یادآور تکرارشونده‌ای ثبت نشده است.", "هیچ یادخەرەوەیەکی دووبارەبووەوە نییە.", "No recurring reminders yet.")}</div>`;
      return;
    }
    const freqLbl = { monthly: tr("ماهانه", "مانگانە", "Monthly"), weekly: tr("هفتگی", "هەفتانە", "Weekly"), yearly: tr("سالانه", "ساڵانە", "Yearly") };
    el.innerHTML = list.map(it => {
      const nextDt = getNextOccurrenceDate(it);
      const diffDays = Math.max(0, Math.ceil((nextDt.getTime() - Date.now()) / 86400000));
      return `<div class="rec-item">
        <div>
          <b>${esc(it.title)}</b>
          <div style="font-size:10px;color:var(--sec)">${esc(freqLbl[it.freq] || it.freq)} • ${tr("روز", "ڕۆژی", "Day")} ${fa(it.day)} • ${fa(it.time)} • <span style="color:var(--pri)">${fa(diffDays)} ${tr("روز مانده", "ڕۆژ ماوە", "days left")}</span></div>
        </div>
        <button type="button" class="ibtn" onclick="deleteRecurringItem('${esc(it.id)}')" style="height:26px;padding:0 7px;color:var(--hol)">✕</button>
      </div>`;
    }).join("");
  };

  // --- 7. GLOBAL SEARCH ---
  window.openGlobalSearchModal = function() {
    triggerHaptic("light");
    const m = document.getElementById("globalSearchModal");
    if (m) {
      m.classList.add("open");
      const inp = document.getElementById("globalSearchInp");
      if (inp) { inp.value = ""; setTimeout(() => inp.focus(), 80); }
      runGlobalSearch("");
    }
  };

  window.jumpFromGlobalSearch = function(jy, jm, jd) {
    if (typeof closeModals === "function") closeModals();
    if (typeof sel !== "undefined") { sel = { jy, jm, jd }; }
    if (typeof syncViewToCalType === "function") syncViewToCalType(cfg.calType || "jalali");
    else if (typeof vy !== "undefined" && typeof vm !== "undefined") { vy = jy; vm = jm; }
    if (typeof switchTab === "function") switchTab("cal");
    if (typeof render === "function") render();
    triggerHaptic("medium");
  };

  window.runGlobalSearch = function(q) {
    const box = document.getElementById("globalSearchResults");
    if (!box) return;
    const term = String(q || "").trim().toLowerCase();
    if (!term) {
      box.innerHTML = `<div style="text-align:center;color:var(--sec);font-size:12px;padding:18px 8px">${tr("عبارتی مثل «نوروز»، «قسط» یا متن یادداشت خود را وارد کنید...", "وشەیەک بنووسە بۆ گەڕان...", "Type a keyword to search notes, reminders, and holidays...")}</div>`;
      return;
    }
    const hits = [];
    // 1. User Notes
    if (typeof notes === "object" && notes) {
      Object.keys(notes).forEach(k => {
        const arr = Array.isArray(notes[k]) ? notes[k] : [];
        const [jy, jm, jd] = k.split("-").map(Number);
        arr.forEach(n => {
          const norm = (typeof normNote === "function") ? normNote(n) : n;
          const txt = String((norm && (norm.t || norm.text || norm.title)) || (typeof n === "string" ? n : "") || "").trim();
          if (txt && txt.toLowerCase().includes(term)) {
            hits.push({ type: tr("یادداشت", "تێبینی", "Note"), title: txt, sub: `${fa(jy)}/${fa(jm)}/${fa(jd)}`, jy, jm, jd });
          }
        });
      });
    }
    // 2. Recurring reminders
    safeJsonParse(REC_KEY, []).forEach(r => {
      if (String(r.title || "").toLowerCase().includes(term)) {
        const nd = getNextOccurrenceDate(r);
        const nj = typeof toJ === "function" ? toJ(nd.getFullYear(), nd.getMonth() + 1, nd.getDate()) : tod;
        hits.push({ type: tr("تکرارشونده", "دووبارە", "Recurring"), title: r.title, sub: `${fa(nj.jy)}/${fa(nj.jm)}/${fa(nj.jd)}`, jy: nj.jy, jm: nj.jm, jd: nj.jd });
      }
    });
    // 3. Official & Emergency Holidays (current year)
    const curY = (typeof tod !== "undefined" && tod.jy) ? tod.jy : 1405;
    if (typeof getEv === "function" && typeof mLen === "function") {
      for (let m = 1; m <= 12; m++) {
        const ml = mLen(curY, m);
        for (let d = 1; d <= ml; d++) {
          const evs = getEv(curY, m, d);
          for (const ev of evs) {
            const t = String(ev?.[0] || "");
            if (t.toLowerCase().includes(term)) {
              hits.push({ type: ev[1] ? tr("تعطیل رسمی", "پشوو", "Holiday") : tr("مناسبت", "بۆنە", "Event"), title: t, sub: `${fa(curY)}/${fa(m)}/${fa(d)}`, jy: curY, jm: m, jd: d });
              if (hits.length >= 40) break;
            }
          }
          if (hits.length >= 40) break;
        }
        if (hits.length >= 40) break;
      }
    }
    if (!hits.length) {
      box.innerHTML = `<div style="text-align:center;color:var(--sec);font-size:12px;padding:18px 8px">${tr("موردی یافت نشد.", "هیچ ئەنجامێک نەدۆزرایەوە.", "No results found.")}</div>`;
      return;
    }
    box.innerHTML = hits.slice(0, 35).map(h => `
      <div class="gs-item" onclick="jumpFromGlobalSearch(${Number(h.jy)},${Number(h.jm)},${Number(h.jd)})">
        <div style="min-width:0">
          <b style="font-size:12px;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(h.title)}</b>
          <span style="font-size:10.5px;color:var(--sec)">📅 ${esc(h.sub)}</span>
        </div>
        <span class="gs-badge">${esc(h.type)}</span>
      </div>`).join("");
  };

  // --- 8. OPTIONAL PRAYER TIMES (Iran Institute of Geophysics Method) ---
  function computePrayerTimes(lat, lon, dateObj) {
    const D2R = Math.PI / 180, R2D = 180 / Math.PI;
    const tz = -dateObj.getTimezoneOffset() / 60;
    const jd = Math.floor((Date.UTC(dateObj.getFullYear(), dateObj.getMonth(), dateObj.getDate()) / 86400000) + 2440587.5);
    const d = jd - 2451545.0;
    const g = (357.529 + 0.98560028 * d) % 360;
    const q = (280.459 + 0.98564736 * d) % 360;
    const L = (q + 1.915 * Math.sin(g * D2R) + 0.020 * Math.sin(2 * g * D2R)) % 360;
    const e = 23.439 - 0.00000036 * d;
    const decl = Math.asin(Math.sin(e * D2R) * Math.sin(L * D2R)) * R2D;
    const RA = Math.atan2(Math.cos(e * D2R) * Math.sin(L * D2R), Math.cos(L * D2R)) * R2D / 15;
    let eqT = q / 15 - ((RA % 24 + 24) % 24);
    if (eqT > 12) eqT -= 24; if (eqT < -12) eqT += 24;
    const dhuhr = 12 + tz - lon / 15 - eqT;
    function hourAngle(angle) {
      const cosH = (-Math.sin(angle * D2R) - Math.sin(lat * D2R) * Math.sin(decl * D2R)) / (Math.cos(lat * D2R) * Math.cos(decl * D2R));
      if (cosH > 1 || cosH < -1) return 0;
      return Math.acos(cosH) * R2D / 15;
    }
    const fajr = dhuhr - hourAngle(17.7);
    const sunrise = dhuhr - hourAngle(0.833);
    const sunset = dhuhr + hourAngle(0.833);
    const maghrib = dhuhr + hourAngle(4.5);
    let midnight = (sunset + (fajr + 24)) / 2;
    if (midnight >= 24) midnight -= 24;
    return { fajr, sunrise, dhuhr, sunset, maghrib, midnight };
  }

  function fmtPrayerHr(h) {
    const norm = ((h % 24) + 24) % 24;
    const hh = Math.floor(norm);
    const mm = Math.round((norm - hh) * 60);
    const fh = (hh + (mm === 60 ? 1 : 0)) % 24;
    const fm = mm === 60 ? 0 : mm;
    return fa(String(fh).padStart(2, "0") + ":" + String(fm).padStart(2, "0"));
  }

  window.renderPrayerTimes = function() {
    const card = document.getElementById("prayerTimesCard");
    const grid = document.getElementById("prayerTimesGrid");
    const btn = document.getElementById("prayerCityBtn");
    if (!card || !grid) return;
    if (!cfg || !cfg.showPrayer) { card.style.display = "none"; return; }
    card.style.display = "flex";
    const srcCity = cfg.prayerCity || cfg.city || "35.6892,51.3890,تهران";
    const parts = String(srcCity).split(",");
    const lat = Number(parts[0]) || 35.6892, lon = Number(parts[1]) || 51.3890, cname = parts[2] || "تهران";
    const dispName = (typeof trCity === "function") ? trCity(cname) : cname;
    if (btn) btn.textContent = "📍 " + dispName + " ▾";
    const now = new Date();
    const nowHr = now.getHours() + now.getMinutes() / 60;
    const pt = computePrayerTimes(lat, lon, now);
    const items = [
      { k: "fajr", n: tr("اذان صبح", "بانگی بەیانی", "Fajr"), v: pt.fajr },
      { k: "sunrise", n: tr("طلوع آفتاب", "هەڵهاتنی خۆر", "Sunrise"), v: pt.sunrise },
      { k: "dhuhr", n: tr("اذان ظهر", "بانگی نیوەڕۆ", "Dhuhr"), v: pt.dhuhr },
      { k: "sunset", n: tr("غروب آفتاب", "خۆرئاوابوون", "Sunset"), v: pt.sunset },
      { k: "maghrib", n: tr("اذان مغرب", "بانگی مەغریب", "Maghrib"), v: pt.maghrib },
      { k: "midnight", n: tr("نیمه‌شب شرعی", "نیوەشەو", "Midnight"), v: pt.midnight }
    ];
    let nextKey = items.find(x => x.v > nowHr)?.k || "fajr";
    grid.innerHTML = items.map(x => `<div class="prayer-pill ${x.k === nextKey ? "next" : ""}"><span class="pn">${x.n}</span><span class="pt">${fmtPrayerHr(x.v)}</span></div>`).join("");
  };

  // --- 9. FULL JSON BACKUP & RESTORE (Native Android SAF + PWA Browser) ---
  function applyFullJsonBackupText(rawText) {
    try {
      const parsed = JSON.parse(String(rawText || "{}"));
      const d = parsed.data || parsed;
      if (!d || typeof d !== "object") throw new Error("invalid");
      let restoredAny = false;
      const restoreMap = [
        ["cal_cfg_v4", "cal_cfg_v4"],
        ["cal_notes_v4", "cal_notes_v4"],
        ["cal_birth_v1", "cal_birth_v1"],
        ["cal_cities_v9", "cal_cities_v9"],
        ["cal_user_ev_v11", "cal_user_ev_v11"],
        ["cal_status_notif_v1", "cal_status_notif_v1"],
        ["smartcal_ai_notes_cfg_v1", "smartcal_ai_notes_cfg_v1"],
        ["smartcal_portfolio_v74", PF_KEY],
        ["smartcal_price_alerts_v74", PA_KEY],
        ["smartcal_recurring_v74", REC_KEY],
        ["smartcal_mkt_prefs_v72", "smartcal_mkt_prefs_v72"]
      ];
      restoreMap.forEach(([srcKey, dstKey]) => {
        if (d[srcKey] !== undefined && d[srcKey] !== null) {
          localStorage.setItem(dstKey, typeof d[srcKey] === "string" ? d[srcKey] : JSON.stringify(d[srcKey]));
          restoredAny = true;
        }
      });
      if (!restoredAny) throw new Error("empty");
      triggerHaptic("medium");
      if (typeof showToast === "function") showToast(tr("بازیابی کامل انجام شد! در حال بارگذاری مجدد...", "گەڕاندنەوە سەرکەوتوو بوو!", "Restore complete! Reloading..."));
      setTimeout(() => location.reload(), 700);
    } catch(e) {
      triggerHaptic("heavy");
      if (typeof showToast === "function") showToast(tr("فایل بکاپ معتبر نیست ❌", "فایلەکە دروست نییە ❌", "Invalid backup file ❌"));
    }
  }

  window.onNativeFileSaved = function(ok) {
    if (ok) {
      triggerHaptic("medium");
      if (typeof showToast === "function") showToast(tr("فایل بکاپ کامل با موفقیت ذخیره شد ✅", "فایلی پاڵپشت بە سەرکەوتوویی پاشەکەوت کرا ✅", "Full JSON backup saved ✅"));
    } else {
      if (typeof showToast === "function") showToast(tr("ذخیره فایل بکاپ لغو شد", "پاشەکەوتکردن هەڵوەشایەوە", "Backup save cancelled"));
    }
  };

  window.onNativeJsonBackupPicked = function(rawText) {
    if (rawText && String(rawText).trim().length > 2) {
      applyFullJsonBackupText(rawText);
    }
  };

  window.downloadFullJsonBackup = function() {
    const payload = {
      app: "SmartCal",
      version: "75",
      exportedAt: new Date().toISOString(),
      data: {
        cal_cfg_v4: safeJsonParse("cal_cfg_v4", cfg),
        cal_notes_v4: safeJsonParse("cal_notes_v4", typeof notes !== "undefined" ? notes : {}),
        cal_birth_v1: safeJsonParse("cal_birth_v1", typeof birthCfg !== "undefined" ? birthCfg : null),
        cal_cities_v9: safeJsonParse("cal_cities_v9", typeof customCities !== "undefined" ? customCities : []),
        cal_user_ev_v11: safeJsonParse("cal_user_ev_v11", typeof customUserEv !== "undefined" ? customUserEv : {}),
        cal_status_notif_v1: localStorage.getItem("cal_status_notif_v1"),
        smartcal_ai_notes_cfg_v1: safeJsonParse("smartcal_ai_notes_cfg_v1", null),
        smartcal_portfolio_v74: safeJsonParse(PF_KEY, []),
        smartcal_price_alerts_v74: safeJsonParse(PA_KEY, []),
        smartcal_recurring_v74: safeJsonParse(REC_KEY, []),
        smartcal_mkt_prefs_v72: safeJsonParse("smartcal_mkt_prefs_v72", null)
      }
    };
    const jsonStr = JSON.stringify(payload, null, 2);
    const filename = "SmartCal-Backup-" + new Date().toISOString().slice(0, 10) + ".json";
    triggerHaptic("medium");
    if (window.AndroidBridge && typeof window.AndroidBridge.saveTextFileNative === "function") {
      try {
        if (window.__suppressAutoRelock) window.__suppressAutoRelock(60000);
        window.AndroidBridge.saveTextFileNative(filename, "application/json", jsonStr);
        return;
      } catch(e) {}
    }
    const blob = new Blob([jsonStr], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { a.remove(); URL.revokeObjectURL(url); }, 300);
    if (typeof showToast === "function") showToast(tr("فایل بکاپ کامل ذخیره شد ✅", "فایلی پاڵپشت پاشەکەوت کرا ✅", "Full JSON backup downloaded ✅"));
  };

  window.triggerFullJsonRestore = function() {
    triggerHaptic("light");
    if (window.AndroidBridge && typeof window.AndroidBridge.pickTextFileNative === "function") {
      try {
        if (window.__suppressAutoRelock) window.__suppressAutoRelock(60000);
        window.AndroidBridge.pickTextFileNative("application/json");
        return;
      } catch(e) {}
    }
    const inp = document.getElementById("fullJsonRestoreInp");
    if (inp) inp.click();
  };

  window.restoreFullJsonBackup = function(input) {
    const file = input?.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = function() { applyFullJsonBackupText(reader.result); };
    reader.readAsText(file);
    input.value = "";
  };

  // --- 10. PRIVACY LOCK (Themed 4-Digit PIN + Native Android BiometricPrompt) ---
  let pinBuf = "";
  let pinMode = "unlock"; // "unlock" | "set" | "disable"
  function hashPin(p) {
    let h = 2166136261;
    const s = "SC74_" + String(p);
    for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
    return (h >>> 0).toString(16);
  }
  function hasNativeBiometric() {
    return !!(window.AndroidBridge && typeof window.AndroidBridge.requestDeviceAuth === "function");
  }
  function updateLockSettingsUI() {
    const hasPin = !!localStorage.getItem(LOCK_KEY);
    const st = document.getElementById("privacyLockStatusTxt");
    const btn = document.getElementById("privacyLockToggleBtn");
    if (st) st.textContent = hasPin ? tr("فعال • قفل هنگام ورود به برنامه", "چالاکە • قوفڵی چوونەژوورەوە", "Enabled • Locked on launch") : tr("غیرفعال • محافظت از یادداشت‌ها و سبد دارایی", "ناچالاکە", "Disabled");
    if (btn) btn.textContent = hasPin ? tr("حذف قفل", "لابردنی قوفڵ", "Disable") : tr("تنظیم قفل", "دانانی قوفڵ", "Set PIN");
  }
  function openPinOverlay(mode) {
    pinMode = mode;
    pinBuf = "";
    updatePinDots();
    const ov = document.getElementById("privacyLockOverlay");
    const tit = document.getElementById("lockOverlayTitle");
    const sub = document.getElementById("lockOverlaySub");
    const cancelBtn = document.getElementById("pinCancelBtn");
    const bioBtn = document.getElementById("pinBioBtn");
    if (sub) sub.style.color = "var(--sec)";
    if (mode === "set") {
      if (tit) tit.textContent = tr("تنظیم رمز ۴ رقمی", "دانانی پینی ٤ ژمارەیی", "Set 4-Digit PIN");
      if (sub) sub.textContent = tr("یک رمز ۴ رقمی برای قفل برنامه انتخاب کنید", "پینێکی ٤ ژمارەیی هەڵبژێرە", "Choose a 4-digit PIN to lock Smart Cal");
      if (cancelBtn) { cancelBtn.style.display = "inline-block"; cancelBtn.textContent = tr("انصراف", "پاشگەزبوونەوە", "Cancel"); }
      if (bioBtn) bioBtn.style.visibility = "hidden";
    } else if (mode === "disable") {
      if (tit) tit.textContent = tr("حذف قفل حریم خصوصی", "لابردنی قوفڵی تایبەتمەندی", "Disable Privacy Lock");
      if (sub) sub.textContent = tr("رمز ۴ رقمی فعلی یا اثر انگشت را وارد کنید", "پینی ئێستا یان پەنجەمۆر بەکاربهێنە", "Enter current PIN or use fingerprint");
      if (cancelBtn) { cancelBtn.style.display = "inline-block"; cancelBtn.textContent = tr("انصراف", "پاشگەزبوونەوە", "Cancel"); }
      if (bioBtn) { bioBtn.style.visibility = "visible"; bioBtn.style.opacity = hasNativeBiometric() ? "1" : "0.3"; }
    } else {
      if (tit) tit.textContent = tr("Smart Cal قفل است", "Smart Cal قوفڵ کراوە", "Smart Cal is Locked");
      if (sub) sub.textContent = tr("رمز ۴ رقمی یا اثر انگشت خود را وارد کنید", "پین یان پەنجەمۆرەکەت بەکاربهێنە", "Enter your 4-digit PIN or use fingerprint");
      if (cancelBtn) cancelBtn.style.display = "none";
      if (bioBtn) { bioBtn.style.visibility = "visible"; bioBtn.style.opacity = hasNativeBiometric() ? "1" : "0.3"; }
    }
    if (ov) ov.classList.add("show");
  }
  window.closePinOverlay = function() {
    if (pinMode === "unlock") return;
    pinBuf = "";
    updatePinDots();
    document.getElementById("privacyLockOverlay")?.classList.remove("show");
  };
  window.configurePrivacyLock = function() {
    const existing = localStorage.getItem(LOCK_KEY);
    openPinOverlay(existing ? "disable" : "set");
  };

  function updatePinDots() {
    document.querySelectorAll("#lockPinDots .pin-dot").forEach((d, i) => d.classList.toggle("filled", i < pinBuf.length));
  }
  function completePinActionSuccess() {
    pinBuf = "";
    updatePinDots();
    document.getElementById("privacyLockOverlay")?.classList.remove("show");
    triggerHaptic("medium");
    if (pinMode === "disable") {
      localStorage.removeItem(LOCK_KEY);
      updateLockSettingsUI();
      if (typeof showToast === "function") showToast(tr("قفل حریم خصوصی غیرفعال شد 🔓", "قوفڵ ناچالاک کرا 🔓", "Privacy lock disabled 🔓"));
    }
  }
  window.pinPadPress = function(digit) {
    triggerHaptic("light");
    if (pinBuf.length >= 4) return;
    pinBuf += String(digit);
    updatePinDots();
    if (pinBuf.length === 4) {
      if (pinMode === "set") {
        localStorage.setItem(LOCK_KEY, hashPin(pinBuf));
        pinBuf = "";
        updatePinDots();
        document.getElementById("privacyLockOverlay")?.classList.remove("show");
        updateLockSettingsUI();
        triggerHaptic("medium");
        if (typeof showToast === "function") showToast(tr("قفل ۴ رقمی و اثر انگشت فعال شد 🔐", "قوفڵ چالاک کرا 🔐", "Privacy lock enabled 🔐"));
        return;
      }
      const saved = localStorage.getItem(LOCK_KEY);
      if (hashPin(pinBuf) === saved) {
        completePinActionSuccess();
      } else {
        pinBuf = "";
        updatePinDots();
        triggerHaptic("heavy");
        const sub = document.getElementById("lockOverlaySub");
        if (sub) { sub.textContent = tr("رمز اشتباه است! دوباره تلاش کنید", "پین هەڵەیە!", "Wrong PIN! Try again"); sub.style.color = "var(--hol)"; }
      }
    }
  };
  window.pinPadBackspace = function() { pinBuf = pinBuf.slice(0, -1); updatePinDots(); triggerHaptic("light"); };
  let _bgHiddenAt = 0;
  let _suppressRelockUntil = 0;
  window.__suppressAutoRelock = function(ms = 15000) {
    _suppressRelockUntil = Date.now() + ms;
  };
  document.addEventListener("visibilitychange", function() {
    if (document.visibilityState === "hidden") {
      _bgHiddenAt = Date.now();
    } else if (document.visibilityState === "visible") {
      if (!localStorage.getItem(LOCK_KEY)) return;
      if (Date.now() < _suppressRelockUntil) return;
      if (_bgHiddenAt && (Date.now() - _bgHiddenAt >= 30000)) {
        const ov = document.getElementById("privacyLockOverlay");
        if (ov && !ov.classList.contains("show")) {
          openPinOverlay("unlock");
          if (hasNativeBiometric()) {
            setTimeout(() => { try { window.__suppressAutoRelock(8000); window.AndroidBridge.requestDeviceAuth(); } catch(e) {} }, 300);
          }
        }
      }
    }
  });

  window.triggerNativeBiometricUnlock = function() {
    if (hasNativeBiometric()) {
      window.__suppressAutoRelock(15000);
      try { window.AndroidBridge.requestDeviceAuth(); } catch(e) {}
    } else {
      if (typeof showToast === "function") showToast(tr("ورود با اثر انگشت مخصوص نسخه اندروید است", "تایبەتە بە ئەندرۆید", "Biometric unlock is available in Android app"));
    }
  };
  window.onNativeDeviceAuthResult = function(success) {
    if (success) {
      completePinActionSuccess();
    }
  };

  // --- 11. MARKET & TODAY TASKS WIDGET SYNC (Android) ---
  function syncMarketAndTasksWidget() {
    if (!window.AndroidBridge || typeof window.AndroidBridge.updateMarketWidgetData !== "function") return;
    try {
      const assets = getAllLiveAssets();
      const map = Object.fromEntries(assets.map(a => [a.ico, a]));
      const pick = ["g18", "emami", "usd", "eur"].map(k => map[k]).filter(Boolean).slice(0, 4);
      const rates = pick.map(x => ({ n: x.n.replace(/\(.*?\)/g, "").trim(), p: fmtToman(x.p), c: x.c || "0%" }));
      const todKey = (typeof tod !== "undefined") ? `${tod.jy}-${tod.jm}-${tod.jd}` : "";
      const rawTodayNotes = (typeof notes === "object" && notes && Array.isArray(notes[todKey])) ? notes[todKey] : [];
      const openTodayNotes = rawTodayNotes
        .map(raw => (typeof normNote === "function") ? normNote(raw) : raw)
        .filter(n => n && !n.done && String(n.t || n.text || n.title || "").trim());
      const recDue = safeJsonParse(REC_KEY, []).filter(r => Math.ceil((getNextOccurrenceDate(r).getTime() - Date.now()) / 86400000) <= 1);
      const taskCount = openTodayNotes.length + recDue.length;
      const firstNoteTxt = openTodayNotes[0] ? String(openTodayNotes[0].t || openTodayNotes[0].text || openTodayNotes[0].title || "").trim() : "";
      const firstTask = firstNoteTxt || String(recDue[0]?.title || "").trim();
      window.AndroidBridge.updateMarketWidgetData(JSON.stringify({ rates, taskCount, firstTask }));
    } catch(e) {}
  }

  // --- HOOK INTO EXISTING RENDER LIFECYCLE ---
  const _origRenderMarket = window.renderMarket;
  if (typeof _origRenderMarket === "function") {
    window.renderMarket = function(d, isCached) {
      const out = _origRenderMarket.apply(this, arguments);
      try {
        populateAssetSelects();
        renderPortfolio();
        renderPriceAlerts();
        evaluatePriceAlerts();
        calcMarketConverter();
        syncMarketAndTasksWidget();
      } catch(e) {}
      return out;
    };
  }


  // --- 12. FULL TRILINGUAL LOCALIZATION FOR v74 PRO SUITE (FA / KU / EN) ---
  window.applyProSuiteLanguageUI = function() {
    const s = (id, faTxt, kuTxt, enTxt) => {
      const el = document.getElementById(id);
      if (el) el.textContent = tr(faTxt, kuTxt, enTxt);
    };
    const p = (id, faTxt, kuTxt, enTxt) => {
      const el = document.getElementById(id);
      if (el) el.placeholder = tr(faTxt, kuTxt, enTxt);
    };
    // Top bar Search
    s("openGlobalSearchTxt", "جستجو", "گەڕان", "Search");
    const gsBtn = document.getElementById("openGlobalSearch");
    if (gsBtn) gsBtn.title = tr("جستجوی سراسری در یادداشت‌ها، اقساط و تعطیلات", "گەڕانی گشتی لە تێبینی، قست و پشووەکاندا", "Global search across notes, reminders & holidays");

    // Prayer Times card
    s("prayerCardTitle", "🕌 اوقات شرعی امروز", "🕌 کاتەکانی بانگی ئەمڕۆ", "🕌 Today's Prayer Times");

    // Market ProSuite
    s("mktProSuiteTitle", "💼 ابزارهای حرفه‌ای دارایی", "💼 ئامرازە پرۆفیشناڵەکانی دارایی", "💼 Pro Asset Tools");
    const tabs = document.querySelectorAll("#mktProTabs .fchip");
    if (tabs.length >= 3) {
      tabs[0].textContent = tr("💼 سبد دارایی", "💼 سەبەتەی دارایی", "💼 Portfolio");
      tabs[1].textContent = tr("🔔 هشدار قیمت", "🔔 ئاگادارکردنەوەی نرخ", "🔔 Alerts");
      tabs[2].textContent = tr("⇄ مبدل نرخ", "⇄ گۆڕینەوەی نرخ", "⇄ Converter");
    }
    s("pfTotalLbl", "ارزش کل سبد دارایی شما:", "کۆی گشتی سەبەتەی داراییت:", "Total Portfolio Value:");
    p("pfQtyInp", "مقدار / تعداد", "بڕ / ژمارە", "Qty / Amount");
    s("pfAddBtn", "➕ افزودن", "➕ زیادکردن", "➕ Add");
    s("paHelpTxt", "برای هر دارایی سقف یا کف قیمتی (تومان) تعیین کنید تا هنگام عبور نرخ، اعلان دریافت کنید.", "بۆ هەر داراییەک بەرزترین یان نزمترین نرخ (تمەن) دیاری بکە بۆ وەرگرتنی ئاگادارکردنەوە.", "Set an upper or lower target (Toman) to get alerted when the live rate crosses it.");
    s("paOptAbove", "📈 بالای", "📈 سەروو", "📈 Above");
    s("paOptBelow", "📉 زیر", "📉 خواروو", "📉 Below");
    p("paTargetInp", "قیمت هدف (تومان)", "نرخی ئامانج (تمەن)", "Target price (Toman)");
    s("paAddBtn", "🔔 ثبت", "🔔 تۆمار", "🔔 Set");

    // Recurring Reminders
    s("recBlockTitle", "🔁 اقساط، چک‌ها و یادآورهای تکرارشونده", "🔁 قست، چەک و یادخەرەوە دووبارەبووەکان", "🔁 Recurring Reminders & Bills");
    s("recBlockSub", "تکرار ماهانه، هفتگی یا سالانه با هشدار روز قبل و همان روز", "دووبارەبوونەوەی مانگانە، هەفتانە یان ساڵانە بە ئاگادارکردنەوە", "Monthly, weekly or yearly schedule with 1-day-before & same-day alerts");
    s("recAddToggleBtn", "➕ افزودن قسط/تکرار", "➕ زیادکردن", "➕ Add Bill/Repeat");
    p("recTitleInp", "عنوان (مثلاً: قسط وام مسکن، اجاره، تمدید بیمه...)", "ناونیشان (بۆ نموونە: قست، کرێ، بیمە...)", "Title (e.g. Rent, Loan installment, Insurance...)");
    s("recOptMonthly", "ماهانه", "مانگانە", "Monthly");
    s("recOptWeekly", "هفتگی", "هەفتانە", "Weekly");
    s("recOptYearly", "سالانه", "ساڵانە", "Yearly");
    p("recDayInp", "روز ماه (۱ تا ۳۱)", "ڕۆژی مانگ (١ تا ٣١)", "Day (1-31)");
    s("recNotifyPrevLbl", "هشدار ۱ روز قبل", "ئاگاداری ١ ڕۆژ پێشتر", "Alert 1 day before");
    s("recNotifySameLbl", "هشدار همان روز", "ئاگاداری هەمان ڕۆژ", "Alert same day");
    s("recSaveBtn", "💾 ذخیره", "💾 پاشەکەوت", "💾 Save");

    // Settings Backup & Extra Toggles
    s("btnFullJsonBackup", "💾 دانلود بکاپ کامل (.json)", "💾 داگرتنی پاڵپشتی تەواو (.json)", "💾 Full JSON Backup");
    s("btnFullJsonRestore", "📂 بازیابی فایل (.json)", "📂 گەڕاندنەوەی فایل (.json)", "📂 Restore (.json)");
    s("lblShowPrayerTitle", "🕌 نمایش اوقات شرعی در صفحه تقویم", "🕌 پیشاندانی کاتەکانی بانگ لە ڕۆژژمێردا", "🕌 Show Prayer Times on Calendar");
    s("lblShowPrayerSub", "محاسبه دقیق آفلاین بر اساس شهر انتخابی شما", "هەژمارکردنی وردی ئۆفلاین بەپێی شارەکەت", "Offline astronomical calculation for your selected city");
    s("lblHapticTitle", "📳 بازخورد لمسی ملایم (Haptic)", "📳 لەرزینی نەرمی دەستلێدان (Haptic)", "📳 Subtle Haptic Feedback");
    s("lblHapticSub", "ویبره بسیار ظریف هنگام تغییر تب و انتخاب روزها", "لەرزینی زۆر نەرم لە کاتی گۆڕینی تاب و هەڵبژاردنی ڕۆژەکان", "Crisp light vibration on tab switches & day selection");
    s("lblPrivacyLockTitle", "🔐 قفل حریم خصوصی (پین / اثر انگشت)", "🔐 قوفڵی تایبەتمەندی (پین / پەنجەمۆر)", "🔐 Privacy Lock (PIN / Biometric)");

    // Global Search & Lock Overlay
    s("gsModalTit", "🔍 جستجوی سراسری هوشمند", "🔍 گەڕانی گشتی زیرەک", "🔍 Smart Global Search");
    p("globalSearchInp", "جستجو در یادداشت‌ها، اقساط، تعطیلات و مناسبت‌ها...", "گەڕان لە تێبینی، قست، پشوو و بۆنەکاندا...", "Search notes, bills, holidays & events...");
    s("lockOverlayTitle", "Smart Cal قفل است", "Smart Cal قوفڵ کراوە", "Smart Cal is Locked");
    s("lockOverlaySub", "رمز ۴ رقمی خود را وارد کنید", "پینە ٤ ژمارەییەکەت بنووسە", "Enter your 4-digit PIN");

    updateLockSettingsUI();
    renderPortfolio();
    renderPriceAlerts();
    const paInp = document.getElementById("paTargetInp");
    if (paInp && paInp.value) onPaTargetInput(paInp);
    renderRecurringList();
    renderPrayerTimes();
    calcMarketConverter();
  };

  const _origApplyCfg = window.applyCfg;
  if (typeof _origApplyCfg === "function") {
    window.applyCfg = function() {
      const out = _origApplyCfg.apply(this, arguments);
      try {
        const sp = document.getElementById("setShowPrayer");
        if (sp) sp.checked = !!cfg.showPrayer;
        const sh = document.getElementById("setHaptic");
        if (sh) sh.checked = cfg.haptic !== false;
        applyProSuiteLanguageUI();
      } catch(e) {}
      return out;
    };
  }

  // Initial bootstrap
  setTimeout(function() {
    try {
      populateAssetSelects();
      renderPortfolio();
      renderPriceAlerts();
      renderRecurringList();
      applyProSuiteLanguageUI();
      const sp = document.getElementById("setShowPrayer"); if (sp) sp.checked = !!cfg.showPrayer;
      const sh = document.getElementById("setHaptic"); if (sh) sh.checked = cfg.haptic !== false;
      if (localStorage.getItem(LOCK_KEY)) {
        openPinOverlay("unlock");
        if (hasNativeBiometric()) {
          setTimeout(() => { try { window.AndroidBridge.requestDeviceAuth(); } catch(e) {} }, 450);
        }
      }
      safeJsonParse(REC_KEY, []).forEach(scheduleRecurringNativeAlarm);
      syncMarketAndTasksWidget();
    } catch(e) {}
  }, 350);
})();
