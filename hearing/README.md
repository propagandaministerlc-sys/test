# Klar – Hörhilfe für Android

Klar macht aus AirPods (oder anderen Kopfhörern) eine einfache Hörhilfe. Die App nimmt die Umgebung über das Mikrofon auf, hebt Stimmen hervor, dämpft Störgeräusche und spielt das Ergebnis live auf den Kopfhörern ab.

> Klar ist kein Medizinprodukt und ersetzt kein Hörgerät. In Deutschland zahlt die gesetzliche Krankenkasse Hörgeräte mit ärztlicher Verordnung; Hörakustiker müssen auch Geräte ohne Eigenanteil anbieten.

## Funktionen

- **Ein-Tipp-Start** mit Live-Pegelanzeige. Der Dienst läuft im Hintergrund weiter, auch bei ausgeschaltetem Bildschirm.
- **Situationen**: Natürlich, Gespräch, Restaurant, TV.
- **Stimmen hervorheben**: Betont den Sprachbereich (Präsenz 1–4 kHz), senkt Brummen und Zischen ab und macht Pausen zwischen Wörtern leiser (Spracherkennung).
- **Störgeräusche reduzieren**: Spektrale Rauschminderung (Wiener-Filter mit Rauschschätzung pro Frequenz). Gleichmäßige Geräusche wie Lüfter, Verkehr oder Stimmengewirr werden um bis zu 22 dB gedämpft.
- **Leise Töne anheben**: Kompression wie bei Hörgeräten (WDRC). Leises wird verstärkt, Lautes nicht noch lauter.
- **Hörtest** (ca. 3 Minuten): Töne von 250 Hz bis 8 kHz auf jedem Ohr. Daraus entsteht ein Equalizer pro Ohr nach der Halbverstärkungsregel.
- **6-Band-Equalizer** für beide Ohren gemeinsam oder getrennt, dazu **Balance**.
- **Gehörschutz**: Ein Begrenzer sorgt dafür, dass der Ausgang den eingestellten Höchstpegel nie überschreitet. Werden die Kopfhörer getrennt, stoppt die App sofort, damit es nicht über den Lautsprecher pfeift.
- **Mikrofon wählbar**:
  - *Telefon* (Standard): beste Qualität. Das Telefon wirkt wie ein Funkmikrofon.
  - *AirPods*: nutzt die AirPods-Mikrofone über Bluetooth-Telefonie. Etwas weniger Verzögerung, aber nur Telefonqualität.
- Helles und dunkles Design in Grautönen, angelehnt an iOS.
- Keine Internet-Berechtigung. Audio verlässt nie das Telefon.

## Grenzen

Android überträgt Bluetooth-Audio mit etwa 100–300 ms Verzögerung. Das lässt sich in einer App nicht umgehen. Bei AirPods Pro hilft es, die Geräuschunterdrückung einzuschalten (Stiel gedrückt halten): Dann hört man nur den bearbeiteten Klang und kein Echo.

Die offizielle Hörgeräte-Funktion der AirPods Pro gibt es nur mit einem iPhone.

## Signalweg

```
Mikrofon → Hochpass 90 Hz → STFT (512 Punkte, 50 % Überlappung)
        → Rauschschätzung + Wiener-Gewinn + Spracherkennung
        → Stimmen-Fokus + Equalizer (je Ohr) → inverse STFT
        → Lautstärke + Kompression → Balance → Begrenzer → Kopfhörer
```

| Datei | Aufgabe |
| --- | --- |
| `dsp/SpectralProcessor.kt` | STFT, Rauschminderung, Spracherkennung, Equalizer |
| `dsp/HearingDsp.kt` | Gesamte Kette inkl. Kompressor und Begrenzer |
| `dsp/Fft.kt`, `dsp/Filters.kt` | FFT, Biquad, Stimmen-Gewichtung, EQ-Interpolation |
| `model/` | Einstellungen, Situationen, Hörtest-Auswertung |
| `audio/AudioEngine.kt` | AudioRecord → DSP → AudioTrack auf einem Echtzeit-Thread |
| `audio/HearingService.kt` | Vordergrunddienst mit Benachrichtigung |
| `audio/AudioDevices.kt` | Kopfhörer-Erkennung, AirPods-Mikrofon (Bluetooth SCO) |
| `audio/TonePlayer.kt` | Töne für den Hörtest |
| `ui/` | Compose-Oberfläche: Hören, Klang, Mehr, Hörtest |

## Bauen

```sh
./gradlew :hearing:assembleDebug          # hearing/build/outputs/apk/debug/hearing-debug.apk
./gradlew :hearing:testDebugUnitTest      # DSP- und Modelltests
```

GitHub Actions baut bei jedem Push beide Apps. Die Hörhilfe-APK liegt im Artefakt `klar-hoerhilfe-apk`.
