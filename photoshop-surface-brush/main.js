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
  if (ps.isLive()) {
    ps.setLiveMode(m).catch((e) => status("Fehler: " + (e && e.message ? e.message : String(e)), true));
  }
}

function updateLiveButton() {
  $("live").textContent = ps.isLive() ? "Live-Modus beenden" : "Live-Modus starten";
}
function liveStatus(text, isError) {
  status(text, isError);
  updateLiveButton();
}

function isAuto() {
  const el = $("auto");
  return !!(el && el.checked);
}
/** In der Automatik werden die Längen-Regler ausgeblendet – sie ergeben sich aus Pinsel und Bild. */
function updateAuto() {
  const auto = isAuto();
  ["wrap", "follow", "snap", "radius", "scale"].forEach((id) => {
    $(id).style.display = auto ? "none" : "";
  });
}

function params() {
  const common = { scale: num("scale"), megapixels: num("quality"), auto: isAuto() };
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

function liveParams() {
  return params();
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
    if (info && info.auto) {
      msg += " Automatik: Pinsel ≈ " + Math.round(info.auto.brush) + " px, Faltengröße " + Math.round(info.auto.scale) + " px.";
    }
    if (info === "live") msg = ps.isLive() ? "Live-Modus läuft: Einfach mit dem Pinsel malen – jeder Strich wird beim Loslassen übernommen." : "Live-Modus beendet.";
    else if (info === "undo") msg = "Letzter Strich zurückgenommen.";
    else if (info) msg += " Regler ändern und erneut »Anwenden« ersetzt das Ergebnis.";
    status(msg);
  } catch (e) {
    const text = e instanceof ps.UserError ? e.message : "Fehler: " + (e && e.message ? e.message : String(e));
    status(text, true);
    if (!(e instanceof ps.UserError)) console.error(e);
  } finally {
    busy = false;
    updateLiveButton();
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
  $("live").addEventListener("click", () =>
    run(ps.isLive() ? "Beende Live-Modus" : "Starte Live-Modus", async () => {
      if (ps.isLive()) await ps.stopLive();
      else await ps.startLive(mode, liveParams, liveStatus);
      return "live";
    })
  );
  $("liveUndo").addEventListener("click", () =>
    run("Nehme Strich zurück", async () => {
      await ps.undoLive();
      return "undo";
    })
  );
  $("apply").addEventListener("click", () => run("Berechne", () => ps.apply(mode, params())));
  $("decompose").addEventListener("click", () => run("Zerlege Bild", () => ps.decompose(params())));
  $("auto").addEventListener("change", updateAuto);
  $("auto").addEventListener("click", () => setTimeout(updateAuto, 0));
  setMode("paint");
  updateAuto();
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
else init();
