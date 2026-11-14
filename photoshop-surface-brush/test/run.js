// Testlauf der Kern-Algorithmen ohne Photoshop: node test/run.js <ausgabeordner>
// Erzeugt ein synthetisches "zerknittertes Tuch", malt Striche darauf und schreibt
// die Ergebnisse als rohe RGBA-Dateien (plus meta.json) in den Ausgabeordner.
const fs = require("fs");
const path = require("path");
const core = require("../core.js");

const out = process.argv[2] || path.join(__dirname, "out");
fs.mkdirSync(out, { recursive: true });
const W = 640;
const H = 480;

// --- synthetisches Tuch: diagonale, leicht gewellte Falten, von links oben beleuchtet ---
function height(x, y) {
  const u = (x * 0.8 + y * 0.6) / 38 + Math.sin(y / 70) * 1.3;
  const v = (x * -0.5 + y * 0.9) / 150;
  return Math.sin(u) * 9 + Math.sin(u * 0.37 + v * 5) * 14 + Math.sin(v * 9 + x / 90) * 5;
}
const base = new Uint8Array(W * H * 3);
const enc = (v) => Math.round(255 * Math.pow(Math.min(1, Math.max(0, v)), 1 / 2.2));
for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) {
    const dx = (height(x + 1, y) - height(x - 1, y)) / 2;
    const dy = (height(x, y + 1) - height(x, y - 1)) / 2;
    const nl = 1 / Math.sqrt(dx * dx + dy * dy + 1);
    const s = Math.max(0.04, (dx * 0.55 + dy * 0.45 + 0.7) * nl) * 0.95 + 0.05;
    const i = (y * W + x) * 3;
    // rechte Hälfte: dunklerer Stoff, um die Flächen-Referenz zu prüfen
    const alb = x > W * 0.72 ? [0.1, 0.1, 0.12] : [0.62, 0.6, 0.55];
    base[i] = enc(alb[0] * s);
    base[i + 1] = enc(alb[1] * s);
    base[i + 2] = enc(alb[2] * s);
  }
}

// --- Striche ---
function strokes(segs) {
  const s = new Uint8Array(W * H * 4);
  for (const g of segs) {
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const vx = g.x1 - g.x0;
        const vy = g.y1 - g.y0;
        const t = Math.max(0, Math.min(1, ((x - g.x0) * vx + (y - g.y0) * vy) / (vx * vx + vy * vy || 1)));
        const d = Math.hypot(x - g.x0 - vx * t, y - g.y0 - vy * t);
        const a = Math.max(0, Math.min(1, (g.r - d) / (g.soft || 1.5)));
        if (a <= 0) continue;
        const i = (y * W + x) * 4;
        const na = a + (s[i + 3] / 255) * (1 - a);
        for (let k = 0; k < 3; k++) s[i + k] = Math.round((g.c[k] * a + s[i + k] * (s[i + 3] / 255) * (1 - a)) / na);
        s[i + 3] = Math.round(na * 255);
      }
    }
  }
  return s;
}

const t0 = Date.now();
const an = core.analyze(base, W, H, 3, 5);
const results = {};
results.base = (() => {
  const b = new Uint8Array(W * H * 4);
  core.blit(base, W, H, 3, b, W, H, 4, 0, 0, W, H);
  return b;
})();

const sPaint = strokes([
  { x0: 60, y0: 90, x1: 580, y1: 90, r: 9, c: [220, 40, 40] },
  { x0: 60, y0: 240, x1: 580, y1: 240, r: 9, c: [30, 90, 220] },
  { x0: 320, y0: 30, x1: 320, y1: 450, r: 7, c: [250, 210, 40] },
]);
results.paint_in = sPaint;
results.paint = core.paint(an, sPaint, { wrap: 14, follow: 16, shade: 0.9 });

