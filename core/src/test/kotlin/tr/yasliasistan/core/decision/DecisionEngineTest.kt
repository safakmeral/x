package tr.yasliasistan.core.decision

import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionEngineTest {

    private val calls = mutableListOf<String>()
    private val settingsAct = Decision.Act(Action.OpenApp(app("Ayarlar", "com.android.settings")), certain = true)

    private fun layer(name: String, result: Decision?) = DecisionLayer { calls += name; result }

    @Test
    fun layersRunInOrderRuleRecipeLanguageModel() {
        val engine = DecisionEngine(layer("rule", null), layer("recipe", null), layer("llm", null))
        assertEquals(Decision.CannotDo, engine.decide(Command("bir şey yap")))
        assertEquals(listOf("rule", "recipe", "llm"), calls)
    }

    @Test
    fun ruleMatchStopsBeforeRecipeAndLanguageModel() {
        val engine = DecisionEngine(layer("rule", settingsAct), layer("recipe", null), layer("llm", null))
        assertEquals(settingsAct, engine.decide(Command("ayarları aç")))
        assertEquals(listOf("rule"), calls)
    }

    @Test
    fun recipeMatchStopsBeforeLanguageModel() {
        val engine = DecisionEngine(layer("rule", null), layer("recipe", settingsAct), layer("llm", null))
        assertEquals(settingsAct, engine.decide(Command("ayarları aç")))
        assertEquals(listOf("rule", "recipe"), calls)
    }

    @Test
    fun resolveReportsTheAnsweringLayer() {
        val byRecipe = DecisionEngine(layer("rule", null), layer("recipe", settingsAct), layer("llm", null))
        assertEquals(DecisionSource.RECIPE, byRecipe.resolve(Command("ayarları aç")).source)
        val byLlm = DecisionEngine(layer("rule", null), layer("recipe", null), layer("llm", settingsAct))
        assertEquals(DecisionSource.LLM, byLlm.resolve(Command("ayarları aç")).source)
        val none = DecisionEngine(RuleLayer(emptyList()))
        assertEquals(SourcedDecision(Decision.CannotDo, DecisionSource.NONE), none.resolve(Command("hava nasıl")))
    }

    @Test
    fun placeholderLayersAreEmptyInThisMilestone() {
        val engine = DecisionEngine(RuleLayer(emptyList()))
        assertEquals(Decision.CannotDo, engine.decide(Command("hava nasıl")))
    }

    @Test
    fun ruleLayerTriesRulesInOrder() {
        val first = Rule { calls += "first"; null }
        val second = Rule { calls += "second"; settingsAct }
        val third = Rule { calls += "third"; null }
        assertEquals(settingsAct, RuleLayer(listOf(first, second, third)).decide(Command("x")))
        assertEquals(listOf("first", "second"), calls)
    }
}
