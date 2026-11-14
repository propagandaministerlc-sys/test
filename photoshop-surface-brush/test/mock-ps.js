// Prüft ps.js gegen eine Attrappe der Photoshop-API: node test/mock-ps.js
// (Ersetzt keinen Test in echtem Photoshop – prüft Ablauf, Koordinaten und Skalierung.)
const Module = require("module");

const BITS = Number(process.env.BITS || 8); // BITS=16 simuliert ein 16-Bit-Dokument
const DW = 900;
const DH = 600;
let nextId = 10;
const calls = [];

function makeLayer(name, bounds, fill) {
  return { id: nextId++, name, bounds, fill, visible: true, kind: "pixel", isBackgroundLayer: false, layers: [], pixels: null };
}
const doc = {
  id: 1,
  width: DW,
  height: DH,
  mode: "RGBColorMode",
  activeHistoryState: { id: 1 },
  bitsPerChannel: BITS === 16 ? "bitDepth16" : "bitDepth8",
  colorProfileName: "sRGB IEC61966-2.1",
  layers: [],
  activeLayers: [],
  async createLayer(kind, opts) {
    const l = makeLayer(opts.name, { left: 0, top: 0, right: 0, bottom: 0 }, null);
    l.blendMode = opts.blendMode;
    l.delete = async () => {
      doc.layers = doc.layers.filter((x) => x !== l);
    };
    doc.layers.unshift(l);
    doc.activeLayers = [l];
    return l;
  },
};

const imaging = {
  async getPixels(req) {
    calls.push(req);
    if (BITS === 16 && req.componentSize === 8) throw new Error("componentSize 8 im 16-Bit-Dokument abgelehnt (Attrappe)");
    if (req.applyAlpha) throw new Error("applyAlpha abgelehnt (Attrappe)"); // Rückfall muss greifen
    let sb = req.sourceBounds;
    let comps = 3;
    let color = (x, y) => {
      const v = 120 + 80 * Math.sin((x + y) / 25);
      return [v, v, v, 255];
    };
    if (req.layerID != null) {
      const l = doc.layers.find((x) => x.id === req.layerID);
      comps = 4;
      // wie Photoshop: auf die Ebenengrenzen beschnitten
      sb = {
        left: Math.max(sb.left, l.bounds.left),
        top: Math.max(sb.top, l.bounds.top),
        right: Math.min(sb.right, l.bounds.right),
        bottom: Math.min(sb.bottom, l.bounds.bottom),
      };
      color = () => l.fill;
      if (!l.fill && l.canvas) {
        sb = req.sourceBounds;
        color = (x, y) => Array.from(l.canvas.subarray(((y | 0) * DW + (x | 0)) * 4, ((y | 0) * DW + (x | 0)) * 4 + 4));
      }
    } else if (doc.layers.some((l) => l.fill && l.visible)) {
      throw new Error("Komposit gelesen, obwohl die Malebene noch sichtbar ist");
    }
    const fw = sb.right - sb.left;
    const fh = sb.bottom - sb.top;
    const w = req.targetSize ? req.targetSize.width : fw;
    const h = req.targetSize ? req.targetSize.height : fh;
    const data = new Uint8Array(w * h * comps);
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const c = color(sb.left + (x * fw) / w, sb.top + (y * fh) / h);
        for (let k = 0; k < comps; k++) data[(y * w + x) * comps + k] = c[k];
      }
    }
    let outData = data;
    if (BITS === 16) outData = Uint16Array.from(data, (v) => Math.round((v * 32768) / 255));
    return { sourceBounds: sb, imageData: { width: w, height: h, components: comps, getData: async () => outData, dispose() {} } };
  },
  async createImageDataFromBuffer(buf, o) {
    if (o.width * o.height * o.components !== buf.length) throw new Error("Puffergröße passt nicht");
    return { buf, o, dispose() {} };
  },
  async putPixels(o) {
    const l = doc.layers.find((x) => x.id === o.layerID);
    if (BITS === 16) {
      const src = o.imageData.buf;
      const conv = new Uint8Array(src.length);
      if (src instanceof Uint16Array && !o.imageData.o.fullRange) {
        for (let i = 0; i < src.length; i++) conv[i] = Math.round((src[i] * 255) / 32768);
      } else {
        // 8-Bit-Daten im 16-Bit-Dokument: Transparenz geht verloren (wie beim schwarzen Rechteck beobachtet)
        for (let i = 0; i < src.length; i++) conv[i] = i % 4 === 3 ? 255 : src[i];
      }
      o.imageData.buf = conv;
    }
    if (l.fill && !o.imageData.buf.some((v) => v)) {
      // Leeren der Malebene: wie in Photoshop schrumpfen die Grenzen auf null
      l.fill = null;
      l.bounds = { left: 0, top: 0, right: 0, bottom: 0 };
      return;
    }
    l.pixels = { data: o.imageData.buf, w: o.imageData.o.width, h: o.imageData.o.height, left: o.targetBounds.left, top: o.targetBounds.top };
    // wie Photoshop: nur das geschriebene Rechteck ändert sich, der Rest der Ebene bleibt
    if (!l.canvas) l.canvas = new Uint8Array(DW * DH * 4);
    const q = l.pixels;
    for (let y = 0; y < q.h; y++) l.canvas.set(q.data.subarray(y * q.w * 4, (y + 1) * q.w * 4), ((q.top + y) * DW + q.left) * 4);
  },
};

