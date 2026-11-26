package app.dastyar.agent.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun openRouterZeroPricingIsFree() {
        assertTrue(ModelFree.isFree(ProviderKinds.OPENROUTER, "google/gemma-2-9b-it", "0", "0"))
        assertTrue(ModelFree.isFree(ProviderKinds.OPENROUTER, "google/gemma-2-9b-it", "0.0", "0.000"))
    }

    @Test
    fun openRouterFreeSuffixAndRouterAreFree() {
        assertTrue(ModelFree.isFree(ProviderKinds.OPENROUTER, "meta/llama-3.1-8b:free", null, null))
        assertTrue(ModelFree.isFree(ProviderKinds.OPENROUTER, "openrouter/free", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.OPENROUTER, "openai/gpt-4.1", "0.000002", "0.000008"))
        assertFalse(ModelFree.isFree(ProviderKinds.OPENROUTER, "openai/gpt-4.1", "0", null))
    }

    @Test
    fun unknownPriceIsNotFree() {
        assertFalse(ModelFree.isFree(ProviderKinds.OPENAI, "gpt-4.1", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.ANTHROPIC, "claude-sonnet", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.XAI, "grok-3", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.DEEPSEEK, "deepseek-chat", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.MISTRAL, "mistral-large", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.CUSTOM, "local-model", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.GROQ, "some/unpublished-model", null, null))
        assertTrue(ModelFree.isFree(ProviderKinds.CUSTOM, "local:free", null, null))
        assertTrue(ModelFree.isFree(ProviderKinds.OPENAI, "promo", "0", "0"))
    }

    @Test
    fun cerebrasIsNotAutoFree() {
        assertFalse(ModelFree.isFree(ProviderKinds.CEREBRAS, "llama3.1-8b", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.CEREBRAS, "llama3.1-8b", "0", null))
        assertTrue(ModelFree.isFree(ProviderKinds.CEREBRAS, "priced-zero", "0", "0"))
    }

    @Test
    fun geminiFlagshipIsNotAutoFree() {
        assertFalse(ModelFree.isFree(ProviderKinds.GEMINI, "gemini-3.8-pro", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.GEMINI, "models/gemini-2.5-pro", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.GEMINI, "gemini-ultra", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.GEMINI, "gemini-experimental", null, null))
        assertTrue(ModelFree.isFree(ProviderKinds.GEMINI, "gemini-3.8-flash", null, null))
        assertTrue(ModelFree.isFree(ProviderKinds.GEMINI, "models/gemini-3.8-flash-lite", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.GEMINI, "gemini-3.8-pro-flash", null, null))
    }

    @Test
    fun groqNvidiaAndCloudflareUseKnownRules() {
        assertTrue(ModelFree.isFree(ProviderKinds.GROQ, "openai/gpt-oss-120b", null, null))
        assertTrue(ModelFree.isFree(ProviderKinds.CLOUDFLARE, "@cf/meta/llama-3.1-8b-instruct", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.CLOUDFLARE, "@hf/partner/model", null, null))
        assertFalse(ModelFree.isFree(ProviderKinds.NVIDIA, "meta/llama", null, null, "https://example.com/v1"))
        assertTrue(
            ModelFree.isFree(
                ProviderKinds.NVIDIA,
                "meta/llama",
                null,
                null,
                "https://integrate.api.nvidia.com/v1",
            ),
        )
        assertFalse(
            ModelFree.isFree(
                ProviderKinds.NVIDIA,
                "meta/llama",
                "0.2",
                "0.2",
                "https://integrate.api.nvidia.com/v1",
            ),
        )
    }

    @Test
    fun freeModelsSortFirstWithoutBreakingSearchGroups() {
        val models = listOf(
            CatalogModel("nimbus-paid", "Nimbus Paid", free = false),
            CatalogModel("zed-free", "Zed", free = true),
            CatalogModel("other", "Other", free = false),
            CatalogModel("nimbus-free", "Nimbus Free", free = true),
        )
        val sorted = ModelOrder.sort(models).map { it.id }
        assertEquals(listOf("nimbus-free", "zed-free", "nimbus-paid", "other"), sorted)
        val filtered = ModelOrder.filter(models, "nimbus").map { it.id }
        assertEquals(listOf("nimbus-free", "nimbus-paid"), filtered)
    }

    @Test
    fun choosePrefersLiveThenCacheThenBuiltin() {
        val live = listOf(CatalogModel("live", "Live", free = false))
        val cached = listOf(CatalogModel("cached", "Cached", free = true, source = ModelSource.CACHE))
        val builtin = BuiltinModels.forKind(ProviderKinds.OPENROUTER)
        assertEquals("live", ModelOrder.choose(live, cached, builtin).second)
        assertEquals("cache", ModelOrder.choose(emptyList(), cached, builtin).second)
        assertEquals("cache", ModelOrder.choose(null, cached, builtin).second)
        val fallback = ModelOrder.choose(null, emptyList(), builtin)
        assertEquals("builtin", fallback.second)
        assertEquals("openrouter/free", fallback.first.single().id)
        assertTrue(fallback.first.single().free)
        assertEquals("empty", ModelOrder.choose(null, emptyList(), emptyList()).second)
    }

    @Test
    fun urlsHonorBaseAndDoNotDoubleSlash() {
        assertEquals("https://api.openai.com/v1/models", ModelEndpoints.openAiModels("https://api.openai.com/v1/"))
        assertEquals("https://api.openai.com/v1/models", ModelEndpoints.openAiModels("https://api.openai.com/v1/models"))
        assertEquals("https://api.deepseek.com/models", ModelEndpoints.openAiModels("https://api.deepseek.com"))
        assertEquals(
            "https://openrouter.ai/api/v1/models",
            ModelEndpoints.openRouterModels("https://openrouter.ai/api/v1"),
        )
        val gemini = ModelEndpoints.geminiModels("https://generativelanguage.googleapis.com/v1beta")
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models", gemini)
        assertFalse(gemini.contains("key="))
        assertEquals("https://api.anthropic.com/v1/models", ModelEndpoints.anthropicModels("https://api.anthropic.com"))
        assertEquals("https://api.anthropic.com/v1/models", ModelEndpoints.anthropicModels("https://api.anthropic.com/v1/"))
        assertEquals(
            "https://api.cloudflare.com/client/v4/accounts/abc/ai/models/search",
            ModelEndpoints.cloudflareModels("https://api.cloudflare.com/", "abc"),
        )
    }

    @Test
    fun parsesOpenRouterPricingContextVisionAndTools() {
        val body = """
            {"data":[
              {"id":"paid/model","name":"Paid","context_length":32000,
               "pricing":{"prompt":"0.000001","completion":"0.000002"},
               "architecture":{"input_modalities":["text"]},
               "supported_parameters":["temperature"]},
              {"id":"vendor/model:free","name":"Free","context_length":8192,
               "pricing":{"prompt":"0","completion":"0"},
               "architecture":{"modality":"text+image->text","input_modalities":["text","image"]},
               "supported_parameters":["tools","tool_choice"]}
            ]}
        """.trimIndent()
        val models = ModelOrder.sort(ModelListParser.parse(ProviderKinds.OPENROUTER, "https://openrouter.ai/api/v1", body))
        assertEquals("vendor/model:free", models[0].id)
        assertTrue(models[0].free)
        assertEquals(8192, models[0].contextLength)
        assertEquals(true, models[0].vision)
        assertEquals(true, models[0].tools)
        assertFalse(models[1].free)
        assertEquals(false, models[1].vision)
        assertEquals(false, models[1].tools)
        assertEquals("0.000001", models[1].promptPrice)
    }

    @Test
    fun parsesGeminiAndDoesNotMarkProFree() {
        val body = """
            {"models":[
              {"name":"models/gemini-3.8-pro","displayName":"Pro","inputTokenLimit":1000000,
               "supportedGenerationMethods":["generateContent","embedContent"]},
              {"name":"models/gemini-3.8-flash","displayName":"Flash","inputTokenLimit":1000000,
               "supportedGenerationMethods":["generateContent"]},
              {"name":"models/text-embedding","displayName":"Embed","inputTokenLimit":2048,
               "supportedGenerationMethods":["embedContent"]}
            ]}
        """.trimIndent()
        val models = ModelListParser.parse(ProviderKinds.GEMINI, "https://generativelanguage.googleapis.com/v1beta", body)
        val pro = models.first { it.id == "gemini-3.8-pro" }
        val flash = models.first { it.id == "gemini-3.8-flash" }
        val embed = models.first { it.id == "text-embedding" }
        assertFalse(pro.free)
        assertTrue(flash.free)
        assertFalse(embed.free)
        assertNull(pro.tools)
        assertEquals(false, embed.tools)
        assertEquals(1_000_000, flash.contextLength)
    }

    @Test
    fun parsesAnthropicCapabilitiesWithoutInventingTools() {
        val body = """
            {"data":[{"id":"claude-opus-5-5","display_name":"Claude Opus 5.5","max_input_tokens":200000,
              "capabilities":{"image_input":{"supported":true},"server_tools":{"supported":true}}}],
             "has_more":false}
        """.trimIndent()
        val model = ModelListParser.parse(ProviderKinds.ANTHROPIC, "https://api.anthropic.com", body).single()
        assertFalse(model.free)
        assertEquals(true, model.vision)
        assertNull(model.tools)
        assertEquals(200_000, model.contextLength)
    }

    @Test
    fun cerebrasPayloadIsTrialAndNotFree() {
        val body = """{"data":[{"id":"llama3.1-8b","context_window":8192}]}"""
        val model = ModelListParser.parse(ProviderKinds.CEREBRAS, "https://api.cerebras.ai/v1", body).single()
        assertFalse(model.free)
        assertEquals("trial", model.note)
        assertEquals(8192, model.contextLength)
    }

    @Test
    fun parsesCloudflareSearchResult() {
        val body = """
            {"success":true,"result":[
              {"name":"@cf/meta/llama-3.1-8b-instruct","properties":[
                {"property_id":"context_window","value":"8192"},
                {"property_id":"function_calling","value":"true"}
              ]},
              {"name":"@hf/partner/model","properties":[]}
            ],"result_info":{"count":2,"page":1,"per_page":50,"total_count":2}}
        """.trimIndent()
        val models = ModelListParser.parse(ProviderKinds.CLOUDFLARE, "https://api.cloudflare.com", body)
        val first = models.first { it.id.startsWith("@cf/") }
        assertTrue(first.free)
        assertEquals(8192, first.contextLength)
        assertEquals(true, first.tools)
        assertFalse(models.first { it.id.startsWith("@hf/") }.free)
        assertFalse(ModelListParser.cloudflareHasMore(body, 1, 50))
    }

    @Test
    fun githubModelsAreNotFetchedAsLive() {
        assertFalse(ModelFree.isFree(ProviderKinds.GITHUB_MODELS, "gpt-4o", "0", "0"))
        val builtin = BuiltinModels.forKind(ProviderKinds.GITHUB_MODELS)
        assertTrue(builtin.isEmpty())
        val withSaved = BuiltinModels.fallback(ProviderKinds.OPENAI, "gpt-4.1")
        assertEquals("gpt-4.1", withSaved.single().id)
        assertFalse(withSaved.single().free)
        assertEquals(ModelSource.BUILTIN, withSaved.single().source)
    }
}