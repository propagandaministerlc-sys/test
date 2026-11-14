/*
 * Surface Brush – Photoshop-Anbindung (UXP). Liest Pixel über die Imaging-API,
 * rechnet mit core.js und schreibt das Ergebnis als neue Ebene zurück.
 */
const { app, core, imaging, constants, action } = require("photoshop");
const SC = require("./core.js");

const STROKE_LAYER_NAME = "Malebene (Surface Brush)";
const MODE_NAMES = { paint: "Oberflächen-Pinsel", recolor: "Umfärbung", light: "Licht" };

// Verknüpfung Malebene <-> Ergebnisebene, damit "Anwenden" wiederholt werden kann.
const resultOf = {}; // Malebenen-ID -> Ergebnis-ID
const sourceOf = {}; // Ergebnis-ID  -> Malebenen-ID

class UserError extends Error {}

function findLayer(layers, id) {
  for (const l of layers) {
    if (l.id === id) return l;
    if (l.layers && l.layers.length) {
      const r = findLayer(l.layers, id);
      if (r) return r;
    }
  }
  return null;
}

function activeDoc() {
  const doc = app.activeDocument;
  if (!doc) throw new UserError("Kein Dokument geöffnet.");
  const rgb = constants.DocumentMode && constants.DocumentMode.RGB;
  if (rgb && doc.mode !== rgb) throw new UserError("Bitte ein RGB-Dokument verwenden (Bild › Modus › RGB-Farbe).");
  return doc;
}

async function withHistory(doc, name, fn) {
  return core.executeAsModal(
    async (ctx) => {
      let token = null;
      try {
        token = await ctx.hostControl.suspendHistory({ documentID: doc.id, name });
      } catch (e) {
        token = null;
      }
      try {
        return await fn(ctx);
      } finally {
        if (token !== null) {
          try {
            await ctx.hostControl.resumeHistory(token);
          } catch (e) {
            /* ignorieren */
          }
        }
      }
    },
    { commandName: name }
  );
}

/**
 * Liest ein Rechteck (Dokument-Komposit oder einzelne Ebene) und passt es in den
 * Arbeits-Puffer dst (tw×th, comps Kanäle) ein, der das Dokument-Rechteck region abdeckt.
 */
/** Führt einen Schritt aus und nennt ihn in der Fehlermeldung, damit Fehler eingrenzbar sind. */
async function step(name, fn) {
  try {
    return await fn();
  } catch (e) {
    if (e instanceof UserError) throw e;
    throw new Error("[" + name + "] " + (e && e.message ? e.message : String(e)));
  }
}

/** Beliebige Pixeldaten (8/16/32 Bit) nach 8 Bit wandeln. */
function to8bit(data) {
  if (data instanceof Uint8Array) return data;
  const out = new Uint8Array(data.length);
  if (data instanceof Uint16Array) {
    for (let i = 0; i < data.length; i++) out[i] = Math.min(255, (data[i] * 255 + 16384) >> 15);
  } else {
    for (let i = 0; i < data.length; i++) out[i] = Math.max(0, Math.min(255, Math.round(data[i] * 255)));
  }
  return out;
}

/**
 * getPixels mit Rückfall-Varianten: Manche Photoshop-Versionen/Dokumente lehnen einzelne
 * Optionen ab (z. B. applyAlpha ohne Transparenz). Dann wird mit weniger Optionen erneut gelesen.
 */
async function getPixelsRobust(req) {
  const variants = [[], ["applyAlpha"], ["applyAlpha", "targetSize"], ["applyAlpha", "targetSize", "componentSize", "colorSpace"]];
  let first = null;
  for (const drop of variants) {
    if (drop.length && !drop.some((k) => k in req)) continue;
    const r = Object.assign({}, req);
    for (const k of drop) delete r[k];
    try {
      return await imaging.getPixels(r);
    } catch (e) {
      if (!first) first = e;
    }
  }
  throw first;
}

/**
 * Liest ein Rechteck (Dokument-Komposit oder einzelne Ebene) und passt es in den
 * Arbeits-Puffer dst (tw×th, comps Kanäle) ein, der das Dokument-Rechteck region abdeckt.
 */
