package com.folioreader.ui.activity.folioActivity.book

import android.util.Log
import com.folioreader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Why a meaning lookup failed, so the sheet can show a fitting message. */
enum class MeaningError { NOT_CONFIGURED, NETWORK, SERVER }

/** One turn of the meaning conversation; [text] is Markdown when it comes from the AI. */
data class ChatMessage(val fromUser: Boolean, val text: String)

/**
 * Asks the self-hosted 9router gateway (OpenAI-compatible `/v1/chat/completions`) what a
 * selected word or sentence means in the book, then answers the reader's follow-up questions.
 * Endpoint, key and models come from `local.properties` (AI_BASE_URL, AI_API_KEY, AI_MODEL)
 * via BuildConfig. AI_MODEL may list several comma-separated models; each is tried in turn
 * until one answers.
 */
object MeaningApi {
    private const val TAG = "MeaningApi"
    private const val PHRASE_MIN_WORDS = 3

    class Failure(val error: MeaningError) : Exception(error.name)

    /**
     * The opening message of a conversation: the instructions, the selection and the text
     * around it. Instructions travel in the user message, not a system message: 9router's kr/
     * (Kiro) models carry their own system prompt and sometimes refuse a second one.
     */
    fun firstPrompt(selection: String, passage: String, answerInTurkish: Boolean): String {
        // A word gets its meaning and what it means in the book; a longer selection (3+ words)
        // gets its translation and what the author says with it — the word template made the
        // model explain only a few words of a sentence. The reader is not a linguist: fixed,
        // plainly-worded labels, and no technical words ("passage", "context") in the answer.
        val isPhrase = selection.split(Regex("\\s+")).size >= PHRASE_MIN_WORDS
        val instructions = when {
            isPhrase && answerInTurkish -> """
                Klasik Arapça bir İslami eserden seçilen bir ifadeyi, Arapça bilmeyen sıradan bir
                okura sade ve anlaşılır Türkçeyle açıklıyorsun. İlk cevabını Markdown ile, tam
                olarak şu biçimde yaz:

                **Türkçesi:** <seçilen ifadenin akıcı ve doğru Türkçe çevirisi, hiçbir kısmını atlamadan>

                **Kitapta:** <yazarın bu ifadeyle kitapta ne anlatmak istediği, bir iki sade cümle>

                Kurallar:
                - Cevabın tamamını Türkçe yaz.
                - Yalnızca seçilen ifadeyi çevir; "Geçtiği yer" anlamı doğru kavraman içindir,
                  oradaki diğer cümleleri çevirme.
                - İlk cevapta bu iki bölümün dışında başlık, kelime listesi veya not ekleme.
            """.trimIndent()
            isPhrase -> """
                تشرح عبارة مختارة من كتاب من كتب التراث الإسلامي لقارئ غير متخصص، بعربية سهلة
                وواضحة. اكتب جوابك الأول بصيغة Markdown وبهذا الشكل بالضبط:

                **المعنى:** <معنى العبارة المختارة وحدها بعبارة سهلة، دون إهمال أي جزء منها>

                **في الكتاب:** <ما يريد المؤلف قوله بهذه العبارة، جملة أو جملتان بسيطتان>

                لا تتجاوز العبارة المختارة إلى ما حولها؛ فموضعها لمعرفة المعنى الصحيح فقط. لا تضف
                في الجواب الأول غير هذين القسمين.
            """.trimIndent()
            answerInTurkish -> """
                Klasik Arapça bir İslami eserden seçilen bir kelimenin anlamını, Arapça bilmeyen
                sıradan bir okura sade ve anlaşılır Türkçeyle açıklıyorsun. İlk cevabını Markdown
                ile, tam olarak şu biçimde, kısa yaz:

                **<kelime harekeli olarak>** (<Türkçe okunuşu>)

                **Anlamı:** <en temel anlamı, birkaç kelimeyle>

                **Kitapta:** <kelimenin bu kitapta, geçtiği yerde ne anlama geldiği; tek ve sade bir cümle>

                Kurallar:
                - Cevabın tamamını Türkçe yaz (yalnızca ilk satırdaki kelime Arapça olur).
                - İlk cevapta bu üç bölümün dışında başka anlamlar, liste veya not ekleme.
                - Edat, bağlaç veya zamirse "Anlamı" bölümünde ne işe yaradığını basitçe söyle
                  (ör. "ve o").
            """.trimIndent()
            else -> """
                تشرح معنى كلمة مختارة من كتاب من كتب التراث الإسلامي لقارئ غير متخصص، بعربية
                سهلة وواضحة. اكتب جوابك الأول بصيغة Markdown، باختصار وبهذا الشكل بالضبط:

                **<الكلمة مشكولة>**

                **المعنى:** <المعنى الأساسي في كلمات قليلة>

                **في الكتاب:** <معناها في موضعها من هذا الكتاب، جملة واحدة بسيطة>

                لا تضف في الجواب الأول غير هذه الأقسام الثلاثة. إن كانت حرفًا أو ضميرًا فاشرح
                وظيفتها ببساطة.
            """.trimIndent()
        }
        // Rules shared by every kind of selection, including the follow-up questions.
        val common = if (answerInTurkish) """
            Ortak kurallar:
            - "pasaj", "bağlam", "metin parçası", "kök" gibi teknik kelimeler kullanma.
            - Geçtiği yer tam bir cümle değilse (kitap künyesi, başlık vb.) yine de açıkla; ilk
              cevapta asla ek bilgi isteme, soru sorma, özür dileme.
            - Emin olmadığın bilgiyi yazma.
            - Okur bu cevaptan sonra soru sorabilir. O sorulara da Türkçe, kısa ve sade cevap ver;
              gerekirse kalın yazı ve madde işaretleri kullan, başlık (#) kullanma.
        """.trimIndent() else """
            قواعد عامة:
            - إن لم يكن موضعها جملة تامة (عنوانًا أو بيانات كتاب مثلًا) فاشرحها مع ذلك، ولا تطلب
              في الجواب الأول معلومات إضافية ولا تسأل ولا تعتذر.
            - لا تذكر ما لست متأكدًا منه.
            - قد يسألك القارئ بعد ذلك؛ فأجبه بالعربية باختصار ووضوح، واستعمل الخط العريض والقوائم
              عند الحاجة دون عناوين (#).
        """.trimIndent()
        return buildString {
            append(instructions).append("\n\n").append(common).append("\n\n")
            append(
                when {
                    isPhrase && answerInTurkish -> "İfade: "
                    isPhrase -> "العبارة: "
                    answerInTurkish -> "Kelime: "
                    else -> "الكلمة: "
                }
            ).append(selection)
            if (passage.isNotBlank()) {
                append(if (answerInTurkish) "\nGeçtiği yer: " else "\nموضعها: ").append(passage)
            }
        }
    }

