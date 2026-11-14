/* Surface Brush – Panel-Logik */
const ps = require("./ps.js");

const HINTS = {
  paint:
    "Mit dem normalen Pinsel auf die Malebene malen. »Anwenden« legt die Striche auf die Oberfläche: sie biegen sich über Falten, laufen an ihnen entlang und übernehmen die Schatten.",
  recolor:
    "Die Fläche grob in der Wunschfarbe übermalen. »Anwenden« färbt sie um und erhält Schatten, Falten und Glanzlichter – auch von Schwarz nach Weiß.",
  light:
    "Dort einen Klecks in der Lichtfarbe malen, wo Licht hin soll. »Anwenden« beleuchtet die Umgebung passend zum Relief der Oberfläche.",
};

let mode = "paint";
let busy = false;

function $(id) {
  return document.getElementById(id);
}
function num(id) {
  return Number($(id).value);
}
function status(text, isError) {
  const el = $("status");
  el.textContent = text;
  el.className = isError ? "status error" : "status";
}

function setMode(m) {
  mode = m;
  document.querySelectorAll(".tab").forEach((t) => {
    t.className = t.getAttribute("data-mode") === m ? "tab active" : "tab";
  });
  document.querySelectorAll(".group").forEach((g) => {
    const f = g.getAttribute("data-for");
    g.className = !f || f === m ? "group" : "group hidden";
  });
  $("hint").textContent = HINTS[m];
}

function params() {
  const common = { scale: num("scale"), megapixels: num("quality") };
  if (mode === "paint") {
    return Object.assign(common, { wrap: num("wrap"), follow: num("follow"), shade: num("paintShade") / 100 });
  }
  if (mode === "recolor") {
    return Object.assign(common, { snap: num("snap"), shade: num("recShade") / 100, highlights: num("highlights") / 100 });
  }
  return Object.assign(common, {
    radius: num("radius"),
    intensity: num("intensity") / 100,
    height: num("height") / 100,
    relief: num("relief") / 100,
  });
}

async function run(label, fn) {
  if (busy) return;
  busy = true;
  status(label + " …");
  // dem Panel Zeit geben, den Status zu zeichnen, bevor gerechnet wird
  await new Promise((r) => setTimeout(r, 30));
  const t0 = Date.now();
  try {
    const info = await fn();
    let msg = "Fertig (" + ((Date.now() - t0) / 1000).toFixed(1) + " s).";
    if (info && info.scale < 0.999) {
      msg += " Mit " + Math.round(info.scale * 100) + " % Auflösung gerechnet – für mehr Details »Max. Rechenauflösung« erhöhen.";
    }
    if (info) msg += " Regler ändern und erneut »Anwenden« ersetzt das Ergebnis.";
    status(msg);
  } catch (e) {
    const text = e instanceof ps.UserError ? e.message : "Fehler: " + (e && e.message ? e.message : String(e));
    status(text, true);
    if (!(e instanceof ps.UserError)) console.error(e);
  } finally {
    busy = false;
  }
}

function init() {
  document.querySelectorAll(".tab").forEach((t) => {
    t.addEventListener("click", () => setMode(t.getAttribute("data-mode")));
  });
  $("newLayer").addEventListener("click", () =>
    run("Lege Malebene an", async () => {
      await ps.newStrokeLayer();
      return null;
    })
  );
  $("apply").addEventListener("click", () => run("Berechne", () => ps.apply(mode, params())));
  $("decompose").addEventListener("click", () => run("Zerlege Bild", () => ps.decompose(params())));
  setMode("paint");
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
else init();