async function readInto(dst, tw, th, comps, region, rect, doc, layerID) {
  const sx = tw / (region.right - region.left);
  const sy = th / (region.bottom - region.top);
  const req = { documentID: doc.id, sourceBounds: rect, colorSpace: "RGB" };
  // In 16-Bit-Dokumenten in der Original-Tiefe lesen (8 Bit anzufordern schlägt dort fehl) und selbst wandeln
  if (bitDepth(doc) === 8) req.componentSize = 8;
  if (layerID != null) req.layerID = layerID;
  else req.applyAlpha = true;
  if (sx < 0.999) {
    req.targetSize = {
      width: Math.max(1, Math.round((rect.right - rect.left) * sx)),
      height: Math.max(1, Math.round((rect.bottom - rect.top) * sy)),
    };
  }
  const res = await getPixelsRobust(req);
  const img = res.imageData;
  try {
    const data = to8bit(await img.getData({ chunky: true }));
    const sb = res.sourceBounds || rect;
    const dx = Math.round((sb.left - region.left) * sx);
    const dy = Math.round((sb.top - region.top) * sy);
    const dw = Math.max(1, Math.round((sb.right - sb.left) * sx));
    const dh = Math.max(1, Math.round((sb.bottom - sb.top) * sy));
    SC.blit(data, img.width, img.height, img.components, dst, tw, th, comps, dx, dy, dw, dh);
  } finally {
    img.dispose();
  }
}

/** Sichtbarkeit setzen; fällt auf batchPlay zurück, falls die DOM-Eigenschaft scheitert. */
async function setVisible(layer, on) {
  try {
    layer.visible = on;
  } catch (e) {
    await action.batchPlay([{ _obj: on ? "show" : "hide", null: [{ _ref: "layer", _id: layer.id }] }], {});
  }
}

function bitDepth(doc) {
  const b = String(doc.bitsPerChannel || "");
  return b.indexOf("32") >= 0 ? 32 : b.indexOf("16") >= 0 ? 16 : 8;
}

// Schreibvarianten: Welche Photoshop annimmt UND korrekt (mit Transparenz) übernimmt, hängt von
// Bit-Tiefe und Version ab. Deshalb wird je Dokument einmal mit einem Testmuster ausprobiert.
const WRITERS = {
  u8: { make: (d) => d, opts: {} },
  u16: {
    make: (d) => {
      const o = new Uint16Array(d.length);
      for (let i = 0; i < d.length; i++) o[i] = Math.round((d[i] * 32768) / 255);
      return o;
    },
    opts: {},
  },
  u16full: {
    make: (d) => {
      const o = new Uint16Array(d.length);
      for (let i = 0; i < d.length; i++) o[i] = d[i] * 257;
      return o;
    },
    opts: { fullRange: true },
  },
};
const writerOf = {}; // "Dokument-ID:Bit-Tiefe" -> Name der Schreibvariante

async function rawPut(doc, layerID, rgba, w, h, left, top, writer) {
  const img = await imaging.createImageDataFromBuffer(
    writer.make(rgba),
    Object.assign(
      { width: w, height: h, components: 4, chunky: true, colorSpace: "RGB", colorProfile: doc.colorProfileName || "sRGB IEC61966-2.1" },
      writer.opts
    )
  );
  try {
    await imaging.putPixels({ documentID: doc.id, layerID, imageData: img, targetBounds: { left, top }, replace: true });
  } finally {
    img.dispose();
  }
}

