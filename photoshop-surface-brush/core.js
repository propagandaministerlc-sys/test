/*
 * Surface Brush – Kern-Algorithmen (reines JavaScript, keine Photoshop-Abhängigkeit).
 *
 * Aus dem Bild unter den Pinselstrichen wird klassisch (ohne KI-Modell) geschätzt:
 *   - Beleuchtung/Schattierung (Luminanz relativ zur mittleren Helligkeit der Fläche)
 *   - Relief (geglättete Helligkeit als Höhenfeld -> Normalen)
 *   - Faltenrichtung (Strukturtensor -> Tangentenfeld)
 * Damit werden Striche auf die Oberfläche gelegt, Flächen umgefärbt oder Licht gemalt.
 *
 * Alle Bilder: Uint8Array, zeilenweise, sRGB. Ergebnis: RGBA (nicht vormultipliziert).
 */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.SurfaceCore = factory();
})(this, function () {
  "use strict";

  // ---------- Farbraum ----------
  const TO_LIN = new Float32Array(256);
  for (let i = 0; i < 256; i++) {
    const c = i / 255;
    TO_LIN[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }
  const ENC_N = 4095;
  const TO_SRGB = new Uint8Array(ENC_N + 1);
  for (let i = 0; i <= ENC_N; i++) {
    const c = i / ENC_N;
    const v = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    TO_SRGB[i] = Math.round(v * 255);
  }
  function enc(v) {
    if (!(v > 0)) return 0;
    if (v >= 1) return 255;
    return TO_SRGB[(v * ENC_N + 0.5) | 0];
  }
  function clamp(v, lo, hi) {
    return v < lo ? lo : v > hi ? hi : v;
  }

  // ---------- Filter ----------
  function boxH(src, dst, w, h, r) {
    const inv = 1 / (2 * r + 1);
    const wm = w - 1;
    for (let y = 0; y < h; y++) {
      const row = y * w;
      let acc = src[row] * (r + 1);
      for (let i = 1; i <= r; i++) acc += src[row + (i < wm ? i : wm)];
      for (let x = 0; x < w; x++) {
        dst[row + x] = acc * inv;
        const xa = x + r + 1;
        const xb = x - r;
        acc += src[row + (xa < wm ? xa : wm)] - src[row + (xb > 0 ? xb : 0)];
      }
    }
  }
  function boxV(src, dst, w, h, r) {
    const inv = 1 / (2 * r + 1);
    const hm = h - 1;
    const acc = new Float32Array(w);
    for (let x = 0; x < w; x++) acc[x] = src[x] * (r + 1);
    for (let i = 1; i <= r; i++) {
      const row = (i < hm ? i : hm) * w;
      for (let x = 0; x < w; x++) acc[x] += src[row + x];
    }
    for (let y = 0; y < h; y++) {
      const row = y * w;
      const ya = y + r + 1;
      const yb = y - r;
      const ra = (ya < hm ? ya : hm) * w;
      const rb = (yb > 0 ? yb : 0) * w;
      for (let x = 0; x < w; x++) {
        dst[row + x] = acc[x] * inv;
        acc[x] += src[ra + x] - src[rb + x];
      }
    }
  }
  /** Gauß-Näherung durch drei Box-Filter. Liefert ein neues Array. */
  function blur(src, w, h, sigma) {
    const out = new Float32Array(src);
    if (!(sigma > 0.3)) return out;
    const r = Math.max(1, Math.round((Math.sqrt(4 * sigma * sigma + 1) - 1) / 2));
    const tmp = new Float32Array(src.length);
    for (let p = 0; p < 3; p++) {
      boxH(out, tmp, w, h, r);
      boxV(tmp, out, w, h, r);
    }
    return out;
  }

  /** Guided Filter: glättet p kantentreu entlang der Kanten von I. */
  function guided(I, p, w, h, radius, eps) {
    const n = w * h;
    const s = radius / 2;
    const mI = blur(I, w, h, s);
    const mp = blur(p, w, h, s);
    const t = new Float32Array(n);
    for (let i = 0; i < n; i++) t[i] = I[i] * I[i];
    const cII = blur(t, w, h, s);
    for (let i = 0; i < n; i++) t[i] = I[i] * p[i];
    const cIp = blur(t, w, h, s);
    const a = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const v = cII[i] - mI[i] * mI[i];
      const c = cIp[i] - mI[i] * mp[i];
      a[i] = c / (v + eps);
      t[i] = mp[i] - a[i] * mI[i];
    }
    const ma = blur(a, w, h, s);
    const mb = blur(t, w, h, s);
    for (let i = 0; i < n; i++) t[i] = clamp(ma[i] * I[i] + mb[i], 0, 1);
    return t;
  }

  // ---------- Analyse ----------
  /**
   * @param base   Uint8Array, comps Kanäle pro Pixel (3 oder 4)
   * @param sigma  Relief-Skala in Pixeln (Größe der Falten)
   */
  function analyze(base, w, h, comps, sigma) {
    const n = w * h;
    const Y = new Float32Array(n);
    const Yp = new Float32Array(n);
    for (let i = 0, j = 0; i < n; i++, j += comps) {
      const y = 0.2126 * TO_LIN[base[j]] + 0.7152 * TO_LIN[base[j + 1]] + 0.0722 * TO_LIN[base[j + 2]];
      Y[i] = y;
      Yp[i] = Math.cbrt(y);
    }
    const H = blur(Yp, w, h, sigma);
    const gx = new Float32Array(n);
    const gy = new Float32Array(n);
    let sum = 0;
    for (let y = 0; y < h; y++) {
      const y0 = (y > 0 ? y - 1 : y) * w;
      const y1 = (y < h - 1 ? y + 1 : y) * w;
      for (let x = 0; x < w; x++) {
        const x0 = x > 0 ? x - 1 : x;
        const x1 = x < w - 1 ? x + 1 : x;
        const i = y * w + x;
        const dx = H[y * w + x1] - H[y * w + x0];
        const dy = H[y1 + x] - H[y0 + x];
        gx[i] = dx;
        gy[i] = dy;
        sum += Math.sqrt(dx * dx + dy * dy);
      }
    }
    // Gradienten auf ~[-1,1] normieren, damit Regler unabhängig vom Kontrast wirken.
    const k = 1 / ((3 * sum) / n + 1e-6);
    for (let i = 0; i < n; i++) {
      let dx = gx[i] * k;
      let dy = gy[i] * k;
      const m = dx * dx + dy * dy;
      if (m > 1) {
        const s = 1 / Math.sqrt(m);
        dx *= s;
        dy *= s;
      }
      gx[i] = dx;
      gy[i] = dy;
    }
    return { w, h, base, comps, sigma, Y, Yp, gx, gy, flow: null };
  }

  /** Tangentenfeld (Richtung entlang der Falten) + Kohärenz. */
  function flowField(an) {
    if (an.flow) return an.flow;
    const { w, h, gx, gy } = an;
    const n = w * h;
    const s = Math.max(1.5, an.sigma * 2);
    const t = new Float32Array(n);
    for (let i = 0; i < n; i++) t[i] = gx[i] * gx[i];
    const jxx = blur(t, w, h, s);
    for (let i = 0; i < n; i++) t[i] = gy[i] * gy[i];
    const jyy = blur(t, w, h, s);
    for (let i = 0; i < n; i++) t[i] = gx[i] * gy[i];
    const jxy = blur(t, w, h, s);
    const tx = jxx; // Arrays wiederverwenden
    const ty = jyy;
    const coh = jxy;
    for (let i = 0; i < n; i++) {
      const a = jxx[i];
      const b = jxy[i];
      const c = jyy[i];
      const theta = 0.5 * Math.atan2(2 * b, a - c);
      const d = Math.sqrt((a - c) * (a - c) + 4 * b * b);
      const tr = a + c;
      const co = tr > 1e-9 ? d / tr : 0;
      const strength = Math.min(1, Math.sqrt(tr) / 0.15);
      tx[i] = -Math.sin(theta);
      ty[i] = Math.cos(theta);
      coh[i] = co * strength;
    }
    an.flow = { tx, ty, coh };
    return an.flow;
  }

  // ---------- Hilfen ----------
  function premult(stroke, n) {
    const P = new Float32Array(n * 4);
    for (let i = 0, j = 0; i < n; i++, j += 4) {
      const a = stroke[j + 3] / 255;
      if (a === 0) continue;
      P[j] = TO_LIN[stroke[j]] * a;
      P[j + 1] = TO_LIN[stroke[j + 1]] * a;
      P[j + 2] = TO_LIN[stroke[j + 2]] * a;
      P[j + 3] = a;
    }
    return P;
  }
  function plane(P, n, c) {
    const out = new Float32Array(n);
    for (let i = 0, j = c; i < n; i++, j += 4) out[i] = P[j];
    return out;
  }

  /**
   * Referenzhelligkeit je zusammenhängender Strichfläche (geometrisches Mittel).
   * Y / ref ist dann die Schattierung relativ zur "normal beleuchteten" Fläche.
   */
  function componentRef(Y, A, w, h) {
    const n = w * h;
    const thr = 8 / 255;
    const lab = new Int32Array(n);
    const parent = [0];
    const find = (x) => {
      while (parent[x] !== x) {
        parent[x] = parent[parent[x]];
        x = parent[x];
      }
      return x;
    };
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = y * w + x;
        if (A[i] <= thr) continue;
        const l = x > 0 ? lab[i - 1] : 0;
        const u = y > 0 ? lab[i - w] : 0;
        if (l && u) {
          const a = find(l);
          const b = find(u);
          if (a !== b) parent[a > b ? a : b] = a > b ? b : a;
          lab[i] = a < b ? a : b;
        } else if (l || u) {
          lab[i] = l || u;
        } else {
          parent.push(parent.length);
          lab[i] = parent.length - 1;
        }
      }
    }
    const sl = new Float64Array(parent.length);
    const sw = new Float64Array(parent.length);
    let gl = 0;
    let gw = 0;
    for (let i = 0; i < n; i++) {
      if (!lab[i]) continue;
      const r = find(lab[i]);
      lab[i] = r;
      const l = Math.log(Y[i] + 1e-3) * A[i];
      sl[r] += l;
      sw[r] += A[i];
      gl += l;
      gw += A[i];
    }
    const g = gw > 0 ? Math.exp(gl / gw) : 0.2;
    const ref = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const r = lab[i];
      ref[i] = r ? Math.exp(sl[r] / sw[r]) : g;
    }
    return ref;
  }

  /** Farbe (linear) * Schattierung -> sRGB, mit weichem Erhalt der Glanzlichter. */
  function shadeOut(out, j, r, g, b, a, s, shade, hl) {
    const f = shade > 0 ? Math.pow(clamp(s, 0.02, 6), shade) : 1;
    r *= f;
    g *= f;
    b *= f;
    const m = Math.max(r, g, b);
    if (m > 1 && hl > 0) {
      const ex = Math.min(1, (m - 1) * hl);
      r = Math.min(1, r);
      g = Math.min(1, g);
      b = Math.min(1, b);
      r += (1 - r) * ex;
      g += (1 - g) * ex;
      b += (1 - b) * ex;
    }
    out[j] = enc(r);
    out[j + 1] = enc(g);
    out[j + 2] = enc(b);
    out[j + 3] = Math.round(clamp(a, 0, 1) * 255);
  }

  // ---------- Modus 1: Oberflächen-Pinsel ----------
  /**
   * opts: wrap   – Verformung der Striche über das Relief (px)
   *       follow – Verlaufen der Farbe entlang der Falten (px)
   *       shade  – wie stark die Farbe die Schatten der Oberfläche annimmt (0..1)
   */
  function paint(an, stroke, opts) {
    const { w, h, gx, gy, Y } = an;
    const n = w * h;
    let P = premult(stroke, n);
    const wm = w - 1;
    const hm = h - 1;

    const sample = (src, x, y, dst, o, wt) => {
      if (x < 0 || y < 0 || x > wm || y > hm) return;
      const x0 = x | 0;
      const y0 = y | 0;
      const fx = x - x0;
      const fy = y - y0;
      const x1 = x0 < wm ? x0 + 1 : x0;
      const y1 = y0 < hm ? y0 + 1 : y0;
      const a = (y0 * w + x0) * 4;
      const b = (y0 * w + x1) * 4;
      const c = (y1 * w + x0) * 4;
      const d = (y1 * w + x1) * 4;
      const w00 = (1 - fx) * (1 - fy) * wt;
      const w10 = fx * (1 - fy) * wt;
      const w01 = (1 - fx) * fy * wt;
      const w11 = fx * fy * wt;
      dst[o] += src[a] * w00 + src[b] * w10 + src[c] * w01 + src[d] * w11;
      dst[o + 1] += src[a + 1] * w00 + src[b + 1] * w10 + src[c + 1] * w01 + src[d + 1] * w11;
      dst[o + 2] += src[a + 2] * w00 + src[b + 2] * w10 + src[c + 2] * w01 + src[d + 2] * w11;
      dst[o + 3] += src[a + 3] * w00 + src[b + 3] * w10 + src[c + 3] * w01 + src[d + 3] * w11;
    };

    // a) Striche über das Relief verformen (wie eine Verschiebungsmatrix)
    const wrap = opts.wrap || 0;
    if (wrap > 0) {
      const Q = new Float32Array(n * 4);
      for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) {
          const i = y * w + x;
          sample(P, x + gx[i] * wrap, y + gy[i] * wrap, Q, i * 4, 1);
        }
      }
      P = Q;
    }

    // b) Farbe entlang der Falten verlaufen lassen (Linienintegral entlang des Tangentenfelds)
    const follow = opts.follow || 0;
    if (follow >= 1) {
      const { tx, ty, coh } = flowField(an);
      const cover = blur(plane(P, n, 3), w, h, follow / 2 + 1);
      const K = Math.min(24, Math.ceil(follow));
      const step = follow / K;
      const norm = 1 / ((K + 1) * (K + 1));
      const Q = new Float32Array(n * 4);
      for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) {
          const i = y * w + x;
          if (cover[i] < 1e-4) continue;
          const o = i * 4;
          Q[o] = P[o] * (K + 1);
          Q[o + 1] = P[o + 1] * (K + 1);
          Q[o + 2] = P[o + 2] * (K + 1);
          Q[o + 3] = P[o + 3] * (K + 1);
          for (let dir = -1; dir <= 1; dir += 2) {
            let px = x;
            let py = y;
            let vx = tx[i] * dir;
            let vy = ty[i] * dir;
            for (let k = 1; k <= K; k++) {
              const xi = (px + 0.5) | 0;
              const yi = (py + 0.5) | 0;
              const q = yi * w + xi;
              let ax = tx[q];
              let ay = ty[q];
              if (ax * vx + ay * vy < 0) {
                ax = -ax;
                ay = -ay;
              }
              vx = ax;
              vy = ay;
              const c = coh[q] * step;
              px += ax * c;
              py += ay * c;
              if (px < 0 || py < 0 || px > wm || py > hm) break;
              sample(P, px, py, Q, o, K + 1 - k);
            }
          }
          Q[o] *= norm;
          Q[o + 1] *= norm;
          Q[o + 2] *= norm;
          Q[o + 3] *= norm;
        }
      }
      P = Q;
    }

    // c) Schattierung der Oberfläche übernehmen
    const A = plane(P, n, 3);
    const ref = componentRef(Y, A, w, h);
    const out = new Uint8Array(n * 4);
    const shade = opts.shade == null ? 0.85 : opts.shade;
    for (let i = 0, j = 0; i < n; i++, j += 4) {
      const a = P[j + 3];
      if (a < 1 / 510) continue;
      const ia = 1 / a;
      shadeOut(out, j, P[j] * ia, P[j + 1] * ia, P[j + 2] * ia, a, Y[i] / ref[i], shade, 0.5);
    }
    return out;
  }

  // ---------- Modus 2: Umfärben mit erhaltenen Schatten ----------
  /**
   * opts: snap       – Kanten-Fang in px (grob gemalte Maske rastet an Objektkanten ein)
   *       shade      – Anteil der Original-Schattierung (0..1)
   *       highlights – Erhalt der Glanzlichter (0..1)
   */
  function recolor(an, stroke, opts) {
    const { w, h, Y, Yp } = an;
    const n = w * h;
    const P = premult(stroke, n);
    const a0 = plane(P, n, 3);
    const snap = opts.snap || 0;
    const sig = Math.max(1.5, snap);
    const ab = blur(a0, w, h, sig);
    const cr = blur(plane(P, n, 0), w, h, sig);
    const cg = blur(plane(P, n, 1), w, h, sig);
    const cb = blur(plane(P, n, 2), w, h, sig);
    let A = a0;
    if (snap > 0) {
      A = guided(Yp, a0, w, h, snap, 0.002);
      for (let i = 0; i < n; i++) {
        const t = clamp((A[i] - 0.06) / 0.88, 0, 1);
        A[i] = ab[i] > 1e-3 ? t * t * (3 - 2 * t) : 0;
      }
    }
    const ref = componentRef(Y, A, w, h);
    const out = new Uint8Array(n * 4);
    const shade = opts.shade == null ? 1 : opts.shade;
    const hl = opts.highlights == null ? 0.5 : opts.highlights;
    for (let i = 0, j = 0; i < n; i++, j += 4) {
      const a = A[i];
      if (a < 1 / 510 || ab[i] < 1e-3) continue;
      const ia = 1 / ab[i];
      shadeOut(out, j, cr[i] * ia, cg[i] * ia, cb[i] * ia, a, Y[i] / ref[i], shade, hl);
    }
    return out;
  }

  // ---------- Modus 3: Licht malen ----------
  /**
   * opts: radius    – Ausbreitung des Lichts (px)
   *       intensity – Helligkeit (1 = Verdopplung auf frontal getroffenen Flächen)
   *       height    – Höhe der Lichtquelle über der Fläche (0 = Streiflicht, 1 = frontal)
   *       relief    – wie stark das Relief das Licht moduliert (0..1)
   */
  function light(an, stroke, opts) {
    const { w, h, Y, gx, gy, base, comps } = an;
    const n = w * h;
    const P = premult(stroke, n);
    const a0 = plane(P, n, 3);
    const R = Math.max(1, opts.radius || 40);
    const A = blur(a0, w, h, R);
    const cr = blur(plane(P, n, 0), w, h, R);
    const cg = blur(plane(P, n, 1), w, h, R);
    const cb = blur(plane(P, n, 2), w, h, R);
    const Sh = blur(Y, w, h, Math.max(2, an.sigma * 4));
    let maxA = 0;
    let maxA0 = 0;
    let meanSh = 0;
    for (let i = 0; i < n; i++) {
      if (A[i] > maxA) maxA = A[i];
      if (a0[i] > maxA0) maxA0 = a0[i];
      meanSh += Sh[i];
    }
    meanSh /= n;
    const out = new Uint8Array(n * 4);
    if (maxA <= 0) return out;
    // Richtung zur Lichtquelle = Gradient der weichen Lichtmaske (zeigt zur Strichmitte)
    let maxG = 1e-9;
    const dxA = new Float32Array(n);
    const dyA = new Float32Array(n);
    for (let y = 0; y < h; y++) {
      const y0 = (y > 0 ? y - 1 : y) * w;
      const y1 = (y < h - 1 ? y + 1 : y) * w;
      for (let x = 0; x < w; x++) {
        const i = y * w + x;
        const dx = A[y * w + (x < w - 1 ? x + 1 : x)] - A[y * w + (x > 0 ? x - 1 : x)];
        const dy = A[y1 + x] - A[y0 + x];
        dxA[i] = dx;
        dyA[i] = dy;
        const m = dx * dx + dy * dy;
        if (m > maxG) maxG = m;
      }
    }
    const ig = 1 / Math.sqrt(maxG);
    const height = clamp(opts.height == null ? 0.5 : opts.height, 0, 1);
    const kxy = 1.5 * (1 - 0.8 * height);
    const hz = 0.25 + height;
    const rel = 2.5 * (opts.relief == null ? 0.6 : opts.relief);
    const inten = opts.intensity == null ? 1 : opts.intensity;
    const aNorm = 1 / (0.6 * maxA);
    for (let i = 0, j = 0, b = 0; i < n; i++, j += 4, b += comps) {
      const al = Math.min(1, A[i] * aNorm) * maxA0;
      if (al < 1 / 510) continue;
      let lx = dxA[i] * ig * kxy;
      let ly = dyA[i] * ig * kxy;
      const ll = 1 / Math.sqrt(lx * lx + ly * ly + hz * hz);
      lx *= ll;
      ly *= ll;
      const lz = hz * ll;
      const nx = -gx[i] * rel;
      const ny = -gy[i] * rel;
      const nl = 1 / Math.sqrt(nx * nx + ny * ny + 1);
      const f = clamp(((nx * lx + ny * ly + lz) * nl) / lz, 0, 3);
      // Schatten etwas stärker aufhellen als reine Multiplikation (grobe Albedo-Schätzung)
      const lift = Math.sqrt((meanSh + 0.01) / (Sh[i] + 0.01));
      const ia = 1 / A[i];
      const g = inten * f;
      const r0 = TO_LIN[base[b]];
      const g0 = TO_LIN[base[b + 1]];
      const b0 = TO_LIN[base[b + 2]];
      const lr = cr[i] * ia * g;
      const lg = cg[i] * ia * g;
      const lb = cb[i] * ia * g;
      out[j] = enc(r0 + (r0 * lift + 0.03) * lr);
      out[j + 1] = enc(g0 + (g0 * lift + 0.03) * lg);
      out[j + 2] = enc(b0 + (b0 * lift + 0.03) * lb);
      out[j + 3] = Math.round(al * 255);
    }
    return out;
  }

  // ---------- Zerlegung in Ebenen ----------
  /** Liefert { albedo, shading, normal } als RGBA. albedo × shading (Multiplizieren) ≈ Original. */
  function decompose(an) {
    const { w, h, Yp, gx, gy, base, comps } = an;
    const n = w * h;
    const S = guided(Yp, Yp, w, h, Math.max(4, an.sigma * 4), 0.01);
    const hist = new Uint32Array(1024);
    for (let i = 0; i < n; i++) hist[Math.min(1023, (S[i] * 1023) | 0)]++;
    let acc = 0;
    let top = 1023;
    for (; top > 0; top--) {
      acc += hist[top];
      if (acc > n * 0.01) break;
    }
    const sMax = Math.max(0.05, top / 1023);
    const albedo = new Uint8Array(n * 4);
    const shading = new Uint8Array(n * 4);
    const normal = new Uint8Array(n * 4);
    for (let i = 0, j = 0, b = 0; i < n; i++, j += 4, b += comps) {
      const sp = clamp(S[i] / sMax, 0.03, 1);
      const sl = sp * sp * sp;
      const v = enc(sl);
      shading[j] = shading[j + 1] = shading[j + 2] = v;
      shading[j + 3] = 255;
      albedo[j] = enc(TO_LIN[base[b]] / sl);
      albedo[j + 1] = enc(TO_LIN[base[b + 1]] / sl);
      albedo[j + 2] = enc(TO_LIN[base[b + 2]] / sl);
      albedo[j + 3] = 255;
      const nx = -gx[i] * 1.5;
      const ny = -gy[i] * 1.5;
      const nl = 1 / Math.sqrt(nx * nx + ny * ny + 1);
      normal[j] = Math.round((nx * nl * 0.5 + 0.5) * 255);
      normal[j + 1] = Math.round((-ny * nl * 0.5 + 0.5) * 255);
      normal[j + 2] = Math.round((nl * 0.5 + 0.5) * 255);
      normal[j + 3] = 255;
    }
    return { albedo, shading, normal };
  }

  // ---------- Skalieren / Einpassen ----------
  /**
   * Bilineares Einpassen von src (sw×sh, sc Kanäle) in das Rechteck (dx,dy,dw,dh)
   * eines Zielbilds (W×H, dc Kanäle). Fehlendes Alpha wird als deckend angenommen.
   */
  function blit(src, sw, sh, sc, dst, W, H, dc, dx, dy, dw, dh) {
    if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return;
    const fx = sw / dw;
    const fy = sh / dh;
    const nc = Math.min(sc, dc);
    for (let y = Math.max(0, dy); y < Math.min(H, dy + dh); y++) {
      let sy = (y - dy + 0.5) * fy - 0.5;
      sy = sy < 0 ? 0 : sy > sh - 1 ? sh - 1 : sy;
      const y0 = sy | 0;
      const y1 = y0 < sh - 1 ? y0 + 1 : y0;
      const wy = sy - y0;
      for (let x = Math.max(0, dx); x < Math.min(W, dx + dw); x++) {
        let sx = (x - dx + 0.5) * fx - 0.5;
        sx = sx < 0 ? 0 : sx > sw - 1 ? sw - 1 : sx;
        const x0 = sx | 0;
        const x1 = x0 < sw - 1 ? x0 + 1 : x0;
        const wx = sx - x0;
        const a = (y0 * sw + x0) * sc;
        const b = (y0 * sw + x1) * sc;
        const c = (y1 * sw + x0) * sc;
        const d = (y1 * sw + x1) * sc;
        const o = (y * W + x) * dc;
        for (let k = 0; k < nc; k++) {
          const top = src[a + k] + (src[b + k] - src[a + k]) * wx;
          const bot = src[c + k] + (src[d + k] - src[c + k]) * wx;
          dst[o + k] = top + (bot - top) * wy + 0.5;
        }
        if (dc === 4 && sc < 4) dst[o + 3] = 255;
      }
    }
  }

  return { analyze, flowField, paint, recolor, light, decompose, blit, blur, guided };
});
