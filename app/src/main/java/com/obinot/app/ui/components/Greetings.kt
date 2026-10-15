package com.obinot.app.ui.components

import java.util.Calendar
import kotlin.random.Random

/**
 * Saludos de RecordScreen, escritos nativamente por idioma (no traducidos).
 * Cada idioma decide dónde va el nombre: "{n}" es el hueco, así en español
 * puede ir al inicio o al final con la puntuación correcta, y en alemán con
 * su propia cadencia.
 *
 * Estructura por idioma: base por franja + mensajes por mes y franja
 * + días especiales (tienen prioridad y son exclusivos).
 * Franjas: mañana 5–11, tarde 12–19, noche 20–4.
 */
object Greetings {

    const val NAME = "{n}"

    enum class Period { MORNING, AFTERNOON, NIGHT }

    fun periodFor(hour: Int) = when (hour) {
        in 5..11 -> Period.MORNING
        in 12..19 -> Period.AFTERNOON
        else -> Period.NIGHT
    }

    private class Lang(
        val base: Map<Period, List<String>>,
        /** 12 meses (enero = 0) × [mañana, tarde, noche]. */
        val months: List<List<String>>,
        val specials: Map<String, List<String>>
    )

    // Claves de días especiales
    private const val GTA_EVE = "gta_eve"
    private const val GTA_DAY = "gta_day"

    // GTA VI sale el jueves 19 de noviembre de 2026.
    private const val GTA_YEAR = 2026

    private fun specialKey(year: Int, month: Int, day: Int): String? = when {
        year == GTA_YEAR && month == Calendar.NOVEMBER && day == 18 -> GTA_EVE
        year == GTA_YEAR && month == Calendar.NOVEMBER && day == 19 -> GTA_DAY
        month == Calendar.DECEMBER && day == 24 -> "xmas_eve"
        month == Calendar.DECEMBER && day == 25 -> "xmas"
        month == Calendar.DECEMBER && day == 31 -> "nye"
        month == Calendar.JANUARY && day == 1 -> "new_year"
        month == Calendar.OCTOBER && day == 31 -> "halloween"
        month == Calendar.FEBRUARY && day == 14 -> "valentines"
        else -> null
    }

    /** Devuelve una plantilla con "{n}" para el idioma [language] ("es", "de", otro → inglés). */
    fun pick(language: String, year: Int, month: Int, day: Int, hour: Int, random: Random = Random): String {
        val lang = when (language) { "es" -> ES; "de" -> DE; else -> EN }
        specialKey(year, month, day)?.let { key ->
            lang.specials[key]?.takeIf { it.isNotEmpty() }?.let { return it.random(random) }
        }
        val p = periodFor(hour)
        val monthMsg = lang.months[month.coerceIn(0, 11)][p.ordinal]
        // El del mes pesa doble: se nota la temporada sin cansar.
        val pool = lang.base.getValue(p) + monthMsg + monthMsg
        return pool.random(random)
    }

    // ───────────────────────────── ENGLISH ─────────────────────────────
    private val EN = Lang(
        base = mapOf(
            Period.MORNING to listOf("Good morning, {n}.", "Morning, {n}! Ready when you are.", "Rise and shine, {n}."),
            Period.AFTERNOON to listOf("Good afternoon, {n}.", "Hey {n}, what's on your mind?", "Afternoon, {n}. What are we capturing?"),
            Period.NIGHT to listOf("Good evening, {n}.", "Still up, {n}? Let's capture it.", "Hey {n}, anything before the day ends?")
        ),
        months = listOf(
            listOf("New year, fresh page, {n}. What's first?", "Still January, {n} — keep the momentum going.", "Quiet January night, {n}. Capture that thought."),
            listOf("Good morning, {n}. February is short — make it count.", "Afternoon, {n}. A little love for your ideas today.", "Cozy February night, {n}. What's on your mind?"),
            listOf("Morning, {n}! Spring is almost here.", "Hey {n}, March afternoons are made for new plans.", "Evening, {n}. Wrap up March with a good note."),
            listOf("Good morning, {n}. April showers, bright ideas.", "Afternoon, {n}. Perfect weather for a fresh thought.", "Night, {n}. Let April's rain inspire you."),
            listOf("Morning, {n}! May is blooming — so are your ideas.", "Hey {n}, a sunny May afternoon. What are we capturing?", "Warm May night, {n}. Say it out loud."),
            listOf("Good morning, {n}. Summer is knocking.", "Afternoon, {n}. June light, clear thoughts.", "Long June evening, {n}. Take your time."),
            listOf("Rise and shine, {n}. It's a July kind of day.", "Hot July afternoon, {n}. Record it before you melt.", "Warm night, {n}. Midsummer thoughts welcome."),
            listOf("Morning, {n}. August is slow — your ideas aren't.", "Hey {n}, lazy August afternoon. Anything to note?", "Late-summer night, {n}. What's the story?"),
            listOf("Good morning, {n}. September, the season of fresh starts.", "Afternoon, {n}. Back to routines — and good ideas.", "Evening, {n}. Autumn is on its way."),
            listOf("Morning, {n}. Crisp October air, clear head.", "Afternoon, {n}. Spooky season is near — any ideas?", "Dark October night, {n}. Tell me a story."),
            listOf("Good morning, {n}. November calls for something warm.", "Afternoon, {n}. The year is winding down — keep capturing.", "Cold night, {n}. Cozy up and record."),
            listOf("Morning, {n}! December is here.", "Afternoon, {n}. Festive season, busy mind?", "Winter night, {n}. Wrap up the year with a thought.")
        ),
        specials = mapOf(
            GTA_EVE to listOf("{n}, GTA VI drops tomorrow. Tonight we sleep… right?", "Tomorrow is GTA VI day, {n}. Vice City is calling."),
            GTA_DAY to listOf("It's GTA VI day, {n}! Welcome back to Vice City.", "{n}, GTA VI is out today. The notes can wait — or can they?"),
            "xmas_eve" to listOf("Christmas Eve, {n}. Any last wishes to record?"),
            "xmas" to listOf("Merry Christmas, {n}!"),
            "nye" to listOf("Last day of the year, {n}. What are you leaving behind?"),
            "new_year" to listOf("Happy New Year, {n}! Let's start with a clean page."),
            "halloween" to listOf("Happy Halloween, {n}. Got a scary story to record?"),
            "valentines" to listOf("Happy Valentine's Day, {n}. Record something sweet.")
        )
    )

