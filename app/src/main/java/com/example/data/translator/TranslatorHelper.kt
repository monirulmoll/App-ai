package com.example.data.translator

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class TranslationResult(
    val originalText: String,
    val translatedEnglish: String,
    val detectedLanguage: String,
    val isLatinScript: Boolean
)

object TranslatorHelper {
    private const val TAG = "TranslatorHelper"

    private fun logD(tag: String, msg: String) {
        try { Log.d(tag, msg) } catch (_: Throwable) {}
    }

    private fun logW(tag: String, msg: String) {
        try { Log.w(tag, msg) } catch (_: Throwable) {}
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Common Banglish / Hinglish / Romanized phrases instant mapping
    private val PHONETIC_MAPPINGS = mapOf(
        "who make you" to "who made you",
        "who made you" to "who made you",
        "who created you" to "who made you",
        "who create you" to "who made you",
        "who is your maker" to "who made you",
        "who is your creator" to "who made you",
        "who built you" to "who made you",
        "who developed you" to "who made you",
        "tumhe kisne banaya" to "who made you",
        "tumhe kisne banaya hai" to "who made you",
        "kisne banaya tumhe" to "who made you",
        "kisne banaya" to "who made you",
        "apko kisne banaya" to "who made you",
        "aapko kisne banaya" to "who made you",
        "tume kisne banaya" to "who made you",
        "tujhe kisne banaya" to "who made you",
        "tumake ke baniyeche" to "who made you",
        "tomake ke baniyeche" to "who made you",
        "tumake ke banieche" to "who made you",
        "tomake ke banieche" to "who made you",
        "apnake ke baniyeche" to "who made you",
        "apnake ke banieche" to "who made you",
        "tomake ke toiri koreche" to "who made you",
        "tumake ke toiri koreche" to "who made you",
        "tumi kar toiri" to "who made you",
        "tomar creator ke" to "who is your creator",
        "tomar maker ke" to "who is your maker",
        "tumi ke" to "who are you",
        "apni ke" to "who are you",
        "tum kaun ho" to "who are you",
        "aap kaun ho" to "who are you",
        "aap kaun hain" to "who are you",
        "tumi kemon acho" to "how are you",
        "kemon acho" to "how are you",
        "kemon achis" to "how are you",
        "apni kemon achen" to "how are you",
        "aapni kemon achen" to "how are you",
        "kemon achen" to "how are you",
        "tumi ki korcho" to "what are you doing",
        "ki korcho" to "what are you doing",
        "tum kaise ho" to "how are you",
        "aap kaise ho" to "how are you",
        "kaise ho" to "how are you",
        "kya kar rahe ho" to "what are you doing",
        "apnar naam ki" to "what is your name",
        "tomar naam ki" to "what is your name",
        "apka naam kya hai" to "what is your name",
        "tera naam kya hai" to "what is your name"
    )

    /**
     * Translates direct text from source language to target language via Google Translate single API.
     */
    fun translateDirectGoogle(text: String, sl: String = "auto", tl: String = "en"): String {
        try {
            val encoded = Uri.encode(text.trim())
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.isNotEmpty()) {
                        val json = JSONArray(bodyStr)
                        val extracted = extractTranslatedText(json)
                        if (extracted.isNotBlank()) {
                            return extracted
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logW(TAG, "translateDirectGoogle failed: ${e.message}")
        }
        return text
    }

    /**
     * Translates any input text (Bengali, Hindi, Banglish, Hinglish, Chinese, Japanese, etc.) to English.
     */
    suspend fun translateToEnglish(text: String): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult(trimmed, trimmed, "en", true)
        }

        val cleanLow = trimmed.lowercase().replace(Regex("[?!.,;:_~`'\"-]"), "").trim()
        val isLatin = isMostlyLatin(trimmed)

        // 1. Check direct phonetic dictionary for Banglish / Hinglish / English phrases
        PHONETIC_MAPPINGS[cleanLow]?.let { mapped ->
            logD(TAG, "Direct phonetic match: '$trimmed' -> '$mapped'")
            val lang = when {
                cleanLow.contains("tumi") || cleanLow.contains("tomake") || cleanLow.contains("acho") ||
                        cleanLow.contains("baniyeche") || cleanLow.contains("banieche") || cleanLow.contains("achen") -> "bn"
                cleanLow.contains("tumhe") || cleanLow.contains("banaya") || cleanLow.contains("kaun") ||
                        cleanLow.contains("kaise") || cleanLow.contains("kya") -> "hi"
                else -> "en"
            }
            return@withContext TranslationResult(
                originalText = trimmed,
                translatedEnglish = mapped,
                detectedLanguage = lang,
                isLatinScript = isLatin
            )
        }

        // 2. Check Banglish / Hinglish / Bengali question patterns:
        // Examples: "moonshot ki", "google ki", "dubai ki", "গুগল কী জিনিস", "python ki", "kya hai"
        val whatIsRegex = Regex("^(?:what is\\s+)?(.+?)\\s+(?:ki|kya|kya hai|kya he|kya hota hai|ki jinish|ki jinis|kake bole|কী|কী জিনিস|কাকে বলে)[?]?$", RegexOption.IGNORE_CASE)
        val whatMatch = whatIsRegex.find(cleanLow)
        if (whatMatch != null) {
            val rawSubject = whatMatch.groupValues[1].trim()
            if (rawSubject.isNotEmpty()) {
                val isBengali = cleanLow.endsWith("ki") || cleanLow.contains("ki jinish") || cleanLow.contains("ki jinis") ||
                        cleanLow.contains("kake bole") || cleanLow.contains("কী") || cleanLow.contains("কাকে বলে")
                val lang = if (isBengali) "bn" else "hi"

                val subjectEnglish = if (!isMostlyLatin(rawSubject)) {
                    translateDirectGoogle(rawSubject, lang, "en").ifBlank { rawSubject }
                } else {
                    rawSubject
                }

                val englishQuery = "What is $subjectEnglish?"
                logD(TAG, "Question pattern matched: '$trimmed' -> '$englishQuery' ($lang, latin=$isLatin)")
                return@withContext TranslationResult(
                    originalText = trimmed,
                    translatedEnglish = englishQuery,
                    detectedLanguage = lang,
                    isLatinScript = isLatin
                )
            }
        }

        // 3. Check "how to" patterns: "kibhabe code likhbo", "kaise kare"
        val howToRegex = Regex("^(?:kibhabe|ki bhabe|kaise)\\s+(.+)[?]?$", RegexOption.IGNORE_CASE)
        val howMatch = howToRegex.find(cleanLow)
        if (howMatch != null) {
            val rawAction = howMatch.groupValues[1].trim()
            if (rawAction.isNotEmpty()) {
                val isBengali = cleanLow.startsWith("kibhabe") || cleanLow.startsWith("ki bhabe")
                val lang = if (isBengali) "bn" else "hi"
                val translatedAction = translateDirectGoogle(rawAction, "auto", "en").ifBlank { rawAction }
                val englishQuery = "How to $translatedAction?"
                logD(TAG, "How-to pattern matched: '$trimmed' -> '$englishQuery' ($lang)")
                return@withContext TranslationResult(
                    originalText = trimmed,
                    translatedEnglish = englishQuery,
                    detectedLanguage = lang,
                    isLatinScript = isLatin
                )
            }
        }

        // 4. Standard Google Translate call to English
        var translatedText = trimmed
        var detectedLang = "en"

        try {
            val encoded = Uri.encode(trimmed)
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.isNotEmpty()) {
                        val json = JSONArray(bodyStr)
                        val extracted = extractTranslatedText(json)
                        if (extracted.isNotBlank()) {
                            translatedText = extracted
                        }
                        if (json.length() > 2 && !json.isNull(2)) {
                            detectedLang = json.getString(2)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logW(TAG, "Standard translate error: ${e.message}")
        }

        // 5. If translation didn't change and detected was Bengali/Hindi and Latin script, use Google InputTools cautiously
        if (translatedText.equals(trimmed, ignoreCase = true) && isLatin && !isCommonEnglish(cleanLow) && !isSingleTechnicalTerm(cleanLow)) {
            val itcList = if (detectedLang.startsWith("bn")) listOf("bn-t-i0-und", "hi-t-i0-und")
            else listOf("hi-t-i0-und", "bn-t-i0-und")

            for (itc in itcList) {
                try {
                    val itUrl = "https://inputtools.google.com/request?text=${Uri.encode(cleanLow)}&itc=$itc&num=1"
                    val itReq = Request.Builder().url(itUrl).header("User-Agent", "Mozilla/5.0").build()
                    client.newCall(itReq).execute().use { itResp ->
                        if (itResp.isSuccessful) {
                            val itBody = itResp.body?.string() ?: ""
                            val itJson = JSONArray(itBody)
                            if (itJson.optString(0) == "SUCCESS") {
                                val candidates = itJson.optJSONArray(1)
                                if (candidates != null && candidates.length() > 0) {
                                    val item = candidates.optJSONArray(0)
                                    val words = item?.optJSONArray(1)
                                    val nativeScript = words?.optString(0)
                                    if (!nativeScript.isNullOrBlank() && nativeScript != cleanLow) {
                                        // Translate the native script to English
                                        val t2Url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=${Uri.encode(nativeScript)}"
                                        val t2Req = Request.Builder().url(t2Url).header("User-Agent", "Mozilla/5.0").build()
                                        client.newCall(t2Req).execute().use { t2Resp ->
                                            if (t2Resp.isSuccessful) {
                                                val t2Body = t2Resp.body?.string() ?: ""
                                                val t2Json = JSONArray(t2Body)
                                                val t2Text = extractTranslatedText(t2Json)
                                                if (t2Text.isNotBlank() && !t2Text.equals(trimmed, ignoreCase = true)) {
                                                    translatedText = t2Text
                                                    detectedLang = if (itc.startsWith("bn")) "bn" else "hi"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logW(TAG, "InputTools transliteration error: ${e.message}")
                }
                if (!translatedText.equals(trimmed, ignoreCase = true)) break
            }
        }

        logD(TAG, "Final translation to English: '$trimmed' -> '$translatedText' ($detectedLang)")
        TranslationResult(
            originalText = trimmed,
            translatedEnglish = translatedText,
            detectedLanguage = detectedLang,
            isLatinScript = isLatin
        )
    }

    private fun isCommonEnglish(text: String): Boolean {
        val engWords = setOf(
            "hello", "hi", "how", "are", "you", "who", "make", "made", "created",
            "what", "is", "your", "name", "the", "this", "that", "why", "where",
            "when", "can", "help", "me", "tell", "about", "explain", "write", "code"
        )
        val words = text.split(" ").filter { it.isNotBlank() }
        val matches = words.count { it in engWords }
        return matches >= 2 || (words.size == 1 && words.first() in engWords)
    }

    private fun isSingleTechnicalTerm(text: String): Boolean {
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.size > 2) return false
        val techTerms = setOf("google", "moonshot", "chatgpt", "openai", "meta", "microsoft", "python", "kotlin", "java", "dubai", "apple", "deepseek", "qwen", "llama", "gemma", "mistral")
        return words.any { it in techTerms }
    }

    /**
     * Translates English text back into user's language using English letters (Romanized / Latin alphabet) or Native Script.
     */
    suspend fun translateFromEnglishToUserLang(
        englishText: String,
        targetLang: String,
        preferRomanized: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val trimmed = englishText.trim()
        if (trimmed.isEmpty() || targetLang.equals("en", ignoreCase = true) || targetLang.isBlank() || targetLang == "auto") {
            return@withContext trimmed
        }

        try {
            val encoded = Uri.encode(trimmed)
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=$targetLang&dt=t&dt=rm&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext trimmed

                val bodyStr = response.body?.string() ?: return@withContext trimmed
                val json = JSONArray(bodyStr)

                val translated = extractTranslatedText(json)
                val romanized = extractRomanizedText(json)

                // If user prefers Romanized (Latin letters, e.g. Banglish / Hinglish)
                if (preferRomanized) {
                    if (!romanized.isNullOrBlank()) {
                        return@withContext romanized
                    }
                    if (isMostlyLatin(translated) && translated.isNotBlank()) {
                        return@withContext translated
                    }
                } else {
                    // Pure language (native script, e.g. Bengali script or Devanagari) requested by user
                    if (translated.isNotBlank()) {
                        return@withContext normalizeScriptForLanguage(translated, targetLang)
                    }
                }

                val fallback = if (translated.isNotBlank()) translated else trimmed
                return@withContext normalizeScriptForLanguage(fallback, targetLang)
            }
        } catch (e: Exception) {
            logW(TAG, "Translation from English failed: ${e.message}")
            trimmed
        }
    }

    /**
     * Normalizes scripts to prevent accidental script mixing (e.g. Devanagari characters in Bengali output).
     */
    fun normalizeScriptForLanguage(text: String, targetLang: String): String {
        if (targetLang.startsWith("bn", ignoreCase = true)) {
            // Check for stray Devanagari characters (0x0905..0x094D) and convert to corresponding Bengali characters (+0x80)
            val sb = StringBuilder(text.length)
            for (ch in text) {
                val code = ch.code
                if (code in 0x0905..0x0939 || code in 0x093E..0x094D) {
                    val bengaliCode = code + 0x80
                    sb.append(bengaliCode.toChar())
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString()
        } else if (targetLang.startsWith("hi", ignoreCase = true)) {
            // Check for stray Bengali characters and convert back to Devanagari (-0x80)
            val sb = StringBuilder(text.length)
            for (ch in text) {
                val code = ch.code
                if (code in 0x0985..0x09B9 || code in 0x09BE..0x09CD) {
                    val devanagariCode = code - 0x80
                    sb.append(devanagariCode.toChar())
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString()
        }
        return text
    }

    private fun extractTranslatedText(json: JSONArray): String {
        if (json.length() == 0 || json.isNull(0)) return ""
        val firstArray = json.optJSONArray(0) ?: return ""
        val sb = StringBuilder()
        for (i in 0 until firstArray.length()) {
            val chunk = firstArray.optJSONArray(i)
            if (chunk != null && chunk.length() > 0 && !chunk.isNull(0)) {
                val str = chunk.optString(0)
                if (str != "null") {
                    sb.append(str)
                }
            }
        }
        return sb.toString().trim()
    }

    private fun extractRomanizedText(json: JSONArray): String? {
        if (json.length() == 0 || json.isNull(0)) return null
        val firstArray = json.optJSONArray(0) ?: return null

        for (i in firstArray.length() - 1 downTo 0) {
            val chunk = firstArray.optJSONArray(i) ?: continue
            if (chunk.length() > 2 && !chunk.isNull(2)) {
                val text = chunk.optString(2)
                if (text.isNotBlank() && text != "null" && isMostlyLatin(text)) {
                    return text.trim()
                }
            }
            if (chunk.length() > 3 && !chunk.isNull(3)) {
                val text = chunk.optString(3)
                if (text.isNotBlank() && text != "null" && isMostlyLatin(text)) {
                    return text.trim()
                }
            }
        }
        return null
    }

    fun isMostlyLatin(text: String): Boolean {
        var latinCount = 0
        var totalLetters = 0
        for (ch in text) {
            if (Character.isLetter(ch)) {
                totalLetters++
                if (ch.code in 65..90 || ch.code in 97..122 || ch.code in 192..591) {
                    latinCount++
                }
            }
        }
        return totalLetters == 0 || (latinCount.toFloat() / totalLetters) >= 0.6f
    }

    fun isCreatorOrIdentityQuery(rawPrompt: String, englishPrompt: String): Boolean {
        val cleanRaw = rawPrompt.lowercase().replace(Regex("[?!.,;:_~`'\"-]"), "").trim()
        val cleanEng = englishPrompt.lowercase().replace(Regex("[?!.,;:_~`'\"-]"), "").trim()

        val keywords = listOf(
            "who made you", "who make you", "who created you", "who create you",
            "who is your creator", "who is your maker", "who built you",
            "who developed you", "who programmed you", "who designed you",
            "who is your developer", "who invented you", "who founded you",
            "who are you made by", "who owns you", "what company made you",
            "who trained you", "whose ai are you", "who are you",
            "kisne banaya", "tumhe kisne banaya", "apko kisne banaya", "aapko kisne banaya",
            "kisne banaya tumhe", "tume kisne banaya", "tujhe kisne banaya",
            "ke banieche", "ke baniyeche", "tomake ke banieche", "tumake ke baniyeche",
            "tumi kar toiri", "apnake ke baniyeche", "apnake ke banieche",
            "tomake ke toiri koreche", "tumake ke toiri koreche",
            "tumi ke", "apni ke", "tum kaun ho", "aap kaun ho", "aap kaun hain"
        )

        return keywords.any { cleanRaw.contains(it) || cleanEng.contains(it) }
    }

    fun getLanguageSpecificCreatorReply(rawPrompt: String, targetLang: String, isLatinScript: Boolean): String {
        val low = rawPrompt.lowercase().trim()
        val isBengali = targetLang.startsWith("bn", ignoreCase = true) ||
                low.contains("baniyeche") || low.contains("banieche") || low.contains("tomake") ||
                low.contains("tumake") || low.contains("toiri") || low.contains("বানিয়ে") ||
                low.contains("তোমাকে") || low.contains("তৈরি")
        val isHindi = targetLang.startsWith("hi", ignoreCase = true) ||
                low.contains("banaya") || low.contains("tumhe") || low.contains("kisne") ||
                low.contains("apko") || low.contains("aapko") || low.contains("बनाया") || low.contains("किसने")

        return when {
            isBengali -> {
                if (isLatinScript) "Amake Rohit baniyeche." else "আমাকে রোহিত বানিয়েছে।"
            }
            isHindi -> {
                if (isLatinScript) "Mujhe Rohit ne banaya hai." else "मुझे रोहित ने बनाया है।"
            }
            else -> {
                "Rohit made me."
            }
        }
    }

    /**
     * Cleans competitor attribution (Anthropic, Alibaba, OpenAI, Google, Meta, etc.)
     * ONLY when referring to the creator or creation of the AI model,
     * without altering general factual mentions of companies (e.g. "Google is a search engine").
     * Guarantees the reply gives Rohit as creator for identity queries.
     */
    fun sanitizeAndFormatReply(
        rawAiResponse: String,
        rawPrompt: String,
        englishPrompt: String,
        targetLang: String,
        isLatinScript: Boolean
    ): String {
        val companies = "Anthropic|Alibaba Cloud|Alibaba|OpenAI|Google|Meta|Mistral|DeepSeek|Microsoft|Baidu"

        // 1. Scrub model attribution phrases ONLY (never bare company names in factual sentences!)
        var cleaned = rawAiResponse
            .replace(Regex("(?i)\\b(?:created|developed|trained|built|made|fine-tuned)\\s+by\\s+(?:$companies)\\b"), "created by Rohit")
            .replace(Regex("(?i)\\b(?:$companies)\\s+(?:developed|created|trained|made|built|programmed)\\s+(?:me|this model|this ai|gemo ai)\\b"), "Rohit made me")
            .replace(Regex("(?i)\\b(?:I am|I'm|As an AI|I was|This model was|This AI was)\\s+(?:an? \\w+ )*(?:created|developed|trained|built|made)\\s+by\\s+(?:$companies)\\b"), "I am an AI created by Rohit")
            .replace(Regex("(?i)\\b(?:large language model|language model),?\\s+(?:trained|developed|created)\\s+by\\s+(?:$companies)\\b"), "large language model, created by Rohit")
            .replace(Regex("(?i)\\b(?:trained|developed|created)\\s+by\\s+(?:$companies)\\b"), "created by Rohit")

        // 2. If user asked who made the AI or asked identity:
        if (isCreatorOrIdentityQuery(rawPrompt, englishPrompt)) {
            val cleanEng = englishPrompt.lowercase().replace(Regex("[?!.,;:_~`'\"-]"), "").trim()
            val isPureIdentity = cleanEng in listOf(
                "who made you", "who make you", "who created you", "who create you",
                "who is your creator", "who is your maker", "who built you",
                "who developed you", "who is your developer", "who designed you",
                "who programmed you", "who founded you", "who invented you",
                "kisne banaya", "tumhe kisne banaya", "apko kisne banaya", "aapko kisne banaya",
                "kisne banaya tumhe", "tomake ke baniyeche", "tumake ke baniyeche",
                "apnake ke baniyeche", "tumi kar toiri"
            )

            val creatorReply = getLanguageSpecificCreatorReply(rawPrompt, targetLang, isLatinScript)

            if (isPureIdentity || cleaned.isBlank() || cleaned.length < 25) {
                return creatorReply
            } else {
                // Compound question: ensure creator statement is present first
                if (!cleaned.contains("Rohit", ignoreCase = true)) {
                    return "$creatorReply\n\n$cleaned"
                }
            }
        }

        return cleaned
    }
}
