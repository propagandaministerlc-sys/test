package app.klar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klar.HearingRepository
import app.klar.model.HearingSettings
import kotlin.math.roundToInt

@Composable
fun MoreScreen() {
    val c = KlarTheme.colors
    val settings by HearingRepository.settings.collectAsStateWithLifecycle()

    Screen {
        LargeHeader("Mehr", eyebrow = "Klar")

        SectionHeader("Gehörschutz")
        GroupCard {
            SliderRow(
                title = "Maximale Lautstärke",
                valueText = "${settings.maxOutputDb.roundToInt()} dB",
                value = settings.maxOutputDb,
                onValueChange = { v -> HearingRepository.update { it.copy(maxOutputDb = v) } },
                valueRange = HearingSettings.MIN_CEILING_DB..HearingSettings.MAX_CEILING_DB,
                step = 1f,
                startIcon = KlarIcon.SHIELD,
            )
        }
        SectionFooter(
            "Ein Begrenzer sorgt dafür, dass auch plötzliche laute Geräusche diesen Pegel nie überschreiten. " +
                "Starte immer leise und erhöhe die Lautstärke langsam.",
        )

        SectionHeader("Tipps für den besten Klang")
        GroupCard {
            TipRow(
                KlarIcon.HEADPHONES,
                "Geräuschunterdrückung einschalten",
                "Halte bei AirPods Pro den Stiel gedrückt, bis die Geräuschunterdrückung an ist. " +
                    "So hörst du nur den bearbeiteten Klang – ohne Echo.",
            )
            Separator()
            TipRow(
                KlarIcon.PHONE,
                "Telefon nah an die Stimme",
                "Leg das Telefon auf den Tisch, in Richtung deines Gegenübers. Es wirkt dann wie ein Funkmikrofon.",
            )
            Separator()
            TipRow(
                KlarIcon.SPARKLE,
                "Etwas Verzögerung ist normal",
                "Bluetooth überträgt auf Android mit etwa 0,1 bis 0,3 Sekunden Verzögerung. " +
                    "Der AirPods-Mikrofon-Modus ist etwas schneller, klingt aber dumpfer.",
            )
            Separator()
            TipRow(
                KlarIcon.EAR,
                "Erst testen, dann anpassen",
                "Mit dem Hörtest unter „Klang“ stellt Klar den Equalizer passend zu deinem Gehör ein.",
            )
        }

        SectionHeader("Wichtig")
        GroupCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Klar ist kein Medizinprodukt und ersetzt weder ein Hörgerät noch eine ärztliche Untersuchung.",
                    style = KlarTheme.type.callout,
                    color = c.label,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Lass dein Gehör bei einer HNO-Praxis prüfen. In Deutschland zahlt die gesetzliche Krankenkasse " +
                        "Hörgeräte mit ärztlicher Verordnung. Hörakustiker müssen dir auch Geräte ohne Eigenanteil " +
                        "anbieten – dann zahlst du meist nur die gesetzliche Zuzahlung von 10 € pro Gerät.",
                    style = KlarTheme.type.subhead,
                    color = c.secondaryLabel,
                )
            }
        }

        Text(
            "Klar 1.0 · Alle Audiodaten bleiben auf deinem Telefon.\nDie App hat keinen Internetzugriff.",
            style = KlarTheme.type.footnote,
            color = c.tertiaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
        )
    }
}