/** Probiert die Schreibvarianten auf einer Testebene aus und merkt sich die erste, die stimmt. */
async function ensureWriter(doc) {
  const bits = bitDepth(doc);
  const key = doc.id + ":" + bits;
  if (writerOf[key]) return WRITERS[writerOf[key]];
  if (bits === 32) throw new UserError("32-Bit-Dokumente werden nicht unterstützt. Bitte in 8 oder 16 Bit/Kanal umwandeln.");
  const N = 8;
  if (doc.width < N || doc.height < N) {
    writerOf[key] = bits === 16 ? "u16" : "u8";
    return WRITERS[writerOf[key]];
  }
  // Testmuster: links deckend, dann halbtransparent, rechts transparent
  const pat = new Uint8Array(N * N * 4);
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const a = x < 4 ? 255 : x < 6 ? 128 : 0;
      pat.set([200, 100, 50, a], (y * N + x) * 4);
    }
  }
  const rect = { left: 0, top: 0, right: N, bottom: N };
  const order = bits === 16 ? ["u16", "u16full", "u8"] : ["u8", "u16"];
  const report = [];
  let found = null;
  const previous = doc.activeLayers && doc.activeLayers[0];
  for (const name of order) {
    const tmp = await doc.createLayer(constants.LayerKind.NORMAL, { name: "Surface Brush Test" });
    try {
      await rawPut(doc, tmp.id, pat, N, N, 0, 0, WRITERS[name]);
      const back = new Uint8Array(N * N * 4);
      await readInto(back, N, N, 4, rect, rect, doc, tmp.id);
      const px = (x) => Array.from(back.subarray((2 * N + x) * 4, (2 * N + x) * 4 + 4));
      const o = px(1);
      const h = px(4);
      const t = px(7);
      const ok =
        o[3] > 240 && Math.abs(o[0] - 200) < 12 && Math.abs(o[1] - 100) < 12 && Math.abs(o[2] - 50) < 12 &&
        Math.abs(h[3] - 128) < 14 && t[3] < 10;
      report.push(name + ": " + (ok ? "ok" : "falsch " + JSON.stringify([o, h, t])));
      if (ok) found = name;
    } catch (e) {
      report.push(name + ": " + (e && e.message ? e.message : String(e)));
    }
    try {
      await tmp.delete();
    } catch (e) {
      /* Testebene bleibt dann stehen */
    }
    if (found) break;
  }
  if (previous) await selectLayer(previous);
  if (!found) {
    throw new UserError("Photoshop übernimmt die Pixel nicht korrekt (" + bits + " Bit). Schreibtest: " + report.join(" | "));
  }
  writerOf[key] = found;
  return WRITERS[found];
}

async function putRGBA(doc, layerID, rgba, w, h, left, top) {
  const writer = await ensureWriter(doc);
  await rawPut(doc, layerID, rgba, w, h, left, top, writer);
}

async function selectLayer(layer) {
  try {
    await action.batchPlay([{ _obj: "select", _target: [{ _ref: "layer", _id: layer.id }], makeVisible: false }], {});
  } catch (e) {
    /* nicht kritisch */
  }
}

/**
 * Pixel-Regler gelten für ein Bild mit 1500 px langer Seite und wachsen mit der Bildgröße,
 * damit die Wirkung in großen Fotos nicht verschwindet.
 */
function docParams(p, W, H) {
  const k = Math.max(1, Math.max(W, H) / 1500);
  const q = Object.assign({}, p);
  for (const key of ["wrap", "follow", "snap", "radius", "scale"]) if (q[key] != null) q[key] = q[key] * k;
  return q;
}

function histId(doc) {
  try {
    const h = doc.activeHistoryState;
    return h && h.id != null ? h.id : null;
  } catch (e) {
    return null;
  }
}

function workSize(W, H, megapixels) {
  const s = Math.min(1, Math.sqrt((megapixels * 1e6) / (W * H)));
  return { s, tw: Math.max(1, Math.round(W * s)), th: Math.max(1, Math.round(H * s)) };
}

function upscale(rgba, tw, th, W, H) {
  if (tw === W && th === H) return rgba;
  const out = new Uint8Array(W * H * 4);
  SC.blit(rgba, tw, th, 4, out, W, H, 4, 0, 0, W, H);
  return out;
}

/** Legt eine leere Malebene an und wählt das Pinsel-Werkzeug. */
async function newStrokeLayer() {
  const doc = activeDoc();
  await withHistory(doc, "Surface Brush: Malebene", async () => {
    await doc.createLayer(constants.LayerKind.NORMAL, { name: STROKE_LAYER_NAME });
    try {
      await action.batchPlay([{ _obj: "select", _target: [{ _ref: "paintbrushTool" }] }], {});
    } catch (e) {
      /* nicht kritisch */
    }
  });
}

/**
 * Wendet einen Modus auf die aktive Malebene an.
 * p: Reglerwerte in Dokument-Pixeln bzw. 0..1
 */
