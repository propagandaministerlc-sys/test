/*
 * Surface Brush – Claude-Anbindung.
 *
 * Claude sieht eine verkleinerte Fassung des Bildes, bekommt die Anweisung des Nutzers und
 * antwortet mit einer Liste von Aktionen (Modus, Farbe, Bereich als Polygone). Das Plugin
 * rastert die Polygone zu "Strichen" und führt sie mit den normalen Algorithmen aus.
 *
 * Hinweis: UXP ist weder Node noch Browser, das offizielle Anthropic-SDK läuft dort nicht.
 * Deshalb wird die Messages-API hier direkt per fetch angesprochen.
 */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.SurfaceClaude = factory();
})(this, function () {
  "use strict";

  const API_URL = "https://api.anthropic.com/v1/messages";
  const MODEL = "claude-opus-5-5";
  const GRID = 1000; // Koordinaten: 0..1000 in beiden Richtungen, unabhängig von der Bildgröße

  const SYSTEM = [
    "Du steuerst ein Photoshop-Plugin namens Surface Brush. Du bekommst ein Foto und eine Anweisung.",
    "Antworte mit Aktionen, die das Plugin auf dem Bild ausführt. Das Plugin kann genau drei Dinge:",
    "",
    "- recolor: färbt einen Bereich in einer neuen Farbe um; Schatten, Falten und Glanzlichter des",
    "  Originals bleiben erhalten. Gib den Bereich als Polygon an, das dem Umriss des Objekts folgt.",
    "  Das Plugin rastet die Kante selbst an Objektkanten ein, bleib im Zweifel knapp innerhalb.",
    "- paint: malt deckende Farbe, die sich an die Oberfläche anschmiegt und deren Schatten annimmt.",
    "  Gib die gemalte Form als Polygon an (z. B. ein Streifen, ein Fleck, ein Muster aus mehreren Polygonen).",
    "- light: setzt eine Lichtquelle. Gib ein kleines Polygon an der Stelle an, wo das Licht am hellsten",
    "  sein soll; es strahlt weich in die Umgebung. color ist die Lichtfarbe, size die Ausbreitung.",
    "",
    "Koordinaten: x und y sind ganze Zahlen von 0 bis " + GRID + ". (0,0) ist die linke obere Ecke des Bildes,",
    "(" + GRID + "," + GRID + ") die rechte untere – unabhängig vom Seitenverhältnis.",
    "Polygone: 3 bis etwa 60 Punkte, im Uhrzeigersinn oder dagegen, nicht selbstüberschneidend.",
    "Besteht ein Objekt aus mehreren getrennten sichtbaren Teilen, gib mehrere Polygone in derselben Aktion an.",
    "Spare Bereiche aus, die nicht gemeint sind (z. B. Hände vor einem Kleidungsstück), indem du die Polygone darum herumführst.",
    "color: Hex-Farbe im Format #rrggbb.",
    "size: nur für light, Ausbreitung des Lichts in denselben Einheiten (typisch 40 bis 200); sonst 0.",
    "strength: 0 bis 100. recolor/paint: wie stark die Original-Schattierung übernommen wird (meist 90 bis 100).",
    "light: Helligkeit (meist 60 bis 150).",
    "",
    "Führe nur aus, was verlangt ist. Wenn die Anweisung mit diesen drei Fähigkeiten nicht umsetzbar ist",
    "oder das Gemeinte im Bild nicht zu finden ist, gib eine leere Aktionsliste zurück und erkläre es in summary.",
    "summary: ein bis zwei kurze Sätze auf Deutsch, was du gemacht hast.",
  ].join("\n");

  const SCHEMA = {
    type: "object",
    additionalProperties: false,
    required: ["summary", "actions"],
    properties: {
      summary: { type: "string" },
      actions: {
        type: "array",
        items: {
          type: "object",
          additionalProperties: false,
          required: ["mode", "color", "polygons", "size", "strength"],
          properties: {
            mode: { type: "string", enum: ["recolor", "paint", "light"] },
            color: { type: "string" },
            polygons: {
              type: "array",
              items: {
                type: "array",
                items: {
                  type: "object",
                  additionalProperties: false,
                  required: ["x", "y"],
                  properties: { x: { type: "integer" }, y: { type: "integer" } },
                },
              },
            },
            size: { type: "integer" },
            strength: { type: "integer" },
          },
        },
      },
    },
  };

  function buildRequest(jpegBase64, instruction) {
    return {
      model: MODEL,
      max_tokens: 16000,
      system: SYSTEM,
      // Bei einer Ablehnung durch die Sicherheitsfilter automatisch auf ein Ersatzmodell ausweichen
      fallbacks: "default",
      output_config: { effort: "medium", format: { type: "json_schema", schema: SCHEMA } },
      messages: [
        {
          role: "user",
          content: [
            { type: "image", source: { type: "base64", media_type: "image/jpeg", data: jpegBase64 } },
            { type: "text", text: "Anweisung: " + instruction },
          ],
        },
      ],
    };
  }

  class ClaudeError extends Error {}

  function parseColor(hex) {
    const m = /^#?([0-9a-f]{6})$/i.exec(String(hex || "").trim());
    if (!m) return null;
    const v = parseInt(m[1], 16);
    return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
  }

  /** Prüft und bereinigt Claudes Antwort; verwirft unbrauchbare Aktionen statt zu raten. */
  function validatePlan(plan) {
    if (!plan || typeof plan !== "object" || !Array.isArray(plan.actions)) {
      throw new ClaudeError("Claude hat keine gültige Aktionsliste geliefert.");
    }
    const actions = [];
    for (const a of plan.actions) {
      if (!a || ["recolor", "paint", "light"].indexOf(a.mode) < 0) continue;
      const color = parseColor(a.color);
      if (!color || !Array.isArray(a.polygons)) continue;
      const polys = [];
      for (const p of a.polygons) {
        if (!Array.isArray(p)) continue;
        const pts = [];
        for (const q of p) {
          if (!q || !isFinite(q.x) || !isFinite(q.y)) continue;
          pts.push({ x: Math.max(0, Math.min(GRID, Number(q.x))), y: Math.max(0, Math.min(GRID, Number(q.y))) });
        }
        if (pts.length >= 3) polys.push(pts);
      }
      if (!polys.length) continue;
      actions.push({
        mode: a.mode,
        color,
        polygons: polys,
        size: Math.max(0, Math.min(GRID, Number(a.size) || 0)),
        strength: Math.max(0, Math.min(300, isFinite(a.strength) ? Number(a.strength) : 100)),
      });
    }
    return { summary: String(plan.summary || ""), actions };
  }

  /**
   * Fragt Claude. fetchFn: fetch-kompatible Funktion (in UXP das globale fetch).
   * Liefert { summary, actions } (bereits geprüft).
   */
  async function askClaude(fetchFn, apiKey, jpegBase64, instruction) {
    if (!apiKey) throw new ClaudeError("Bitte zuerst einen Anthropic-API-Schlüssel eintragen.");
    if (!instruction || !instruction.trim()) throw new ClaudeError("Bitte beschreiben, was Claude tun soll.");
    const body = JSON.stringify(buildRequest(jpegBase64, instruction.trim()));
    let res = null;
    let lastErr = null;
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        res = await fetchFn(API_URL, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-api-key": apiKey,
            "anthropic-version": "2026-10-21",
            "anthropic-beta": "server-side-fallback-2026-07-01",
            "anthropic-dangerous-direct-browser-access": "true",
          },
          body,
        });
      } catch (e) {
        lastErr = e;
        res = null;
        continue; // Netzwerkfehler: einmal wiederholen
      }
      if (res.status === 429 || res.status >= 500) {
        lastErr = null;
        if (attempt === 0) {
          await new Promise((r) => setTimeout(r, 1500));
          continue;
        }
      }
      break;
    }
    if (!res) {
      throw new ClaudeError("Keine Verbindung zu Claude (" + (lastErr && lastErr.message ? lastErr.message : "Netzwerkfehler") + ").");
    }
    let data = null;
    try {
      data = await res.json();
    } catch (e) {
      data = null;
    }
    if (!res.ok) {
      const detail = data && data.error && data.error.message ? data.error.message : "HTTP " + res.status;
      if (res.status === 401) throw new ClaudeError("Der API-Schlüssel wurde abgelehnt. Bitte prüfen. (" + detail + ")");
      if (res.status === 429) throw new ClaudeError("Zu viele Anfragen oder Kontingent erschöpft. (" + detail + ")");
      if (res.status >= 500) throw new ClaudeError("Claude ist gerade nicht erreichbar. Bitte später erneut versuchen. (" + detail + ")");
      throw new ClaudeError("Claude hat die Anfrage abgelehnt: " + detail);
    }
    if (!data) throw new ClaudeError("Unlesbare Antwort von Claude.");
    if (data.stop_reason === "refusal") throw new ClaudeError("Claude hat diese Anfrage abgelehnt.");
    if (data.stop_reason === "max_tokens") throw new ClaudeError("Claudes Antwort war zu lang und wurde abgeschnitten. Bitte die Anweisung vereinfachen.");
    const block = (data.content || []).find((b) => b.type === "text");
    if (!block) throw new ClaudeError("Claude hat keinen Text geliefert.");
    let plan;
    try {
      plan = JSON.parse(block.text);
    } catch (e) {
      throw new ClaudeError("Claudes Antwort war kein gültiges JSON.");
    }
    return validatePlan(plan);
  }

  /** Füllt Polygone (Koordinaten 0..GRID) in ein RGBA-Bild w×h. Gerade-ungerade-Regel je Polygon. */
  function rasterize(polygons, color, w, h) {
    const out = new Uint8Array(w * h * 4);
    for (const poly of polygons) {
      const n = poly.length;
      const xs = poly.map((p) => (p.x / GRID) * w);
      const ys = poly.map((p) => (p.y / GRID) * h);
      let y0 = h;
      let y1 = 0;
      for (const y of ys) {
        if (y < y0) y0 = y;
        if (y > y1) y1 = y;
      }
      const ya = Math.max(0, Math.floor(y0));
      const yb = Math.min(h - 1, Math.ceil(y1));
      for (let y = ya; y <= yb; y++) {
        const sy = y + 0.5;
        const hits = [];
        for (let i = 0, j = n - 1; i < n; j = i++) {
          if (ys[i] > sy !== ys[j] > sy) hits.push(xs[i] + ((sy - ys[i]) / (ys[j] - ys[i])) * (xs[j] - xs[i]));
        }
        hits.sort((a, b) => a - b);
        for (let k = 0; k + 1 < hits.length; k += 2) {
          const xa = Math.max(0, Math.round(hits[k]));
          const xb = Math.min(w, Math.round(hits[k + 1]));
          for (let x = xa; x < xb; x++) {
            const o = (y * w + x) * 4;
            out[o] = color[0];
            out[o + 1] = color[1];
            out[o + 2] = color[2];
            out[o + 3] = 255;
          }
        }
      }
    }
    return out;
  }

  return { askClaude, buildRequest, validatePlan, rasterize, parseColor, ClaudeError, MODEL, GRID };
});