const sRec = strokes([
  { x0: 150, y0: 150, x1: 380, y1: 330, r: 80, c: [200, 30, 60], soft: 8 },
  { x0: 560, y0: 120, x1: 560, y1: 360, r: 45, c: [240, 240, 240], soft: 8 },
]);
results.recolor_in = sRec;
results.recolor = core.recolor(an, sRec, { snap: 8, shade: 1, highlights: 0.5 });

const sLight = strokes([{ x0: 300, y0: 230, x1: 340, y1: 250, r: 14, c: [255, 190, 110], soft: 6 }]);
results.light_in = sLight;
results.light = core.light(an, sLight, { radius: 60, intensity: 1.5, height: 0.35, relief: 0.8 });

const d = core.decompose(an);
results.albedo = d.albedo;
results.shading = d.shading;
results.normal = d.normal;
console.log("Rechenzeit", Date.now() - t0, "ms");

// --- einfache Plausibilitätsprüfungen ---
const assert = (c, m) => {
  if (!c) throw new Error("FEHLER: " + m);
  console.log("ok  ", m);
};
const px = (img, x, y) => Array.from(img.subarray((y * W + x) * 4, (y * W + x) * 4 + 4));
const cover = (img) => {
  let n = 0;
  for (let i = 3; i < img.length; i += 4) if (img[i] > 20) n++;
  return n;
};
assert(cover(results.paint) > 0.5 * cover(sPaint), "Pinsel: Ergebnis enthält Farbe");
{
  // Die Schattierung muss in den Strich übernommen werden: Helligkeit variiert entlang des Strichs.
  let lo = 255;
  let hi = 0;
  for (let i = 0; i < W * H * 4; i += 4) {
    if (results.recolor[i + 3] < 250 || i / 4 % W > W * 0.6) continue;
    lo = Math.min(lo, results.recolor[i]);
    hi = Math.max(hi, results.recolor[i]);
  }
  assert(hi - lo > 60, `Umfärben: Schatten bleiben erhalten (Rot-Kanal ${lo}..${hi})`);
}
{
  // Weiß auf dunklem Stoff muss hell werden (Referenz je Fläche), nicht dunkel bleiben.
  let sum = 0;
  let cnt = 0;
  for (let y = 150; y < 330; y++) {
    const p = px(results.recolor, 560, y);
    if (p[3] > 250) {
      sum += p[1];
      cnt++;
    }
  }
  assert(cnt > 50 && sum / cnt > 140, `Umfärben: dunkler Stoff wird wirklich weiß (Mittel ${Math.round(sum / cnt)})`);
}
{
  const c = px(results.light, 320, 240);
  const b = px(results.base, 320, 240);
  const far = px(results.light, 20, 20);
  assert(c[3] > 200 && c[0] > b[0], "Licht: Mitte wird heller");
  assert(far[3] === 0, "Licht: fern vom Strich bleibt das Bild unberührt");
}
{
  // Zerlegung: Albedo × Schattierung ≈ Original
  let err = 0;
  for (let i = 0; i < W * H * 4; i += 4) err += Math.abs((d.albedo[i + 1] * d.shading[i + 1]) / 255 - results.base[i + 1]);
  err /= W * H;
  assert(err < 6, `Zerlegung: Albedo × Schattierung ≈ Original (mittlerer Fehler ${err.toFixed(2)})`);
}
{
  const dst = new Uint8Array(320 * 240 * 4);
  core.blit(results.base, W, H, 4, dst, 320, 240, 4, 0, 0, 320, 240);
  assert(Math.abs(dst[(120 * 320 + 160) * 4] - results.base[(240 * W + 320) * 4]) < 12, "Skalieren funktioniert");
}

for (const [k, v] of Object.entries(results)) fs.writeFileSync(path.join(out, k + ".rgba"), v);
fs.writeFileSync(path.join(out, "meta.json"), JSON.stringify({ w: W, h: H, names: Object.keys(results) }));
console.log("geschrieben nach", out);