async function apply(mode, p) {
  const doc = activeDoc();
  let layer = doc.activeLayers && doc.activeLayers[0];
  if (!layer) throw new UserError("Bitte die Malebene auswählen.");
  if (sourceOf[layer.id] != null) {
    // Ergebnisebene ist ausgewählt -> aus der ursprünglichen Malebene neu berechnen
    const src = findLayer(doc.layers, sourceOf[layer.id]);
    if (src) layer = src;
  }
  if (layer.isBackgroundLayer) throw new UserError("Bitte nicht auf dem Hintergrund malen – zuerst »Neue Malebene« anlegen.");
  if (constants.LayerKind && layer.kind !== constants.LayerKind.NORMAL) {
    throw new UserError("Die ausgewählte Ebene ist keine Pixelebene.");
  }

  const dw = doc.width;
  const dh = doc.height;
  p = docParams(p, dw, dh);
  const b = layer.bounds;
  const lb = {
    left: Math.max(0, Math.floor(b.left)),
    top: Math.max(0, Math.floor(b.top)),
    right: Math.min(dw, Math.ceil(b.right)),
    bottom: Math.min(dh, Math.ceil(b.bottom)),
  };
  if (lb.right <= lb.left || lb.bottom <= lb.top) {
    throw new UserError("Die Malebene ist leer. Erst mit dem Pinsel malen, dann »Anwenden«.");
  }
  const margin = Math.ceil(
    mode === "paint" ? p.wrap + p.follow + 8 : mode === "recolor" ? p.snap * 3 + 8 : p.radius * 3
  );
  const region = {
    left: Math.max(0, lb.left - margin),
    top: Math.max(0, lb.top - margin),
    right: Math.min(dw, lb.right + margin),
    bottom: Math.min(dh, lb.bottom + margin),
  };
  const W = region.right - region.left;
  const H = region.bottom - region.top;
  const { s, tw, th } = workSize(W, H, p.megapixels);

  await withHistory(doc, "Surface Brush: " + MODE_NAMES[mode], async () => {
    const oldId = resultOf[layer.id];
    if (oldId != null) {
      const old = findLayer(doc.layers, oldId);
      if (old) await step("Altes Ergebnis löschen", () => old.delete());
      delete sourceOf[oldId];
      delete resultOf[layer.id];
    }

    await step("Malebene einblenden", () => setVisible(layer, true));
    const stroke = new Uint8Array(tw * th * 4);
    await step("Malebene lesen", () => readInto(stroke, tw, th, 4, region, lb, doc, layer.id));
    await step("Malebene ausblenden", () => setVisible(layer, false));
    const base = new Uint8Array(tw * th * 3);
    await step("Bild lesen", () => readInto(base, tw, th, 3, region, region, doc, null));

    let out;
    await step("Berechnung", async () => {
    const an = SC.analyze(base, tw, th, 3, Math.max(1, p.scale * s));
    if (mode === "paint") out = SC.paint(an, stroke, { wrap: p.wrap * s, follow: p.follow * s, shade: p.shade });
    else if (mode === "recolor") out = SC.recolor(an, stroke, { snap: p.snap * s, shade: p.shade, highlights: p.highlights });
    else out = SC.light(an, stroke, { radius: p.radius * s, intensity: p.intensity, height: p.height, relief: p.relief });
    out = upscale(out, tw, th, W, H);
    });

    await selectLayer(layer);
    const result = await step("Ergebnisebene anlegen", () =>
      doc.createLayer(constants.LayerKind.NORMAL, { name: MODE_NAMES[mode] + " (Surface Brush)" })
    );
    await step("Ergebnis schreiben", () => putRGBA(doc, result.id, out, W, H, region.left, region.top));
    resultOf[layer.id] = result.id;
    sourceOf[result.id] = layer.id;
  });
  return { width: tw, height: th, scale: s };
}