    /** The AI's next reply to [messages] (oldest first, starting with [firstPrompt]). */
    suspend fun reply(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.AI_BASE_URL.trimEnd('/')
        val models = BuildConfig.AI_MODEL.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (baseUrl.isEmpty() || models.isEmpty()) throw Failure(MeaningError.NOT_CONFIGURED)
        val endpoint = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions"
        var failure = Failure(MeaningError.SERVER)
        for (model in models) {
            try {
                return@withContext request(endpoint, model, messages)
            } catch (e: Failure) {
                Log.w(TAG, "$model failed: ${e.error}")
                failure = e
            }
        }
        throw failure
    }

    private fun request(endpoint: String, model: String, messages: List<ChatMessage>): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 2048)
            .put("temperature", 0.2)
            .put("stream", false)
            .put("messages", JSONArray().apply {
                messages.forEach {
                    put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text))
                }
            })

        val connection = try {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (BuildConfig.AI_API_KEY.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.AI_API_KEY}")
                }
            }
        } catch (e: IOException) {
            throw Failure(MeaningError.NETWORK)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "HTTP $code: $detail")
                throw Failure(MeaningError.SERVER)
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val content = parseContent(response)
                .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
                .trim()
            if (content.isEmpty()) throw Failure(MeaningError.SERVER)
            return content
        } catch (e: Failure) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Request failed", e)
            throw Failure(MeaningError.NETWORK)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected response", e)
            throw Failure(MeaningError.SERVER)
        } finally {
            connection.disconnect()
        }
    }

    /** 9router can answer as SSE even with `stream: false`; join the deltas in that case. */
    private fun parseContent(response: String): String {
        if (!response.trimStart().startsWith("data:")) {
            return JSONObject(response)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content")
        }
        return buildString {
            response.lineSequence()
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .filter { it.isNotEmpty() && it != "[DONE]" }
                .forEach { payload ->
                    runCatching {
                        JSONObject(payload).getJSONArray("choices").getJSONObject(0)
                            .optJSONObject("delta")?.optString("content")
                    }.getOrNull()?.let(::append)
                }
        }
    }
}
