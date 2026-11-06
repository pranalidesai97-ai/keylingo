package com.keylingo.app.data.language.spanish

import com.keylingo.app.data.language.spanish.SpanishPerson.EL
import com.keylingo.app.data.language.spanish.SpanishPerson.ELLOS
import com.keylingo.app.data.language.spanish.SpanishPerson.NOSOTROS
import com.keylingo.app.data.language.spanish.SpanishPerson.TU
import com.keylingo.app.data.language.spanish.SpanishPerson.YO
import com.keylingo.app.data.language.spanish.SpanishTense.CONDITIONAL
import com.keylingo.app.data.language.spanish.SpanishTense.FUTURE
import com.keylingo.app.data.language.spanish.SpanishTense.IMPERFECT
import com.keylingo.app.data.language.spanish.SpanishTense.PRESENT
import com.keylingo.app.data.language.spanish.SpanishTense.PRETERITE
import org.junit.Assert.assertEquals
import org.junit.Test

class SpanishConjugatorTest {

    private fun paradigm(inf: String, tense: SpanishTense) =
        SpanishPerson.entries.joinToString(" ") { SpanishConjugator.conjugate(inf, tense, it) }

    @Test fun regularPresentAllThreeConjugations() {
        assertEquals("hablo hablas habla hablamos hablan", paradigm("hablar", PRESENT))
        assertEquals("como comes come comemos comen", paradigm("comer", PRESENT))
        assertEquals("vivo vives vive vivimos viven", paradigm("vivir", PRESENT))
    }

    @Test fun regularPreteriteCarriesAccents() {
        assertEquals("trabajé trabajaste trabajó trabajamos trabajaron", paradigm("trabajar", PRETERITE))
        assertEquals("aprendí aprendiste aprendió aprendimos aprendieron", paradigm("aprender", PRETERITE))
        assertEquals("escribí escribiste escribió escribimos escribieron", paradigm("escribir", PRETERITE))
    }

    @Test fun imperfectFutureConditional() {
        assertEquals("caminaba caminabas caminaba caminábamos caminaban", paradigm("caminar", IMPERFECT))
        assertEquals("vivía vivías vivía vivíamos vivían", paradigm("vivir", IMPERFECT))
        assertEquals("hablaré hablarás hablará hablaremos hablarán", paradigm("hablar", FUTURE))
        assertEquals("comería comerías comería comeríamos comerían", paradigm("comer", CONDITIONAL))
        assertEquals("tendré tendrás tendrá tendremos tendrán", paradigm("tener", FUTURE))
        assertEquals("haría harías haría haríamos harían", paradigm("hacer", CONDITIONAL))
        assertEquals("oiré", SpanishConjugator.conjugate("oír", FUTURE, YO))
    }

    @Test fun stemChangingVerbsChangeOnlyInTheBootForms() {
        assertEquals("pienso piensas piensa pensamos piensan", paradigm("pensar", PRESENT))
        assertEquals("duermo duermes duerme dormimos duermen", paradigm("dormir", PRESENT))
        assertEquals("juego juegas juega jugamos juegan", paradigm("jugar", PRESENT))
        assertEquals("pido pides pide pedimos piden", paradigm("pedir", PRESENT))
        assertEquals("prefiero prefieres prefiere preferimos prefieren", paradigm("preferir", PRESENT))
        assertEquals("encuentro encuentras encuentra encontramos encuentran", paradigm("encontrar", PRESENT))
    }

    @Test fun irStemChangersChangeInThirdPersonPreterite() {
        assertEquals("dormí dormiste durmió dormimos durmieron", paradigm("dormir", PRETERITE))
        assertEquals("pedí pediste pidió pedimos pidieron", paradigm("pedir", PRETERITE))
        assertEquals("sentí sentiste sintió sentimos sintieron", paradigm("sentir", PRETERITE))
        assertEquals("seguí seguiste siguió seguimos siguieron", paradigm("seguir", PRETERITE))
    }

    @Test fun orthographicChanges() {
        assertEquals("busqué", SpanishConjugator.conjugate("buscar", PRETERITE, YO))
        assertEquals("llegué", SpanishConjugator.conjugate("llegar", PRETERITE, YO))
        assertEquals("empecé", SpanishConjugator.conjugate("empezar", PRETERITE, YO))
        assertEquals("jugué", SpanishConjugator.conjugate("jugar", PRETERITE, YO))
        assertEquals("almorcé", SpanishConjugator.conjugate("almorzar", PRETERITE, YO))
        assertEquals("conozco", SpanishConjugator.conjugate("conocer", PRESENT, YO))
        assertEquals("conduzco", SpanishConjugator.conjugate("conducir", PRESENT, YO))
        assertEquals("conduje condujiste condujo condujimos condujeron", paradigm("conducir", PRETERITE))
        assertEquals("protejo", SpanishConjugator.conjugate("proteger", PRESENT, YO))
        assertEquals("elijo eliges elige elegimos eligen", paradigm("elegir", PRESENT))
        assertEquals("sigo sigues sigue seguimos siguen", paradigm("seguir", PRESENT))
        assertEquals("construyo construyes construye construimos construyen", paradigm("construir", PRESENT))
        assertEquals("construí construiste construyó construimos construyeron", paradigm("construir", PRETERITE))
        assertEquals("leí leíste leyó leímos leyeron", paradigm("leer", PRETERITE))
        assertEquals("envío envías envía enviamos envían", paradigm("enviar", PRESENT))
        assertEquals("continúo continúas continúa continuamos continúan", paradigm("continuar", PRESENT))
    }