/** Zerlegt das sichtbare Bild in Albedo, Schattierung (Multiplizieren) und Normalen. */
async function decompose(p) {
  const doc = activeDoc();
  const W = doc.width;
  const H = doc.height;
  p = docParams(p, W, H);
  const region = { left: 0, top: 0, right: W, bottom: H };
  const { s, tw, th } = workSize(W, H, p.megapixels);
  await withHistory(doc, "Surface Brush: Zerlegung", async () => {
    const base = new Uint8Array(tw * th * 3);
    await step("Bild lesen", () => readInto(base, tw, th, 3, region, region, doc, null));
    const an = SC.analyze(base, tw, th, 3, Math.max(1, p.scale * s));
    const d = SC.decompose(an);
    const add = async (name, data, blendMode, visible) => {
      const opts = { name };
      if (blendMode) opts.blendMode = blendMode;
      const l = await step("Ebene anlegen", () => doc.createLayer(constants.LayerKind.NORMAL, opts));
      await step("Ebene schreiben", () => putRGBA(doc, l.id, upscale(data, tw, th, W, H), W, H, 0, 0));
      if (!visible) await setVisible(l, false);
    };
    await add("Normalen (Surface Brush)", d.normal, null, false);
    await add("Albedo (Surface Brush)", d.albedo, null, true);
    await add("Schattierung (Surface Brush)", d.shading, constants.BlendMode.MULTIPLY, true);
  });
  return { width: tw, height: th, scale: s };
}

// ====================== Live-Modus ======================
// Jeder fertige Pinselstrich auf der Live-Malebene wird sofort übernommen: Der Strich wird
// eingelesen, die Malebene geleert und das Ergebnis im betroffenen Bereich neu berechnet.

let live = null;

function cropBuf(src, SW, comps, r) {
  const w = r.right - r.left;
  const h = r.bottom - r.top;
  const out = new Uint8Array(w * h * comps);
  for (let y = 0; y < h; y++) {
    const o = ((r.top + y) * SW + r.left) * comps;
    out.set(src.subarray(o, o + w * comps), y * w * comps);
  }
  return out;
}
function pasteBuf(dst, SW, comps, r, data) {
  const w = r.right - r.left;
  const h = r.bottom - r.top;
  for (let y = 0; y < h; y++) {
    dst.set(data.subarray(y * w * comps, (y + 1) * w * comps), ((r.top + y) * SW + r.left) * comps);
  }
}
function scaleBuf(src, w, h, comps, tw, th) {
  if (w === tw && h === th) return src;
  const out = new Uint8Array(tw * th * comps);
  SC.blit(src, w, h, comps, out, tw, th, comps, 0, 0, tw, th);
  return out;
}
function isModalBusy(e) {
  const m = String((e && e.message) || e).toLowerCase();
  return (e && e.number === 9) || m.indexOf("modal") >= 0;
}
function marginFor(mode, p) {
  return Math.ceil(mode === "paint" ? p.wrap + p.follow + 8 : mode === "recolor" ? p.snap * 3 + 8 : p.radius * 3);
}
function modeOpts(mode, p, s) {
  if (mode === "paint") return { wrap: p.wrap * s, follow: p.follow * s, shade: p.shade };
  if (mode === "recolor") return { snap: p.snap * s, shade: p.shade, highlights: p.highlights };
  return { radius: p.radius * s, intensity: p.intensity, height: p.height, relief: p.relief };
}

function computeNorm(st, scale) {
  const d = Math.min(1, Math.sqrt(2e6 / (st.W * st.H)));
  const dw = Math.max(1, Math.round(st.W * d));
  const dh = Math.max(1, Math.round(st.H * d));
  const an = SC.analyze(scaleBuf(st.base, st.W, st.H, 3, dw, dh), dw, dh, 3, Math.max(1, scale * d));
  st.norm = an.norm / d;
  st.normScale = scale;
}

/** Berechnet das Ergebnis für rect (+Rand) aus allen bisherigen Strichen neu und schreibt es. */
async function renderRegion(st, doc, result, rect) {
  const p = st.getParams();
  if (st.normScale !== p.scale) computeNorm(st, p.scale);
  const m = marginFor(st.mode, p);
  const region = {
    left: Math.max(0, rect.left - m),
    top: Math.max(0, rect.top - m),
    right: Math.min(st.W, rect.right + m),
    bottom: Math.min(st.H, rect.bottom + m),
  };
  const W = region.right - region.left;
  const H = region.bottom - region.top;
  const { s, tw, th } = workSize(W, H, p.megapixels);
  let out;
  await step("Berechnung", async () => {
    const base = scaleBuf(cropBuf(st.base, st.W, 3, region), W, H, 3, tw, th);
    const stroke = scaleBuf(cropBuf(st.accum, st.W, 4, region), W, H, 4, tw, th);
    const an = SC.analyze(base, tw, th, 3, Math.max(1, p.scale * s), st.norm * s);
    out = upscale(SC[st.mode](an, stroke, modeOpts(st.mode, p, s)), tw, th, W, H);
  });
  await step("Ergebnis schreiben", () => putRGBA(doc, result.id, out, W, H, region.left, region.top));
  return s;
}

