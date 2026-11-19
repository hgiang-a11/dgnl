/* Tải Video – mã giao diện */
(() => {
  "use strict";

  const $ = (sel) => document.querySelector(sel);
  const els = {
    form: $("#form"), field: $("#field"), url: $("#url"), quality: $("#quality"),
    submit: $("#submitBtn"), paste: $("#pasteBtn"), clear: $("#clearBtn"), detected: $("#detected"),
    hint: $("#formHint"), result: $("#result"), stage: $("#stage"), stageBg: $("#stageBg"),
    ring: $("#ring"), ringFill: $("#ringFill"), ringText: $("#ringText"),
    stageMsg: $("#stageMsg"), stageSub: $("#stageSub"), player: $("#player"),
    badge: $("#platformBadge"), meta: $("#metaText"), title: $("#title"), uploader: $("#uploader"),
    bar: $("#bar"), barFill: $("#barFill"),
    errorBox: $("#errorBox"), errorText: $("#errorText"), errorDetails: $("#errorDetails"), errorDetail: $("#errorDetail"),
    download: $("#downloadBtn"), fileText: $("#fileText"),
    retry: $("#retryBtn"), copy: $("#copyBtn"), again: $("#newBtn"), cancel: $("#cancelBtn"),
    theme: $("#themeBtn"), toast: $("#toast"), engine: $("#engine"),
    share: $("#shareBtn"), downloadLabel: $("#downloadLabel"),
  };

  // Khi chạy trong ứng dụng Android, phần mã gốc (Kotlin) cung cấp window.TaiVideoApp.
  const NATIVE = window.TaiVideoApp || null;
  const IS_APP = !!NATIVE;
  if (IS_APP) document.documentElement.classList.add("is-app");

  const RING_LEN = 2 * Math.PI * 52;
  const QUALITY_KEY = "quality";

  /* ---------- Nhận diện nền tảng từ link ---------- */
  const PLATFORMS = [
    { re: /(^|\.)(youtube\.com|youtu\.be|youtube-nocookie\.com)$/, name: "YouTube", icon: "p-youtube" },
    { re: /(^|\.)(facebook\.com|fb\.watch|fb\.com)$/, name: "Facebook", icon: "p-facebook" },
    { re: /(^|\.)instagram\.com$/, name: "Instagram", icon: "p-instagram" },
    { re: /(^|\.)tiktok\.com$/, name: "TikTok", icon: "p-tiktok" },
    { re: /(^|\.)(x\.com|twitter\.com)$/, name: "X (Twitter)", icon: "p-x" },
    { re: /(^|\.)vimeo\.com$/, name: "Vimeo", icon: "p-vimeo" },
    { re: /(^|\.)(reddit\.com|redd\.it)$/, name: "Reddit", icon: "p-reddit" },
    { re: /(^|\.)twitch\.tv$/, name: "Twitch", icon: "p-twitch" },
    { re: /(^|\.)soundcloud\.com$/, name: "SoundCloud", icon: "p-soundcloud" },
    { re: /(^|\.)(dailymotion\.com|dai\.ly)$/, name: "Dailymotion", icon: "p-dailymotion" },
  ];

  function extractUrl(text) {
    text = (text || "").trim();
    const m = text.match(/https?:\/\/\S+/i);
    return m ? m[0] : text;
  }

  function hostOf(text) {
    let u = extractUrl(text);
    if (!u) return "";
    if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(u)) u = "https://" + u;
    try { return new URL(u).hostname.toLowerCase(); } catch { return ""; }
  }

  function detectPlatform(text) {
    const host = hostOf(text);
    if (!host) return null;
    const p = PLATFORMS.find((x) => x.re.test(host));
    return p || (host.includes(".") ? { name: host.replace(/^www\./, ""), icon: "i-globe", generic: true } : null);
  }

  let lastIcon = "";
  function updateDetected() {
    const value = els.url.value;
    els.clear.hidden = !value;
    const p = detectPlatform(value);
    const icon = p ? p.icon : "i-link";
    if (icon !== lastIcon) {
      els.detected.innerHTML = `<svg><use href="#${icon}"/></svg>`;
      els.detected.title = p ? p.name : "Nền tảng";
      if (p && !p.generic) {
        els.detected.classList.remove("pop");
        void els.detected.offsetWidth;
        els.detected.classList.add("pop");
      }
      lastIcon = icon;
    }
    if (els.hint.classList.contains("error")) setHint();
  }

  function setHint(text, isError) {
    els.hint.classList.toggle("error", !!isError);
    els.hint.innerHTML = text || 'Mẹo: nhấn <kbd>Ctrl</kbd> + <kbd>V</kbd> để dán, rồi <kbd>Enter</kbd> để tải.';
    if (isError) els.hint.style.display = "block"; else els.hint.style.display = "";
  }

  /* ---------- Định dạng số ---------- */
  function fmtBytes(n) {
    if (!n && n !== 0) return "";
    const u = ["B", "KB", "MB", "GB", "TB"];
    let i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return `${n.toFixed(n < 10 && i > 0 ? 1 : 0)} ${u[i]}`;
  }
  function fmtDuration(s) {
    if (!s && s !== 0) return "";
    s = Math.round(s);
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    const pad = (x) => String(x).padStart(2, "0");
    return h ? `${h}:${pad(m)}:${pad(sec)}` : `${m}:${pad(sec)}`;
  }
  function fmtEta(s) {
    if (s == null || !isFinite(s)) return "";
    s = Math.max(0, Math.round(s));
    if (s < 60) return `còn ${s} giây`;
    const m = Math.floor(s / 60);
    if (m < 60) return `còn ${m} phút ${s % 60 ? (s % 60) + " giây" : ""}`.trim();
    return `còn ${Math.floor(m / 60)} giờ ${m % 60} phút`;
  }
  function fmtCount(n) {
    if (n == null) return "";
    if (n >= 1e9) return (n / 1e9).toFixed(1).replace(".0", "") + " tỷ";
    if (n >= 1e6) return (n / 1e6).toFixed(1).replace(".0", "") + " triệu";
    if (n >= 1e3) return (n / 1e3).toFixed(1).replace(".0", "") + " nghìn";
    return String(n);
  }
  function qualityLabel(file) {
    if (!file) return "";
    if (file.is_audio) return "Âm thanh";
    const h = Math.min(file.width || Infinity, file.height || Infinity);
    if (!isFinite(h)) return "";
    if (h >= 2160) return "4K";
    if (h >= 1440) return "2K";
    return `${h}p`;
  }
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  /* ---------- Thông báo nhỏ ---------- */
  let toastTimer;
  function toast(msg) {
    els.toast.textContent = msg;
    els.toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => els.toast.classList.remove("show"), 2600);
  }

  /* ---------- Gọi máy chủ ---------- */
  async function api(path, opts = {}) {
    let res;
    try {
      res = await fetch(path, { headers: { "Content-Type": "application/json" }, ...opts });
    } catch {
      throw new Error(IS_APP
        ? "Ứng dụng vừa bị hệ thống tạm dừng. Hãy đóng hẳn ứng dụng rồi mở lại."
        : "Không kết nối được tới ứng dụng. Hãy chắc chắn cửa sổ chạy run.bat / run.sh vẫn đang mở.");
    }
    let data = null;
    try { data = await res.json(); } catch { /* bỏ qua */ }
    if (!res.ok) {
      const d = data && data.detail;
      throw new Error(typeof d === "string" ? d : "Máy chủ gặp lỗi, hãy thử lại.");
    }
    return data;
  }

  /* ---------- Trạng thái ---------- */
  let job = null;        // lượt tải hiện tại
  let pollTimer = null;
  let pollFails = 0;
  let renderedInfoFor = null;
  let renderedDoneFor = null;

  function stopPolling() { clearTimeout(pollTimer); pollTimer = null; }

  function setBusy(busy) {
    els.submit.disabled = busy;
    els.submit.classList.toggle("loading", busy);
    els.submit.querySelector(".btn-label").textContent = busy ? "Đang xử lý" : "Tải";
  }

  function resetResult() {
    renderedInfoFor = null;
    renderedDoneFor = null;
    els.stage.className = "stage";
    els.stageBg.classList.remove("on");
    els.stageBg.style.backgroundImage = "";
    els.stage.querySelectorAll(".stage-thumb").forEach((n) => n.remove());
    els.player.innerHTML = "";
    els.ring.hidden = false;
    setRing(null);
    els.stageMsg.textContent = "Đang phân tích link…";
    els.stageSub.textContent = "";
    els.badge.innerHTML = `<svg><use href="#i-globe"/></svg><span>Đang nhận diện…</span>`;
    els.meta.textContent = "";
    els.title.innerHTML = '<span class="skeleton" style="width:72%"></span>';
    els.uploader.innerHTML = '<span class="skeleton" style="width:34%"></span>';
    els.bar.hidden = false;
    els.bar.classList.add("indeterminate");
    els.barFill.style.width = "0";
    els.errorBox.hidden = true;
    els.download.hidden = true;
    els.retry.hidden = true;
    els.copy.hidden = true;
    els.share.hidden = true;
    els.again.hidden = true;
    els.cancel.hidden = false;
  }

  function setRing(pct) {
    if (pct == null) {
      els.ring.classList.add("indeterminate");
      els.ringText.textContent = "";
      els.ringFill.style.strokeDashoffset = "";
      return;
    }
    els.ring.classList.remove("indeterminate");
    els.ringFill.style.strokeDasharray = RING_LEN;
    els.ringFill.style.strokeDashoffset = RING_LEN * (1 - Math.min(pct, 100) / 100);
    els.ringText.textContent = `${Math.floor(pct)}%`;
  }

  /* ---------- Bắt đầu tải ---------- */
  async function start(e) {
    if (e) e.preventDefault();
    const url = extractUrl(els.url.value);
    if (!url) {
      els.form.classList.remove("shake"); void els.form.offsetWidth; els.form.classList.add("shake");
      setHint("Hãy dán link video vào ô trên trước nhé.", true);
      els.url.focus();
      return;
    }
    if (!hostOf(url).includes(".")) {
      setHint("Link chưa đúng. Hãy sao chép lại link từ nút Chia sẻ của video.", true);
      return;
    }
    els.url.value = url;
    updateDetected();
    setHint();

    // hủy lượt cũ nếu đang chạy
    if (job && !["done", "error", "cancelled"].includes(job.state)) {
      api(`/api/jobs/${job.id}`, { method: "DELETE" }).catch(() => {});
    }
    stopPolling();
    resetResult();
    els.result.hidden = false;
    setBusy(true);
    if (IS_APP) { try { NATIVE.onDownloadStarted(); } catch {} }
    requestAnimationFrame(() => els.result.scrollIntoView({ behavior: "smooth", block: "center" }));

    try {
      job = await api("/api/jobs", { method: "POST", body: JSON.stringify({ url, quality: els.quality.value }) });
      pollFails = 0;
      render(job);
      poll();
    } catch (err) {
      job = { state: "error", error: err.message, url };
      render(job);
    }
  }

  async function poll() {
    if (!job || !job.id) return;
    const id = job.id;
    try {
      const data = await api(`/api/jobs/${id}`);
      if (!job || job.id !== id) return;
      pollFails = 0;
      job = data;
      render(job);
    } catch (err) {
      if (!job || job.id !== id) return;
      if (++pollFails >= 5) {
        job = { ...job, state: "error", error: err.message };
        render(job);
      }
    }
    if (job && job.id === id && !["done", "error", "cancelled"].includes(job.state)) {
      pollTimer = setTimeout(poll, 600);
    }
  }

  /* ---------- Vẽ giao diện theo trạng thái ---------- */
  function render(j) {
    if (j.info && renderedInfoFor !== j.id) renderInfo(j);

    const pct = j.progress || 0;
    switch (j.state) {
      case "queued": {
        const msg = j.message || "Đang chờ đến lượt…";
        els.stageMsg.textContent = msg;
        els.stageSub.textContent = /khởi động/i.test(msg) ? "Chỉ chậm ở lần mở đầu tiên" : "Đang có video khác được xử lý";
        setRing(null);
        break;
      }
      case "analyzing":
        els.stageMsg.textContent = "Đang phân tích link…";
        els.stageSub.textContent = "Tìm video và chất lượng tốt nhất";
        setRing(null);
        break;
      case "downloading": {
        const isAudio = j.quality === "audio";
        els.stageMsg.textContent = isAudio ? "Đang tạo tệp âm thanh…" : "Đang tạo video…";
        const parts = [];
        if (j.total) parts.push(`${fmtBytes(j.downloaded)} / ${fmtBytes(j.total)}`);
        else if (j.downloaded) parts.push(fmtBytes(j.downloaded));
        if (j.speed) parts.push(`${fmtBytes(j.speed)}/s`);
        const eta = fmtEta(j.eta);
        if (eta) parts.push(eta);
        els.stageSub.textContent = parts.join("  ·  ");
        setRing(pct);
        els.bar.classList.remove("indeterminate");
        els.barFill.style.width = `${pct}%`;
        break;
      }
      case "processing":
        els.stageMsg.textContent = j.message || "Đang hoàn thiện tệp…";
        els.stageSub.textContent = "Sắp xong rồi";
        setRing(99);
        els.bar.classList.remove("indeterminate");
        els.barFill.style.width = "99%";
        break;
      case "done":
        if (renderedDoneFor !== j.id) renderDone(j);
        break;
      case "error":
        renderError(j);
        break;
      case "cancelled":
        setBusy(false);
        break;
    }
  }

  function renderMeta(info) {
    const meta = [];
    if (info.duration) meta.push(fmtDuration(info.duration));
    if (info.view_count) meta.push(`${fmtCount(info.view_count)} lượt xem`);
    els.meta.textContent = meta.join(" · ");
  }

  function renderInfo(j) {
    renderedInfoFor = j.id;
    const info = j.info;
    const p = PLATFORMS.find((x) => x.name === info.platform);
    els.badge.innerHTML = `<svg><use href="#${p ? p.icon : "i-globe"}"/></svg><span>${esc(info.platform)}</span>`;
    els.title.textContent = info.title || "Video";
    els.title.title = info.title || "";
    const by = [info.uploader, info.upload_date].filter(Boolean).join(" · ");
    els.uploader.textContent = by;
    els.uploader.hidden = !by;
    renderMeta(info);

    if (info.has_thumbnail) {
      const src = `/api/jobs/${j.id}/thumbnail`;
      const img = new Image();
      img.className = "stage-thumb";
      img.alt = "";
      img.onload = () => {
        if (renderedInfoFor !== j.id) return;
        els.stageBg.style.backgroundImage = `url("${src}")`;
        els.stageBg.classList.add("on");
        els.stage.classList.add("has-bg");
        if (!els.stage.classList.contains("ready")) els.stage.insertBefore(img, els.stage.querySelector(".stage-overlay"));
      };
      img.src = src;
    }
  }

  function renderDone(j) {
    renderedDoneFor = j.id;
    setBusy(false);
    if (j.info) renderMeta(j.info);
    const f = j.file;
    const media = `/api/jobs/${j.id}/media`;
    const poster = j.info && j.info.has_thumbnail ? `/api/jobs/${j.id}/thumbnail` : "";

    if (f.is_audio) {
      els.player.innerHTML = `
        <div class="audio-player">
          ${poster ? `<img class="audio-cover" src="${poster}" alt="">` : `<div class="audio-cover"><svg><use href="#i-music"/></svg></div>`}
          <audio controls preload="metadata" src="${media}"></audio>
        </div>`;
    } else {
      els.player.innerHTML = `<video controls playsinline preload="metadata" ${poster ? `poster="${poster}"` : ""} src="${media}"></video>`;
      const v = els.player.querySelector("video");
      v.addEventListener("error", () => toast("Trình duyệt không phát thử được định dạng này, nhưng bạn vẫn tải về bình thường."), { once: true });
    }
    els.stage.classList.add("ready");
    els.stage.querySelectorAll(".stage-thumb").forEach((n) => n.remove());

    els.bar.hidden = true;
    els.cancel.hidden = true;
    els.download.href = `/api/jobs/${j.id}/download`;
    els.download.setAttribute("download", f.filename || "");
    els.fileText.textContent = [f.ext.toUpperCase(), qualityLabel(f), fmtBytes(f.size)].filter(Boolean).join(" · ");
    els.download.hidden = false;
    els.copy.hidden = false;
    els.share.hidden = !IS_APP;
    els.again.hidden = false;
    toast(`Video đã sẵn sàng! Xem thử rồi bấm “${els.downloadLabel.textContent}”.`);
  }

  function renderError(j) {
    setBusy(false);
    stopPolling();
    els.stage.classList.add("error");
    els.ring.hidden = true;
    els.stageMsg.textContent = "Không tải được video";
    els.stageSub.textContent = "";
    els.bar.hidden = true;
    els.cancel.hidden = true;
    els.errorText.textContent = j.error || "Đã có lỗi xảy ra.";
    els.errorDetail.textContent = j.error_detail || "";
    els.errorDetails.hidden = !j.error_detail;
    els.errorBox.hidden = false;
    els.retry.hidden = false;
    els.again.hidden = false;
    if (renderedInfoFor !== j.id) {
      els.title.textContent = "Không đọc được video";
      els.uploader.textContent = j.url || "";
      els.badge.innerHTML = `<svg><use href="#i-alert"/></svg><span>Lỗi</span>`;
    }
  }

  /* ---------- Sự kiện ---------- */
  els.form.addEventListener("submit", start);
  els.url.addEventListener("input", updateDetected);
  els.url.addEventListener("paste", () => setTimeout(() => { els.url.value = extractUrl(els.url.value); updateDetected(); }, 0));

  els.clear.addEventListener("click", () => { els.url.value = ""; updateDetected(); els.url.focus(); });

  els.paste.addEventListener("click", async () => {
    try {
      const text = IS_APP ? String(NATIVE.readClipboard() || "") : await navigator.clipboard.readText();
      if (!text.trim()) { toast("Bộ nhớ tạm đang trống."); return; }
      els.url.value = extractUrl(text);
      updateDetected();
      toast("Đã dán link");
      els.submit.focus();
    } catch {
      toast("Trình duyệt chưa cho phép đọc bộ nhớ tạm. Hãy nhấn Ctrl + V vào ô nhập.");
      els.url.focus();
    }
  });

  els.quality.addEventListener("change", () => { try { localStorage.setItem(QUALITY_KEY, els.quality.value); } catch {} });

  els.cancel.addEventListener("click", async () => {
    if (!job || !job.id) return;
    const id = job.id;
    stopPolling();
    job = { ...job, state: "cancelled" };
    setBusy(false);
    els.result.hidden = true;
    toast("Đã hủy");
    try { await api(`/api/jobs/${id}`, { method: "DELETE" }); } catch {}
  });

  els.retry.addEventListener("click", () => start());

  els.again.addEventListener("click", () => {
    stopPolling();
    els.player.innerHTML = "";
    els.result.hidden = true;
    els.url.value = "";
    updateDetected();
    window.scrollTo({ top: 0, behavior: "smooth" });
    setTimeout(() => els.url.focus(), 250);
  });

  els.copy.addEventListener("click", async () => {
    const link = (job && job.info && job.info.webpage_url) || (job && job.url) || "";
    try {
      if (IS_APP) NATIVE.copyText(link); else await navigator.clipboard.writeText(link);
      toast("Đã sao chép link gốc");
    } catch { toast(link); }
  });

  els.download.addEventListener("click", (e) => {
    if (IS_APP) {
      // Trong ứng dụng: lưu thẳng vào Thư viện của điện thoại.
      e.preventDefault();
      if (job && job.id) { NATIVE.save(job.id); toast("Đang lưu vào máy…"); }
      return;
    }
    toast("Đang lưu vào thư mục Tải xuống…");
  });

  els.share.addEventListener("click", () => { if (IS_APP && job && job.id) NATIVE.share(job.id); });

  // Giao diện sáng / tối
  function isDark() {
    const t = document.documentElement.dataset.theme;
    return t ? t === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
  }
  function syncTheme() {
    if (IS_APP) { try { NATIVE.setDarkMode(isDark()); } catch {} }
  }
  els.theme.addEventListener("click", () => {
    const next = isDark() ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem("theme", next); } catch {}
    syncTheme();
  });
  try { matchMedia("(prefers-color-scheme: dark)").addEventListener("change", syncTheme); } catch {}

  // Các hàm phần mã Android gọi vào.
  window.taiVideoSyncTheme = syncTheme;
  window.taiVideoStart = (url) => {
    els.url.value = extractUrl(url);
    updateDetected();
    start();
  };
  window.__tvNative = (event, data) => {
    if (event === "saved") toast(data && data.message ? data.message : (data && data.ok ? "Đã lưu" : "Không lưu được"));
  };

  /* ---------- Khởi tạo ---------- */
  $("#year").textContent = new Date().getFullYear();
  try {
    const q = localStorage.getItem(QUALITY_KEY);
    if (q && [...els.quality.options].some((o) => o.value === q)) els.quality.value = q;
  } catch {}

  if (IS_APP) {
    els.downloadLabel.textContent = "Lưu vào máy";
    syncTheme();
  }

  const params = new URLSearchParams(location.search);
  const sharedUrl = params.get("url");
  if (sharedUrl) {
    els.url.value = sharedUrl;
    // xóa ?url=... khỏi thanh địa chỉ để tải lại trang không bắt đầu tải lần nữa
    try { history.replaceState(null, "", location.pathname); } catch {}
  }
  updateDetected();
  if (sharedUrl) start();

  function showEngine(h) {
    const ver = h.ready === false ? "đang khởi động…" : `yt-dlp ${h.yt_dlp}`;
    els.engine.textContent = `Bộ tải: ${ver}${h.ffmpeg ? "" : " · thiếu ffmpeg"}${h.app && h.version ? ` · Ứng dụng ${h.version}` : ""}`;
    if (h.ready === false || h.updating) setTimeout(() => api("/api/health").then(showEngine).catch(() => {}), 3000);
  }
  api("/api/health").then(showEngine).catch(() => {
    els.engine.textContent = "Chưa kết nối máy chủ";
    if (location.protocol === "file:") {
      setHint("Bạn đang mở tệp trực tiếp. Hãy chạy <b>run.bat</b> (Windows) hoặc <b>run.sh</b> (Mac/Linux) để dùng ứng dụng.", true);
    }
  });
})();