    @Test fun commonIrregularVerbs() {
        assertEquals("soy eres es somos son", paradigm("ser", PRESENT))
        assertEquals("estoy estás está estamos están", paradigm("estar", PRESENT))
        assertEquals("voy vas va vamos van", paradigm("ir", PRESENT))
        assertEquals("fui fuiste fue fuimos fueron", paradigm("ir", PRETERITE))
        assertEquals("iba ibas iba íbamos iban", paradigm("ir", IMPERFECT))
        assertEquals("era eras era éramos eran", paradigm("ser", IMPERFECT))
        assertEquals("tengo tienes tiene tenemos tienen", paradigm("tener", PRESENT))
        assertEquals("quiero quieres quiere queremos quieren", paradigm("querer", PRESENT))
        assertEquals("puedo puedes puede podemos pueden", paradigm("poder", PRESENT))
        assertEquals("hice hiciste hizo hicimos hicieron", paradigm("hacer", PRETERITE))
        assertEquals("dije dijiste dijo dijimos dijeron", paradigm("decir", PRETERITE))
        assertEquals("sé sabes sabe sabemos saben", paradigm("saber", PRESENT))
        assertEquals("vine viniste vino vinimos vinieron", paradigm("venir", PRETERITE))
        assertEquals("oigo oyes oye oímos oyen", paradigm("oír", PRESENT))
        assertEquals("río ríes ríe reímos ríen", paradigm("reír", PRESENT))
    }

    @Test fun nonFiniteForms() {
        assertEquals("trabajando", SpanishConjugator.gerund("trabajar"))
        assertEquals("comiendo", SpanishConjugator.gerund("comer"))
        assertEquals("leyendo", SpanishConjugator.gerund("leer"))
        assertEquals("durmiendo", SpanishConjugator.gerund("dormir"))
        assertEquals("pidiendo", SpanishConjugator.gerund("pedir"))
        assertEquals("diciendo", SpanishConjugator.gerund("decir"))
        assertEquals("yendo", SpanishConjugator.gerund("ir"))
        assertEquals("construyendo", SpanishConjugator.gerund("construir"))
        assertEquals("siguiendo", SpanishConjugator.gerund("seguir"))
        assertEquals("hablado", SpanishConjugator.pastParticiple("hablar"))
        assertEquals("comido", SpanishConjugator.pastParticiple("comer"))
        assertEquals("leído", SpanishConjugator.pastParticiple("leer"))
        assertEquals("hecho", SpanishConjugator.pastParticiple("hacer"))
        assertEquals("escrito", SpanishConjugator.pastParticiple("escribir"))
        assertEquals("abierto", SpanishConjugator.pastParticiple("abrir"))
    }

    @Test fun reflexiveAndMultiWordExpressions() {
        assertEquals("me quedo", SpanishConjugator.conjugatePhrase("quedarse", PRESENT, YO))
        assertEquals("se sentó", SpanishConjugator.conjugatePhrase("sentarse", PRETERITE, EL))
        assertEquals("nos preocupamos", SpanishConjugator.conjugatePhrase("preocuparse", PRESENT, NOSOTROS))
        assertEquals("estoy de acuerdo", SpanishConjugator.conjugatePhrase("estar de acuerdo", PRESENT, YO))
        assertEquals("hizo clic", SpanishConjugator.conjugatePhrase("hacer clic", PRETERITE, EL))
        assertEquals("se quedaron", SpanishConjugator.conjugatePhrase("quedarse", PRETERITE, ELLOS))
        assertEquals("te despiertas", SpanishConjugator.conjugatePhrase("despertarse", PRESENT, TU))
    }

    @Test fun imperatives() {
        assertEquals("habla", SpanishConjugator.imperativeTu("hablar"))
        assertEquals("ven", SpanishConjugator.imperativeTu("venir"))
        assertEquals("haz", SpanishConjugator.imperativeTu("hacer"))
        assertEquals("duerme", SpanishConjugator.imperativeTu("dormir"))
        assertEquals("siéntate", SpanishConjugator.imperativeTu("sentarse"))
    }
}