    // ───────────────────────────── ESPAÑOL ─────────────────────────────
    private val ES = Lang(
        base = mapOf(
            Period.MORNING to listOf("Buenos días, {n}.", "{n}, ¡buen día! Cuando quieras empezamos.", "¡Arriba, {n}! Hoy hay mucho por capturar."),
            Period.AFTERNOON to listOf("Buenas tardes, {n}.", "¿Qué tienes en mente, {n}?", "{n}, ¿qué vamos a guardar esta tarde?"),
            Period.NIGHT to listOf("Buenas noches, {n}.", "¿Sigues despierto, {n}? Cuéntamelo.", "{n}, ¿algo antes de que termine el día?")
        ),
        months = listOf(
            listOf("Año nuevo, página en blanco. ¿Por dónde empezamos, {n}?", "Sigue enero, {n}: no pierdas el ritmo.", "Noche tranquila de enero. Anótalo, {n}."),
            listOf("Buen día, {n}. Febrero es corto: aprovéchalo.", "{n}, una tarde de febrero para tus ideas.", "Noche de febrero, {n}. ¿Qué te da vueltas en la cabeza?"),
            listOf("¡Buenos días, {n}! La primavera ya casi llega.", "{n}, las tardes de marzo piden planes nuevos.", "Buenas noches, {n}. Cierra marzo con una buena nota."),
            listOf("Buen día, {n}. Lluvias de abril, ideas brillantes.", "{n}, buena tarde para una idea nueva.", "Que la lluvia de abril te inspire esta noche, {n}."),
            listOf("¡Buenos días, {n}! Mayo florece, y tus ideas también.", "{n}, tarde soleada de mayo. ¿Qué guardamos?", "Noche cálida de mayo, {n}. Dilo en voz alta."),
            listOf("Buen día, {n}. El verano ya toca la puerta.", "{n}, luz de junio y pensamientos claros.", "Larga noche de junio, {n}. Sin prisa."),
            listOf("¡Arriba, {n}! Día de pleno julio.", "{n}, tarde de calor. Grábalo antes de derretirte.", "Noche cálida, {n}. Las ideas de verano son bienvenidas."),
            listOf("Buen día, {n}. Agosto va lento, pero tus ideas no.", "{n}, tarde perezosa de agosto. ¿Algo que anotar?", "Noche de fin de verano, {n}. ¿Cuál es la historia?"),
            listOf("Buen día, {n}. Septiembre: temporada de nuevos comienzos.", "{n}, de vuelta a la rutina, y a las buenas ideas.", "Buenas noches, {n}. El otoño ya viene en camino."),
            listOf("Buen día, {n}. Aire fresco de octubre, mente despejada.", "{n}, se acerca la temporada de sustos. ¿Alguna idea?", "Oscura noche de octubre, {n}. Cuéntame una historia."),
            listOf("Buen día, {n}. Noviembre pide algo calentito.", "{n}, el año va cerrando: sigue capturando.", "Noche fría, {n}. Ponte cómodo y grábalo."),
            listOf("¡Buenos días, {n}! Ya es diciembre.", "{n}, época de fiestas, ¿la cabeza a mil?", "Noche de invierno, {n}. Cierra el año con una idea.")
        ),
        specials = mapOf(
            GTA_EVE to listOf("{n}, mañana sale GTA VI. Esta noche se duerme… ¿o no?", "Mañana es el día de GTA VI, {n}. Vice City te espera."),
            GTA_DAY to listOf("¡Hoy sale GTA VI, {n}! Bienvenido de vuelta a Vice City.", "{n}, GTA VI ya está aquí. Las notas pueden esperar… ¿o no?"),
            "xmas_eve" to listOf("Es Nochebuena, {n}. ¿Algún deseo de último momento?"),
            "xmas" to listOf("¡Feliz Navidad, {n}!"),
            "nye" to listOf("Último día del año, {n}. ¿Qué te llevas y qué dejas atrás?"),
            "new_year" to listOf("¡Feliz año nuevo, {n}! Empecemos con la página en blanco."),
            "halloween" to listOf("Feliz Halloween, {n}. ¿Tienes alguna historia de terror?"),
            "valentines" to listOf("Feliz San Valentín, {n}. Graba algo bonito.")
        )
    )

