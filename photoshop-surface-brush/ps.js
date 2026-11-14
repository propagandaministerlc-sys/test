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
async function readInto(dst, tw, th, comps, region, rect, doc, layerID) {
  const sx = tw / (region.right - region.left);
  const sy = th / (region.bottom - region.top);
  const req = { documentID: doc.id, sourceBounds: rect, colorSpace: "RGB", componentSize: 8 };
  if (layerID != null) req.layerID = layerID;
  else req.applyAlpha = true;
  if (sx < 0.999) {
    req.targetSize = {
      width: Math.max(1, Math.round((rect.right - rect.left) * sx)),
      height: Math.max(1, Math.round((rect.bottom - rect.top) * sy)),
    };
  }
  const res = await imaging.getPixels(req);
  const img = res.imageData;
  try {
    const data = await img.getData({ chunky: true });
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

async function putRGBA(doc, layerID, rgba, w, h, left, top) {
  const base = {
    width: w,
    height: h,
    components: 4,
    chunky: true,
    colorSpace: "RGB",
    colorProfile: doc.colorProfileName || "sRGB IEC61966-2.1",
  };
  const put = async (buf, extra) => {
    const img = await imaging.createImageDataFromBuffer(buf, Object.assign({}, base, extra));
    try {
      await imaging.putPixels({ documentID: doc.id, layerID, imageData: img, targetBounds: { left, top }, replace: true });
    } finally {
      img.dispose();
    }
  };
  try {
    await put(rgba, {});
  } catch (e8) {
    // 16-Bit-Dokumente: Photoshop rechnet intern mit 0..32768
    let buf16;
    try {
      buf16 = new Uint16Array(rgba.length);
      for (let i = 0; i < rgba.length; i++) buf16[i] = Math.round((rgba[i] * 32768) / 255);
      await put(buf16, { componentSize: 16 });
    } catch (e16) {
      throw new UserError(
        "Photoshop hat die Pixel nicht angenommen (" + (e8 && e8.message) + "). Bitte das Dokument in 8 Bit/Kanal testen."
      );
    }
  }
}

async function selectLayer(layer) {
  try {
    await action.batchPlay([{ _obj: "select", _target: [{ _ref: "layer", _id: layer.id }], makeVisible: false }], {});
  } catch (e) {
    /* nicht kritisch */
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
      if (old) await old.delete();
      delete sourceOf[oldId];
      delete resultOf[layer.id];
    }

    layer.visible = true;
    const stroke = new Uint8Array(tw * th * 4);
    await readInto(stroke, tw, th, 4, region, lb, doc, layer.id);
    layer.visible = false;
    const base = new Uint8Array(tw * th * 3);
    await readInto(base, tw, th, 3, region, region, doc, null);

    const an = SC.analyze(base, tw, th, 3, Math.max(1, p.scale * s));
    let out;
    if (mode === "paint") out = SC.paint(an, stroke, { wrap: p.wrap * s, follow: p.follow * s, shade: p.shade });
    else if (mode === "recolor") out = SC.recolor(an, stroke, { snap: p.snap * s, shade: p.shade, highlights: p.highlights });
    else out = SC.light(an, stroke, { radius: p.radius * s, intensity: p.intensity, height: p.height, relief: p.relief });
    out = upscale(out, tw, th, W, H);

    await selectLayer(layer);
    const result = await doc.createLayer(constants.LayerKind.NORMAL, { name: MODE_NAMES[mode] + " (Surface Brush)" });
    await putRGBA(doc, result.id, out, W, H, region.left, region.top);
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
  const region = { left: 0, top: 0, right: W, bottom: H };
  const { s, tw, th } = workSize(W, H, p.megapixels);
  await withHistory(doc, "Surface Brush: Zerlegung", async () => {
    const base = new Uint8Array(tw * th * 3);
    await readInto(base, tw, th, 3, region, region, doc, null);
    const an = SC.analyze(base, tw, th, 3, Math.max(1, p.scale * s));
    const d = SC.decompose(an);
    const add = async (name, data, blendMode, visible) => {
      const opts = { name };
      if (blendMode) opts.blendMode = blendMode;
      const l = await doc.createLayer(constants.LayerKind.NORMAL, opts);
      await putRGBA(doc, l.id, upscale(data, tw, th, W, H), W, H, 0, 0);
      if (!visible) l.visible = false;
    };
    await add("Normalen (Surface Brush)", d.normal, null, false);
    await add("Albedo (Surface Brush)", d.albedo, null, true);
    await add("Schattierung (Surface Brush)", d.shading, constants.BlendMode.MULTIPLY, true);
  });
  return { width: tw, height: th, scale: s };
}

module.exports = { apply, decompose, newStrokeLayer, UserError };