async function createLivePair(st, doc) {
  const r = await step("Ergebnisebene anlegen", () =>
    doc.createLayer(constants.LayerKind.NORMAL, { name: "Live " + MODE_NAMES[st.mode] + " (Surface Brush)" })
  );
  const pl = await step("Malebene anlegen", () => doc.createLayer(constants.LayerKind.NORMAL, { name: "Live-" + STROKE_LAYER_NAME }));
  st.resultId = r.id;
  st.paintId = pl.id;
  try {
    await action.batchPlay([{ _obj: "select", _target: [{ _ref: "paintbrushTool" }] }], {});
  } catch (e) {
    /* nicht kritisch */
  }
}

async function liveTick(st, fromEvent) {
  if (live !== st || st.busy) return;
  const doc = app.activeDocument;
  if (!doc || doc.id !== st.docId) return;
  // Nur nach einem abgeschlossenen Strich eingreifen (neuer Protokollschritt) – niemals mitten im Malen.
  const hid = histId(doc);
  if (hid === null ? !fromEvent : hid === st.lastHist) return;
  const paint = findLayer(doc.layers, st.paintId);
  const result = findLayer(doc.layers, st.resultId);
  if (!paint || !result) {
    await stopLive();
    st.onStatus("Live-Modus beendet: Die Live-Ebenen wurden gelöscht.", true);
    return;
  }
  const b = paint.bounds;
  const lb = {
    left: Math.max(0, Math.floor(b.left)),
    top: Math.max(0, Math.floor(b.top)),
    right: Math.min(st.W, Math.ceil(b.right)),
    bottom: Math.min(st.H, Math.ceil(b.bottom)),
  };
  if (lb.right <= lb.left || lb.bottom <= lb.top) return;
  st.busy = true;
  const t0 = Date.now();
  try {
    let scale = null;
    await withHistory(doc, "Surface Brush: Strich", async () => {
      const w = lb.right - lb.left;
      const h = lb.bottom - lb.top;
      const ns = new Uint8Array(w * h * 4);
      await step("Strich lesen", () => readInto(ns, w, h, 4, lb, lb, doc, paint.id));
      let any = false;
      for (let i = 3; i < ns.length; i += 4) {
        if (ns[i]) {
          any = true;
          break;
        }
      }
      if (!any) return;
      const prev = cropBuf(st.accum, st.W, 4, lb);
      st.undo.push({ rect: lb, data: prev });
      if (st.undo.length > 20) st.undo.shift();
      // neuen Strich über die bisherigen legen
      const merged = new Uint8Array(prev);
      for (let i = 0; i < ns.length; i += 4) {
        const a = ns[i + 3] / 255;
        if (a === 0) continue;
        const pa = merged[i + 3] / 255;
        const oa = a + pa * (1 - a);
        for (let k = 0; k < 3; k++) merged[i + k] = Math.round((ns[i + k] * a + merged[i + k] * pa * (1 - a)) / oa);
        merged[i + 3] = Math.round(oa * 255);
      }
      pasteBuf(st.accum, st.W, 4, lb, merged);
      // Malebene leeren (der rohe Strich wird durch das Ergebnis ersetzt)
      await step("Malebene leeren", () => putRGBA(doc, paint.id, new Uint8Array(w * h * 4), w, h, lb.left, lb.top));
      scale = await renderRegion(st, doc, result, lb);
    });
    if (scale !== null) {
      st.onStatus(
        "Live: Strich übernommen (" + ((Date.now() - t0) / 1000).toFixed(1) + " s)" +
          (scale < 0.999 ? ", mit " + Math.round(scale * 100) + " % Auflösung gerechnet." : "."),
        false
      );
    }
  } catch (e) {
    if (!isModalBusy(e)) {
      await stopLive();
      st.onStatus("Live-Modus beendet. Fehler: " + (e && e.message ? e.message : String(e)), true);
    }
  } finally {
    st.lastHist = histId(doc);
    st.busy = false;
  }
}