    // ───────────────────────────── DEUTSCH ─────────────────────────────
    private val DE = Lang(
        base = mapOf(
            Period.MORNING to listOf("Guten Morgen, {n}.", "Moin, {n}! Ich bin bereit, wenn du es bist.", "Aufgewacht, {n}! Heute gibt's viel festzuhalten."),
            Period.AFTERNOON to listOf("Guten Tag, {n}.", "Na, {n}, was geht dir durch den Kopf?", "Was halten wir heute Nachmittag fest, {n}?"),
            Period.NIGHT to listOf("Guten Abend, {n}.", "{n}, noch wach? Erzähl es mir.", "Noch etwas, bevor der Tag endet, {n}?")
        ),
        months = listOf(
            listOf("Neues Jahr, leeres Blatt. Womit fängst du an, {n}?", "Noch Januar, {n} – bleib im Schwung.", "Stille Januarnacht, {n}. Halte den Gedanken fest."),
            listOf("Guten Morgen, {n}. Der Februar ist kurz – nutze ihn.", "{n}, ein Februarnachmittag für deine Ideen.", "Gemütliche Februarnacht, {n}. Was beschäftigt dich?"),
            listOf("Guten Morgen, {n}! Der Frühling ist fast da.", "Märznachmittage sind für neue Pläne gemacht, {n}.", "Guten Abend, {n}. Beende den März mit einer guten Notiz."),
            listOf("Guten Morgen, {n}. April, April – und kluge Ideen.", "{n}, perfektes Wetter für einen frischen Gedanken.", "Lass dich vom Aprilregen inspirieren, {n}."),
            listOf("Guten Morgen, {n}! Der Mai blüht – deine Ideen auch.", "Sonniger Mainachmittag, {n}. Was halten wir fest?", "Warme Mainacht, {n}. Sprich es einfach aus."),
            listOf("Guten Morgen, {n}. Der Sommer klopft an.", "{n}, Junilicht und klare Gedanken.", "Langer Juniabend, {n}. Lass dir Zeit."),
            listOf("Aufgewacht, {n}! Ein richtiger Julitag.", "Heißer Julinachmittag, {n}. Nimm es auf, bevor du schmilzt.", "Warme Nacht, {n}. Hochsommer-Gedanken willkommen."),
            listOf("Guten Morgen, {n}. Der August ist gemächlich, deine Ideen nicht.", "Fauler Augustnachmittag, {n}. Gibt's etwas zu notieren?", "Spätsommernacht, {n}. Was ist die Geschichte?"),
            listOf("Guten Morgen, {n}. September – die Zeit für Neuanfänge.", "{n}, zurück in den Alltag – und zu guten Ideen.", "Guten Abend, {n}. Der Herbst ist unterwegs."),
            listOf("Guten Morgen, {n}. Klare Oktoberluft, klarer Kopf.", "{n}, die Gruselzeit naht. Schon eine Idee?", "Dunkle Oktobernacht, {n}. Erzähl mir eine Geschichte."),
            listOf("Guten Morgen, {n}. Der November verlangt nach etwas Warmem.", "{n}, das Jahr neigt sich dem Ende – halte weiter fest.", "Kalte Nacht, {n}. Mach es dir gemütlich und sprich los."),
            listOf("Guten Morgen, {n}! Es ist Dezember.", "{n}, Weihnachtszeit – und der Kopf voller Gedanken?", "Winternacht, {n}. Beende das Jahr mit einem Gedanken.")
        ),
        specials = mapOf(
            GTA_EVE to listOf("{n}, morgen erscheint GTA VI. Heute Nacht wird geschlafen… oder?", "Morgen ist GTA-VI-Tag, {n}. Vice City ruft."),
            GTA_DAY to listOf("Heute erscheint GTA VI, {n}! Willkommen zurück in Vice City.", "{n}, GTA VI ist da. Die Notizen können warten… oder?"),
            "xmas_eve" to listOf("Heiligabend, {n}. Noch ein letzter Wunsch zum Aufnehmen?"),
            "xmas" to listOf("Frohe Weihnachten, {n}!"),
            "nye" to listOf("Letzter Tag des Jahres, {n}. Was lässt du hinter dir?"),
            "new_year" to listOf("Frohes neues Jahr, {n}! Fangen wir mit einem leeren Blatt an."),
            "halloween" to listOf("Fröhliches Halloween, {n}. Eine Gruselgeschichte zum Aufnehmen?"),
            "valentines" to listOf("Schönen Valentinstag, {n}. Nimm etwas Schönes auf.")
        )
    )
}