const photoshop = {
  app: { activeDocument: doc },
  core: {
    executeAsModal: (fn) =>
      fn({ hostControl: { suspendHistory: async () => 1, resumeHistory: async () => {} } }),
  },
  imaging,
  action: { batchPlay: async () => [], addNotificationListener() {}, removeNotificationListener() {} },
  constants: {
    DocumentMode: { RGB: "RGBColorMode" },
    LayerKind: { NORMAL: "pixel" },
    BlendMode: { MULTIPLY: "multiply" },
  },
};

const origLoad = Module._load;
Module._load = function (request, ...rest) {
  if (request === "photoshop") return photoshop;
  return origLoad.call(this, request, ...rest);
};
const ps = require("../ps.js");

const assert = (c, m) => {
  if (!c) throw new Error("FEHLER: " + m);
  console.log("ok  ", m);
};
const alphaAt = (l, x, y) => (l.canvas ? l.canvas[(y * DW + x) * 4 + 3] : 0);

(async () => {
  // Malebene: rotes Rechteck 300..500 × 200..320
  const strokes = makeLayer("Malebene", { left: 300, top: 200, right: 500, bottom: 320 }, [220, 30, 30, 255]);
  doc.layers.unshift(strokes);
  doc.activeLayers = [strokes];

  for (const mp of [16, 0.02]) {
    const label = mp > 1 ? "volle Auflösung" : "verkleinert gerechnet";
    const info = await ps.apply("recolor", { scale: 5, megapixels: mp, snap: 0, shade: 1, highlights: 0.5 });
    const res = doc.layers[0];
    assert(res !== strokes && res.pixels, `Umfärben (${label}): Ergebnisebene geschrieben`);
    assert(mp > 1 ? info.scale === 1 : info.scale < 1, `Umfärben (${label}): Skalierung ${info.scale.toFixed(2)}`);
    assert(alphaAt(res, 400, 260) > 240, `Umfärben (${label}): Mitte des Strichs deckend`);
    assert(alphaAt(res, 290, 260) < 40 && alphaAt(res, 400, 190) < 40, `Umfärben (${label}): außerhalb transparent`);
    assert(alphaAt(res, 302, 202) > 100 && alphaAt(res, 497, 317) > 100, `Umfärben (${label}): Position stimmt an den Ecken`);
    assert(strokes.visible === false, "Malebene wurde ausgeblendet");
    assert(doc.layers.length === 2, "altes Ergebnis wurde ersetzt, nicht gestapelt");
    doc.activeLayers = [res]; // Ergebnis ausgewählt -> erneutes Anwenden nutzt die Malebene
  }

  await ps.apply("paint", { scale: 5, megapixels: 16, wrap: 10, follow: 10, shade: 0.9 });
  assert(alphaAt(doc.layers[0], 400, 260) > 200, "Pinsel: Ergebnis geschrieben");
  doc.activeLayers = [doc.layers[0]];
  await ps.apply("light", { scale: 5, megapixels: 16, radius: 40, intensity: 1, height: 0.4, relief: 0.7 });
  assert(alphaAt(doc.layers[0], 400, 260) > 200 && alphaAt(doc.layers[0], 560, 260) > 0, "Licht: strahlt über den Strich hinaus");

  const before = doc.layers.length;
  await ps.decompose({ scale: 5, megapixels: 0.1 });
  assert(doc.layers.length === before + 3, "Zerlegung: drei Ebenen");
  assert(doc.layers[0].blendMode === "multiply" && doc.layers[0].pixels.w === DW, "Zerlegung: Schattierung multipliziert, volle Größe");

  const empty = makeLayer("leer", { left: 0, top: 0, right: 0, bottom: 0 }, null);
  doc.layers.unshift(empty);
  doc.activeLayers = [empty];
  let msg = "";
  try {
    await ps.apply("paint", { scale: 5, megapixels: 4, wrap: 1, follow: 1, shade: 1 });
  } catch (e) {
    msg = e instanceof ps.UserError ? e.message : "";
  }
  assert(/leer/.test(msg), "leere Malebene ergibt verständliche Meldung");
  // ---------- Live-Modus ----------
  doc.layers = [];
  const P = { scale: 5, megapixels: 3, snap: 0, shade: 1, highlights: 0.5, wrap: 6, follow: 6, radius: 30, intensity: 1, height: 0.4, relief: 0.7 };
  const msgs = [];
  await ps.startLive("recolor", () => P, (t, err) => msgs.push([t, err]));
  assert(ps.isLive() && doc.layers.length === 2, "Live: Ergebnis- und Malebene angelegt");
  const paintL = doc.layers[0];
  const resL = doc.layers[1];
  const strokeOn = (l, b, c) => {
    l.bounds = b;
    l.fill = c;
    doc.activeHistoryState = { id: (doc.activeHistoryState ? doc.activeHistoryState.id : 0) + 1 }; // Strich fertig
  };
  // Mitten im Strich (Ebene hat schon Pixel, aber kein neuer Protokollschritt): nicht eingreifen
  paintL.bounds = { left: 100, top: 100, right: 130, bottom: 130 };
  paintL.fill = [30, 60, 220, 255];
  await ps._tick();
  assert(paintL.fill !== null && !resL.pixels, "Live: kein Eingriff mitten im Strich");
  strokeOn(paintL, { left: 100, top: 100, right: 220, bottom: 140 }, [30, 60, 220, 255]);
  await ps._tick();
  assert(resL.pixels && alphaAt(resL, 160, 120) > 240, "Live: erster Strich im Ergebnis");
  assert(paintL.fill === null, "Live: Malebene nach dem Strich geleert");
  assert(!msgs.some((m) => m[1]), "Live: keine Fehlermeldung");
  // zweiter Strich weiter rechts: Ergebnis wird nur im betroffenen Bereich geschrieben
  strokeOn(paintL, { left: 600, top: 400, right: 700, bottom: 430 }, [220, 30, 30, 255]);
  await ps._tick();
  assert(alphaAt(resL, 650, 415) > 240 && resL.pixels.left >= 560, "Live: zweiter Strich, nur Teilbereich neu geschrieben");
  // überlappender Strich: der alte bleibt im Bereich erhalten (wird aus dem Gesamtstand neu berechnet)
  strokeOn(paintL, { left: 680, top: 400, right: 760, bottom: 430 }, [30, 200, 30, 255]);
  await ps._tick();
  assert(alphaAt(resL, 650, 415) > 240 && alphaAt(resL, 740, 415) > 240, "Live: überlappende Striche bleiben beide erhalten");
  await ps.undoLive();
  assert(alphaAt(resL, 740, 415) < 40 && alphaAt(resL, 650, 415) > 240, "Live: »Strich zurück« entfernt nur den letzten");
  await ps.setLiveMode("paint");
  assert(doc.layers.length === 3 && !doc.layers.includes(paintL), "Live: Moduswechsel legt neues Ebenenpaar an");
  await ps.stopLive();
  assert(!ps.isLive() && doc.layers.length === 2, "Live: Beenden entfernt die Malebene");
  console.log("alle Prüfungen bestanden");
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