/** Startet den Live-Modus im aktiven Dokument. */
async function startLive(mode, getParams, onStatus) {
  if (live) await stopLive();
  const doc = activeDoc();
  const W = doc.width;
  const H = doc.height;
  if (W * H > 60e6) throw new UserError("Das Dokument ist für den Live-Modus zu groß (über 60 Megapixel).");
  const st = {
    docId: doc.id, W, H, mode, onStatus, lastHist: null,
    getParams: () => docParams(getParams(), W, H),
    base: new Uint8Array(W * H * 3), accum: new Uint8Array(W * H * 4),
    undo: [], busy: true, norm: 0, normScale: -1, resultId: null, paintId: null, timer: null, listener: null,
  };
  const full = { left: 0, top: 0, right: W, bottom: H };
  await withHistory(doc, "Surface Brush: Live starten", async () => {
    await step("Bild lesen", () => readInto(st.base, W, H, 3, full, full, doc, null));
    await step("Schreibtest", () => ensureWriter(doc));
    await createLivePair(st, doc);
  });
  computeNorm(st, st.getParams().scale);
  st.lastHist = histId(doc);
  st.busy = false;
  live = st;
  st.listener = () => {
    liveTick(st, true);
  };
  try {
    action.addNotificationListener(["historyStateChanged"], st.listener);
  } catch (e) {
    st.listener = null;
  }
  // Zusätzlich regelmäßig prüfen, falls das Ereignis ausbleibt
  st.timer = setInterval(() => liveTick(st, false), 300);
}

async function stopLive() {
  const st = live;
  if (!st) return;
  live = null;
  if (st.timer) clearInterval(st.timer);
  if (st.listener) {
    try {
      action.removeNotificationListener(["historyStateChanged"], st.listener);
    } catch (e) {
      /* ignorieren */
    }
  }
  try {
    const doc = app.activeDocument;
    const paint = doc && doc.id === st.docId ? findLayer(doc.layers, st.paintId) : null;
    if (paint) await withHistory(doc, "Surface Brush: Live beenden", () => paint.delete());
  } catch (e) {
    /* Malebene bleibt dann einfach stehen */
  }
}

/** Moduswechsel im Live-Modus: neues Ebenenpaar, bisheriges Ergebnis bleibt als Ebene erhalten. */
async function setLiveMode(mode) {
  const st = live;
  if (!st || st.mode === mode) return;
  const doc = app.activeDocument;
  if (!doc || doc.id !== st.docId) return;
  st.busy = true;
  try {
    await withHistory(doc, "Surface Brush: Moduswechsel", async () => {
      const paint = findLayer(doc.layers, st.paintId);
      if (paint) await paint.delete();
      st.mode = mode;
      st.accum = new Uint8Array(st.W * st.H * 4);
      st.undo = [];
      await createLivePair(st, doc);
    });
  } finally {
    st.lastHist = histId(doc);
    st.busy = false;
  }
}

/** Nimmt den letzten Live-Strich zurück. */
async function undoLive() {
  const st = live;
  if (!st) throw new UserError("Der Live-Modus läuft nicht.");
  if (!st.undo.length) throw new UserError("Kein Strich zum Zurücknehmen.");
  const doc = app.activeDocument;
  const result = doc && doc.id === st.docId ? findLayer(doc.layers, st.resultId) : null;
  if (!result) throw new UserError("Die Live-Ergebnisebene wurde nicht gefunden.");
  st.busy = true;
  try {
    const u = st.undo.pop();
    pasteBuf(st.accum, st.W, 4, u.rect, u.data);
    await withHistory(doc, "Surface Brush: Strich zurück", () => renderRegion(st, doc, result, u.rect));
  } finally {
    st.lastHist = histId(doc);
    st.busy = false;
  }
}

function isLive() {
  return !!live;
}

module.exports = { apply, decompose, newStrokeLayer, UserError, startLive, stopLive, setLiveMode, undoLive, isLive, _tick: () => (live ? liveTick(live, true) : null) };
