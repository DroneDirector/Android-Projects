/* AlcorCut Pro website: animations, the app's glowing pointer, scroll-to-top, share, search. Own code, no libraries. */
(function () {
  "use strict";
  var doc = document, root = doc.documentElement, W = window;
  var cfg = W.ACS || { pointer: true, copied: "Link copied" };
  var reduce = W.matchMedia && W.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var finePointer = W.matchMedia && W.matchMedia("(hover: hover) and (pointer: fine)").matches;
  root.classList.add("acs-js");
  var $$ = function (s, el) { return Array.prototype.slice.call((el || doc).querySelectorAll(s)); };

  function ready(fn) { if (doc.readyState !== "loading") fn(); else doc.addEventListener("DOMContentLoaded", fn); }

  ready(function () {
    /* ---------------- reveal on scroll */
    var revealed = function (el) { el.classList.add("is-in"); };
    if ("IntersectionObserver" in W && !reduce) {
      var io = new IntersectionObserver(function (es) {
        es.forEach(function (e) { if (e.isIntersecting) { revealed(e.target); io.unobserve(e.target); } });
      }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
      $$("[data-reveal], .acs-android__word").forEach(function (el) { io.observe(el); });
    } else $$("[data-reveal], .acs-android__word").forEach(revealed);

    /* ---------------- top bar: solid when scrolled, reading progress, phone menu */
    var nav = doc.querySelector(".acs-nav"), bar = nav && nav.querySelector(".acs-progress i"), top = doc.querySelector(".acs-top");
    var onScroll = function () {
      var y = W.scrollY || 0, h = root.scrollHeight - W.innerHeight;
      if (nav) nav.classList.toggle("is-scrolled", y > 30);
      if (bar) bar.style.setProperty("--p", h > 0 ? Math.min(1, y / h) : 0);
      if (top) top.classList.toggle("is-on", y > W.innerHeight * 0.6);
    };
    W.addEventListener("scroll", onScroll, { passive: true }); onScroll();
    var menu = nav && nav.querySelector(".acs-nav__menu");
    if (menu) menu.addEventListener("click", function () {
      var open = nav.classList.toggle("is-open"); menu.setAttribute("aria-expanded", open ? "true" : "false");
    });
    $$(".acs-nav__links a").forEach(function (a) { a.addEventListener("click", function () { nav.classList.remove("is-open"); }); });

    /* ---------------- 3D tilt that follows the mouse */
    if (finePointer && !reduce) $$("[data-tilt]").forEach(function (el) {
      var raf = 0;
      el.parentElement.addEventListener("mousemove", function (e) {
        var r = el.getBoundingClientRect(), x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
        cancelAnimationFrame(raf);
        raf = requestAnimationFrame(function () { el.style.setProperty("--ry", (x * 16).toFixed(2) + "deg"); el.style.setProperty("--rx", (-y * 10).toFixed(2) + "deg"); });
      });
      el.parentElement.addEventListener("mouseleave", function () { el.style.setProperty("--ry", "0deg"); el.style.setProperty("--rx", "0deg"); });
    });

    /* ---------------- glow that follows the mouse on cards */
    $$("[data-glow]").forEach(function (el) {
      el.addEventListener("mousemove", function (e) {
        var r = el.getBoundingClientRect();
        el.style.setProperty("--mx", (e.clientX - r.left) + "px"); el.style.setProperty("--my", (e.clientY - r.top) + "px");
      });
    });

    /* ---------------- numbers count up */
    $$("[data-count]").forEach(function (el) {
      var m = /^(\D*)(\d+)(.*)$/.exec(el.textContent.trim()); if (!m || reduce) return;
      var end = +m[2], done = false; el.textContent = m[1] + "0" + m[3];
      var run = function () {
        if (done) return; done = true; var t0 = performance.now();
        (function step(t) { var k = Math.min(1, (t - t0) / 1400), e = 1 - Math.pow(1 - k, 3); el.textContent = m[1] + Math.round(end * e) + m[3]; if (k < 1) requestAnimationFrame(step); })(t0);
      };
      if ("IntersectionObserver" in W) { var o = new IntersectionObserver(function (es) { if (es[0].isIntersecting) { run(); o.disconnect(); } }); o.observe(el); } else run();
    });

    /* ---------------- screenshots carousel: arrows and drag */
    $$(".acs-gallery").forEach(function (g) {
      var tr = g.querySelector(".acs-gallery__track"); if (!tr) return;
      $$(".acs-round", g).forEach(function (b) { b.addEventListener("click", function () { tr.scrollBy({ left: +b.dataset.dir * tr.clientWidth * 0.6, behavior: "smooth" }); }); });
      var down = false, sx = 0, sl = 0, moved = 0;
      tr.addEventListener("pointerdown", function (e) { if (e.pointerType !== "mouse") return; down = true; moved = 0; sx = e.clientX; sl = tr.scrollLeft; tr.classList.add("is-drag"); });
      W.addEventListener("pointermove", function (e) { if (!down) return; moved = Math.abs(e.clientX - sx); tr.scrollLeft = sl - (e.clientX - sx); });
      W.addEventListener("pointerup", function () { if (!down) return; down = false; tr.classList.remove("is-drag"); });
      tr.addEventListener("dragstart", function (e) { e.preventDefault(); });
    });

    /* ---------------- Vimeo: the player loads only when Play is pressed (no Vimeo calls before that) */
    $$(".acs-player[data-src]").forEach(function (p) {
      var b = p.querySelector(".acs-player__play"); if (!b) return;
      b.addEventListener("click", function () {
        var f = doc.createElement("iframe");
        f.src = p.getAttribute("data-src"); f.allow = "autoplay; fullscreen; picture-in-picture"; f.allowFullscreen = true; f.title = "AlcorCut Pro film";
        p.querySelector(".acs-player__frame").appendChild(f);
        b.remove(); var n = p.querySelector(".acs-player__note"); if (n) n.remove();
      });
    });

    /* ---------------- share this page */
    var url = encodeURIComponent(location.href.split("#")[0]), title = encodeURIComponent(doc.title || "AlcorCut Pro");
    $$(".acs-share__btn[data-share]").forEach(function (a) {
      a.href = a.getAttribute("data-share").replace(/\{u\}/g, url).replace(/\{t\}/g, title);
      if (a.href.indexOf("mailto:") === 0) a.removeAttribute("target");
    });
    if (navigator.share && !finePointer) $$(".acs-share__native").forEach(function (b) {
      b.hidden = false;
      b.addEventListener("click", function () { navigator.share({ title: doc.title, url: location.href.split("#")[0] }).catch(function () {}); });
    });
    $$(".acs-share__copy").forEach(function (b) {
      b.addEventListener("click", function () {
        var u = location.href.split("#")[0], lab = b.querySelector("span"), old = lab ? lab.textContent : "";
        var ok = function () { b.classList.add("is-done"); if (lab) lab.textContent = cfg.copied; setTimeout(function () { b.classList.remove("is-done"); if (lab) lab.textContent = old; }, 2000); };
        if (navigator.clipboard) navigator.clipboard.writeText(u).then(ok, function () { prompt("Copy this link:", u); });
        else prompt("Copy this link:", u);
      });
    });
    $$('a[aria-disabled="true"]').forEach(function (a) { a.addEventListener("click", function (e) { if (a.getAttribute("href") === "#") e.preventDefault(); }); });

    /* ---------------- feature pages: search */
    $$(".acs-features").forEach(function (sec) {
      var input = sec.querySelector(".acs-search input"), count = sec.querySelector(".acs-features__count"), empty = sec.querySelector(".acs-features__empty");
      var items = $$(".acs-group li", sec);
      items.forEach(function (li) { li.dataset.html = li.innerHTML; li.dataset.text = li.textContent.toLowerCase(); });
      if (!input) return;
      var esc = function (s) { return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); };
      input.addEventListener("input", function () {
        var q = input.value.trim().toLowerCase(), n = 0;
        items.forEach(function (li) {
          var hit = !q || li.dataset.text.indexOf(q) >= 0;
          li.classList.toggle("is-hidden", !hit); li.innerHTML = li.dataset.html;
          if (hit && q) { n++; li.innerHTML = li.innerHTML.replace(new RegExp("(" + esc(q) + ")(?![^<]*>)", "gi"), "<mark>$1</mark>"); }
        });
        $$(".acs-group", sec).forEach(function (g) { g.classList.toggle("is-hidden", !g.querySelector("li:not(.is-hidden)")); });
        count.textContent = q ? n + (n === 1 ? " feature" : " features") + " found" : "";
        empty.hidden = !q || n > 0;
      });
    });

    /* ---------------- scroll to top, with the pointer's click ripple */
    if (top) top.addEventListener("click", function () {
      var r = doc.createElement("span"); r.className = "acs-ripple"; top.appendChild(r); setTimeout(function () { r.remove(); }, 460);
      W.scrollTo({ top: 0, behavior: reduce ? "auto" : "smooth" });
    });

    /* ---------------- the app's scroll wheel as the page's scroll bar (and under the screenshots) */
    if (finePointer) wheel(null, false);
    $$(".acs-gallery__track").forEach(function (tr) { wheel(tr, true); });

    /* ---------------- the app's glowing mouse pointer */
    if (cfg.pointer && finePointer) pointer();
  });

  /**
   * Port of the app's MousePointerHost (ui/MousePointer.kt): an arrow with a gradient fill, a soft
   * breathing halo, a comet trail (260 ms), a click ripple (450 ms) and a spark orbiting at 13 px
   * every 2.4 s. Colour #74def8 and its partner (hue + 70°), as in the app.
   */
  function pointer() {
    var cv = doc.createElement("canvas"); cv.className = "acs-pointer"; cv.setAttribute("aria-hidden", "true");
    doc.body.appendChild(cv);
    var ctx = cv.getContext("2d"), dpr = 1, pos = null, pressed = false, clickAt = -1e9, clickPos = { x: 0, y: 0 }, trail = [], hoverLink = false;
    var col = [79, 227, 255], sec = partner(col), t0 = performance.now();
    // The app draws in dp; on a tablet or DeX screen one dp is about 1.75 screen pixels.
    var U = 1.75;
    function partner(c) { // hue + 70°, as CursorStyle.partner
      var r = c[0] / 255, g = c[1] / 255, b = c[2] / 255, mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn, h = 0;
      if (d) h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
      h = (h * 60 + 360 + 70) % 360; var s = mx ? d / mx : 0, v = mx;
      if (s < 0.15) { s = 0; v = 0.75; }
      var C = v * s, X = C * (1 - Math.abs((h / 60) % 2 - 1)), m = v - C, rr = 0, gg = 0, bb = 0;
      if (h < 60) { rr = C; gg = X; } else if (h < 120) { rr = X; gg = C; } else if (h < 180) { gg = C; bb = X; } else if (h < 240) { gg = X; bb = C; } else if (h < 300) { rr = X; bb = C; } else { rr = C; bb = X; }
      return [Math.round((rr + m) * 255), Math.round((gg + m) * 255), Math.round((bb + m) * 255)];
    }
    function rgba(c, a) { return "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + Math.max(0, a).toFixed(3) + ")"; }
    function size() { dpr = Math.min(2, W.devicePixelRatio || 1); cv.width = innerWidth * dpr; cv.height = innerHeight * dpr; cv.style.width = innerWidth + "px"; cv.style.height = innerHeight + "px"; }
    size(); W.addEventListener("resize", size);
    var seen = false;
    doc.addEventListener("mousemove", function (e) {
      pos = { x: e.clientX, y: e.clientY }; var t = performance.now(); trail.push([pos.x, pos.y, t]); if (trail.length > 24) trail.shift();
      if (!seen) { seen = true; root.classList.add("acs-ptr"); }
      var el = e.target; hoverLink = !!(el && el.closest && el.closest("a,button,input,label,[role=button]"));
    }, { passive: true });
    doc.addEventListener("mousedown", function (e) { pressed = true; clickAt = performance.now(); clickPos = { x: e.clientX, y: e.clientY }; });
    doc.addEventListener("mouseup", function () { pressed = false; });
    doc.documentElement.addEventListener("mouseleave", function () { pos = null; });
    W.addEventListener("blur", function () { pos = null; });
    function circle(x, y, r) { ctx.beginPath(); ctx.arc(x, y, Math.max(0, r), 0, Math.PI * 2); }
    function radial(x, y, r, stops) { var g = ctx.createRadialGradient(x, y, 0, x, y, Math.max(0.01, r)); stops.forEach(function (s) { g.addColorStop(s[0], s[1]); }); return g; }
    (function frame(now) {
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, innerWidth, innerHeight);
      if (pos) {
        // Effects always on, as in the app (not tied to the system's reduce-animations setting).
        var p = pos, ph = ((now - t0) % 2400) / 2400, fx = true;
        if (fx) {
          // comet trail
          var pts = trail.filter(function (q) { return now - q[2] < 260; });
          pts.forEach(function (q, i) {
            var life = 1 - (now - q[2]) / 260, k = (i + 1) / pts.length, r = (9 * k + 2) * U;
            ctx.fillStyle = radial(q[0], q[1], r, [[0, rgba(sec, 0.45 * life * k)], [1, rgba(sec, 0)]]); circle(q[0], q[1], r); ctx.fill();
          });
          // breathing halo (brighter while pressed, and a little over links and buttons)
          var br = (16 + Math.sin(ph * 2 * Math.PI) * 3) * U, a = pressed ? 0.42 : hoverLink ? 0.34 : 0.26;
          ctx.fillStyle = radial(p.x, p.y, br, [[0, rgba(col, a)], [0.5, rgba(col, 0.08)], [1, rgba(col, 0)]]); circle(p.x, p.y, br); ctx.fill();
          // orbiting spark
          var an = ph * 2 * Math.PI, sx = p.x + Math.cos(an) * 13 * U, sy = p.y + Math.sin(an) * 13 * U;
          ctx.fillStyle = rgba(sec, 0.5); circle(sx, sy, 3.2 * U); ctx.fill();
          ctx.fillStyle = "rgba(255,255,255,.85)"; circle(sx, sy, 1.6 * U); ctx.fill();
          // click ripple
          var dt = now - clickAt;
          if (dt >= 0 && dt <= 450) {
            var f = dt / 450;
            ctx.lineWidth = (2.5 - 1.5 * f) * U; ctx.strokeStyle = rgba(col, (1 - f) * 0.9); circle(clickPos.x, clickPos.y, (6 + 22 * f) * U); ctx.stroke();
            ctx.lineWidth = U; ctx.strokeStyle = rgba(sec, (1 - f) * 0.5); circle(clickPos.x, clickPos.y, (4 + 34 * f) * U); ctx.stroke();
          }
        }
        // the arrow, tip at the mouse position
        var s = (pressed ? 0.9 : 1) * U;
        ctx.beginPath();
        ctx.moveTo(p.x, p.y); ctx.lineTo(p.x, p.y + 17 * s); ctx.lineTo(p.x + 4.5 * s, p.y + 13 * s); ctx.lineTo(p.x + 7.5 * s, p.y + 19.5 * s);
        ctx.lineTo(p.x + 10 * s, p.y + 18.3 * s); ctx.lineTo(p.x + 7 * s, p.y + 12 * s); ctx.lineTo(p.x + 12.5 * s, p.y + 12 * s); ctx.closePath();
        ctx.lineJoin = "round"; ctx.lineWidth = 3 * U; ctx.strokeStyle = "rgba(0,0,0,.75)"; ctx.stroke();
        if (fx) { var lg = ctx.createLinearGradient(p.x, p.y, p.x + 12 * U, p.y + 19 * U); lg.addColorStop(0, rgba(col, 1)); lg.addColorStop(1, rgba(sec, 1)); ctx.fillStyle = lg; } else ctx.fillStyle = rgba(col, 1);
        ctx.fill(); ctx.lineWidth = U; ctx.strokeStyle = fx ? "rgba(255,255,255,.9)" : "rgba(255,255,255,.6)"; ctx.stroke();
      }
      requestAnimationFrame(frame);
    })(performance.now());
  }

  /**
   * Port of the app's TimelineWheel (editor/TimelineWheel.kt): a glass track with rolling ridges, a
   * cyan-to-violet thumb with a sweeping scan line, an animated gradient rim, and — while it moves —
   * "upscale" pixel blocks that refine from coarse to fine, plus sparkles. Drag it like a wheel or click
   * to jump. target = null is the page itself (vertical, fixed on the right edge).
   */
  function wheel(target, horizontal) {
    var page = !target, U = 1.75;
    var cv = doc.createElement("canvas"); cv.className = "acs-wheel" + (horizontal ? " acs-wheel--h" : page ? " acs-wheel--page" : "");
    cv.setAttribute("aria-hidden", "true");
    if (page) { doc.body.appendChild(cv); root.classList.add("acs-has-wheel"); }
    else target.parentNode.insertBefore(cv, target.nextSibling);
    var ctx = cv.getContext("2d"), dpr = 1, W0 = 0, H0 = 0, energy = 0, last = 0, t0 = performance.now(), dragging = false, prevPos = -1;
    var col = [79, 227, 255], vio = [155, 107, 255], tip = [167, 238, 255];
    function rgba(c, a) { return "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + Math.max(0, Math.min(1, a)).toFixed(3) + ")"; }
    function hash(a, b, c) { var h = (a * 374761393 + b * 668265263 + c * 1274126177) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) & 0xffff) / 65535; }
    function metrics() {
      if (page) { var max = root.scrollHeight - innerHeight; return { pos: W.scrollY, max: Math.max(0, max), view: innerHeight }; }
      return horizontal ? { pos: target.scrollLeft, max: target.scrollWidth - target.clientWidth, view: target.clientWidth } : { pos: target.scrollTop, max: target.scrollHeight - target.clientHeight, view: target.clientHeight };
    }
    function scrollTo(v) { if (page) W.scrollTo(0, v); else if (horizontal) target.scrollLeft = v; else target.scrollTop = v; }
    function size() { var r = cv.getBoundingClientRect(); dpr = Math.min(2, W.devicePixelRatio || 1); W0 = r.width; H0 = r.height; cv.width = Math.max(1, W0 * dpr); cv.height = Math.max(1, H0 * dpr); }
    size(); W.addEventListener("resize", size);
    // drag like a wheel; click to jump
    var start = 0, startPos = 0, moved = false;
    cv.addEventListener("pointerdown", function (e) { dragging = true; moved = false; start = horizontal ? e.clientX : e.clientY; startPos = metrics().pos; cv.setPointerCapture(e.pointerId); e.preventDefault(); });
    cv.addEventListener("pointermove", function (e) {
      if (!dragging) return; var m = metrics(), len = horizontal ? W0 : H0, d = (horizontal ? e.clientX : e.clientY) - start;
      if (Math.abs(d) > 3) moved = true;
      scrollTo(startPos + d * (m.max + len) / len);
    });
    cv.addEventListener("pointerup", function (e) {
      dragging = false; if (moved) return;
      var r = cv.getBoundingClientRect(), f = horizontal ? (e.clientX - r.left) / r.width : (e.clientY - r.top) / r.height, m = metrics();
      if (page) W.scrollTo({ top: f * m.max, behavior: "smooth" }); else target.scrollTo({ left: horizontal ? f * m.max : 0, top: horizontal ? 0 : f * m.max, behavior: "smooth" });
    });
    function rr(x, y, w, h, r) { ctx.beginPath(); if (ctx.roundRect) ctx.roundRect(x, y, w, h, r); else ctx.rect(x, y, w, h); }
    (function frame(now) {
      var m = metrics(), dt = now - last; last = now;
      if (m.pos !== prevPos || dragging) { if (prevPos !== -1) energy = 1; prevPos = m.pos; } else energy = Math.max(0, energy - dt / 900);
      var e = energy * energy * (3 - 2 * energy); // ease out
      var ph = ((now - t0) % 2400) / 2400, can = m.max > 1, dim = can ? 1 : 0.4;
      cv.style.opacity = can ? "" : "0";
      // Work along the wheel's length: L = long side, S = short side (vertical = as in the app).
      var L = horizontal ? W0 : H0, S = horizontal ? H0 : W0;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, W0, H0);
      if (horizontal) ctx.setTransform(0, dpr, dpr, 0, 0, 0); // draw in "vertical" coordinates, mapped sideways
      var w = S, h = L, r = w / 2;
      // glass track
      rr(0, 0, w, h, r); ctx.fillStyle = "rgba(13,16,22,.8)"; ctx.fill();
      // rolling ridges
      var sp = 5 * U, off = (m.pos * 0.35) % sp;
      for (var y = -off; y < h; y += sp) {
        var edge = 1 - Math.abs(y / h - 0.5) * 2;
        ctx.strokeStyle = "rgba(255,255,255," + ((0.06 + 0.10 * edge) * dim).toFixed(3) + ")"; ctx.lineWidth = U;
        ctx.beginPath(); ctx.moveTo(w * 0.3, y); ctx.lineTo(w * 0.7, y); ctx.stroke();
      }
      // thumb position and size
      var vis = can ? Math.min(h, Math.max(h * 0.22, h * m.view / (m.view + m.max))) : h;
      var t = can ? Math.min(1, Math.max(0, m.pos / m.max)) : 0, top = (h - vis) * t;
      // "upscale" pixels while moving
      if (e > 0.01) {
        var cell = 2 * U + 7 * U * e, step = Math.floor(ph * 12), j = 0;
        for (var cy = 0; cy < h; cy += cell, j++) {
          var i = 0;
          for (var cx = 0; cx < w; cx += cell, i++) {
            var v = hash(i, j, step), near = 1 - Math.min(1, Math.abs(cy + cell / 2 - (top + vis / 2)) / h), a = e * near * (0.15 + 0.45 * v);
            if (a > 0.02) { ctx.fillStyle = rgba(v > 0.5 ? col : vio, a); ctx.fillRect(cx, cy, cell - 1, cell - 1); }
          }
        }
      }
      // thumb with gradient glow
      var g = ctx.createLinearGradient(0, top, 0, top + vis);
      g.addColorStop(0, rgba(col, 0.85 * dim)); g.addColorStop(0.5, rgba(vio, 0.85 * dim)); g.addColorStop(1, rgba(tip, 0.9 * dim));
      rr(w * 0.18, top + 1, w * 0.64, Math.max(2, vis - 2), w * 0.32); ctx.fillStyle = g; ctx.fill();
      // scan line sweeping through the thumb
      var scanY = top + vis * ((ph * 1.4) % 1), sa = (0.25 + 0.75 * e) * dim, sg = ctx.createLinearGradient(0, scanY - 6, 0, scanY + 6);
      sg.addColorStop(0, "rgba(255,255,255,0)"); sg.addColorStop(0.5, "rgba(255,255,255," + sa.toFixed(3) + ")"); sg.addColorStop(1, "rgba(255,255,255,0)");
      ctx.fillStyle = sg; ctx.fillRect(0, scanY - 6, w, 12);
      // sparkles while moving
      if (e > 0.05) for (var k = 0; k < 4; k++) {
        var st = Math.floor(ph * 30), sx = w * hash(k, 7, st), sy = top + vis * hash(k, 11, st);
        ctx.fillStyle = "rgba(255,255,255," + (e * 0.9).toFixed(3) + ")"; ctx.beginPath(); ctx.arc(sx, sy, U * (0.6 + 0.6 * Math.max(0, Math.sin(ph * 40 + k))), 0, Math.PI * 2); ctx.fill();
      }
      // rim: animated gradient border
      var rim;
      if (ctx.createConicGradient) { rim = ctx.createConicGradient(0, w / 2, h * ph); rim.addColorStop(0, rgba(col, 1)); rim.addColorStop(0.33, rgba(vio, 1)); rim.addColorStop(0.66, rgba(tip, 1)); rim.addColorStop(1, rgba(col, 1)); }
      else rim = rgba(col, 1);
      ctx.globalAlpha = (0.45 + 0.55 * e) * dim; ctx.lineWidth = (1 + 1.2 * e) * U; ctx.strokeStyle = rim;
      rr(ctx.lineWidth / 2, ctx.lineWidth / 2, w - ctx.lineWidth, h - ctx.lineWidth, r); ctx.stroke(); ctx.globalAlpha = 1;
      requestAnimationFrame(frame);
    })(performance.now());
  }
})();
