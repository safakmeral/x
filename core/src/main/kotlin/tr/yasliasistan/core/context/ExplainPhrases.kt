package tr.yasliasistan.core.context

import tr.yasliasistan.core.clarify.IndirectKind

/**
 * Sentences of the layered "neden?" answer (PLAN G-39, FG-15, NFG-09). `:core` has no Android
 * resources; the app implements this with strings.xml. Intended Turkish wording is in each comment.
 * Sentences marked "why" are complete sentences; "done" phrases are lower-case predicates that
 * [because] puts after "Bu yüzden".
 */
interface ExplainPhrases {
    // --- Layer 1: one line ---

    /** "%1$s Bu yüzden %2$s." (why sentence + done phrase). */
    fun because(why: String, done: String): String

    /** why: "Bunu siz istediniz." */
    fun youAsked(): String

    /** why: "Sesi duyamadığınızı söylediniz." / "Ekranın karanlık olduğunu söylediniz." ... */
    fun indirectCue(kind: IndirectKind): String

    /** why: "\"Onu\" deyince az önce konuştuğumuz %1$s kaydını anladım." */
    fun previousReference(name: String): String

    /** why: "\"Bunu\" deyince ekranda seçili olan \"%1$s\" ögesini anladım." */
    fun screenReference(label: String): String

    /** why: "Son yaptığım işi yinelememi istediniz." */
    fun repeated(): String

    /** done: "%1$s uygulamasını açtım" */
    fun doneOpenApp(label: String): String

    /** done: "%1$s için arama ekranını açtım" */
    fun doneCall(name: String): String

    /** done: "%1$s için mesaj ekranını açtım" */
    fun doneMessage(name: String): String

    /** done: "sesi açtım" / "sesi kıstım" / "ses ayarını gösterdim" */
    fun doneVolume(up: Boolean?): String

    /** done: "ekran ayarlarını açtım" */
    fun doneDisplaySettings(): String

    /** done: "rehber izni ekranını açtım" */
    fun doneContactsPermission(): String

    /** done: "istediğiniz işi yaptım" (actions without their own sentence). */
    fun doneOther(): String

    /** "Söylediğinize uyan birden çok seçenek vardı; hangisini istediğinizi bilmem gerekiyordu." */
    fun askedSeveral(): String

    /** "Komutta kimin ya da neyin istendiği eksikti." */
    fun askedMissing(): String

    /** "Bu isteği nasıl yapacağımı bilmiyorum." */
    fun cannotDo(): String

    /** "Az önce yaptığım bir iş yok." */
    fun nothingRecent(): String

    // --- Layer 2: grounds, based on the screen and device data (NFG-09) ---

    /** "Ekranda \"%1$s\" yazan ögeyi görüyorum." */
    fun screenBasis(label: String): String

    /** "Telefonunuzda %1$s adlı uygulama yüklü." */
    fun appBasis(label: String): String

    /** "Rehberinizde söylediğiniz ada uyan kişi %1$s." */
    fun contactBasis(name: String): String

    /** "Söylediğiniz ad rehberde tam olarak yoktu; en yakını %1$s." */
    fun contactBasisInexact(name: String): String

    /** "Bu tür işleri, yanlışlık olmasın diye önce size sorarım." (Medium / High risk) */
    fun confirmNote(): String

    /** "Bu iş kolayca geri alınabilir." (Low risk) */
    fun undoNote(): String

    /** "Tahmin edip yanlış iş yapmamak için soruyorum." */
    fun askDetail(): String

    /** "Telefondaki kurallarım bu isteği karşılamıyor; başka sözcüklerle söylemeyi deneyebilirsiniz." */
    fun cannotDoDetail(): String

    /** "Bundan başka söyleyebileceğim bir ayrıntı yok." */
    fun noMoreDetail(): String
}
