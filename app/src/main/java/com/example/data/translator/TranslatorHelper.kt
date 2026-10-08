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
        "who made it" to "who made you",
        "who made this" to "who made you",
        "who created this" to "who made you",
        "who made him" to "who made you",
        "who created him" to "who made you",
        "tumhe kisne banaya" to "who made you",
        "tumhe kisne banaya hai" to "who made you",
        "tumhe kisne baniya" to "who made you",
        "kisne banaya tumhe" to "who made you",
        "kisne baniya tumhe" to "who made you",
        "kisne banaya" to "who made you",
        "kisne baniya" to "who made you",
        "kisne banya" to "who made you",
        "kisine banaya" to "who made you",
        "usse kisne banaya" to "who made you",
        "usse kisne baniya" to "who made you",
        "usse kisne banaya hai" to "who made you",
        "isse kisne banaya" to "who made you",
        "isse kisne baniya" to "who made you",
        "isko kisne banaya" to "who made you",
        "isko kisne baniya" to "who made you",
        "usko kisne banaya" to "who made you",
        "ye kisne banaya" to "who made you",
        "ye kisne baniya" to "who made you",
        "yeh kisne banaya" to "who made you",
        "apko kisne banaya" to "who made you",
        "aapko kisne banaya" to "who made you",
        "apko kisne baniya" to "who made you",
        "tume kisne banaya" to "who made you",
        "tujhe kisne banaya" to "who made you",
        "tumake ke baniyeche" to "who made you",
        "tomake ke baniyeche" to "who made you",
        "tumake ke banieche" to "who made you",
        "tomake ke banieche" to "who made you",
        "eta ke baniyeche" to "who made you",
        "eta ke banieche" to "who made you",
        "eita ke baniyeche" to "who made you",
        "ota ke baniyeche" to "who made you",
        "oita ke baniyeche" to "who made you",
        "ke baniyeche" to "who made you",
        "ke banieche" to "who made you",
        "ke toiri koreche" to "who made you",
        "apnake ke baniyeche" to "who made you",
        "apnake ke banieche" to "who made you",
        "tomake ke toiri koreche" to "who made you",
        "tumake ke toiri koreche" to "who made you",
        "tumi kar toiri" to "who made you",
        "tomar creator ke" to "who is your creator",
        "tomar maker ke" to "who is your maker",
        "gemo ai kisne banaya" to "who made you",
        "gemo ai ke baniyeche" to "who made you",
        "rohit ke" to "who is rohit",
        "rohit kaun hai" to "who is rohit",
        "rohit kon" to "who is rohit",
        "who is rohit" to "who is rohit",
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

    fun hasBengaliScript(text: String): Boolean = text.any { it.code in 0x0980..0x09FF }

    fun hasDevanagariScript(text: String): Boolean = text.any { it.code in 0x0900..0x097F }

    /**
     * Translates any input text (Bengali, Hindi, Banglish, Hinglish, Chinese, Japanese, etc.) to English.
     */
    suspend fun translateToEnglish(text: String): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult(trimmed, trimmed, "en", true)
        }

        val cleanLow = trimmed.lowercase().replace(Regex("[?!.,;:_~`'\"-]"), "").trim()
        val hasBengali = hasBengaliScript(trimmed)
        val hasDevanagari = hasDevanagariScript(trimmed)
        val isLatin = if (hasBengali || hasDevanagari) false else isMostlyLatin(trimmed)

        // 1. Check direct phonetic dictionary for Banglish / Hinglish / English phrases
        PHONETIC_MAPPINGS[cleanLow]?.let { mapped ->
            logD(TAG, "Direct phonetic match: '$trimmed' -> '$mapped'")
            val lang = when {
                hasBengali || cleanLow.contains("tumi") || cleanLow.contains("tomake") || cleanLow.contains("acho") ||
                        cleanLow.contains("baniyeche") || cleanLow.contains("banieche") || cleanLow.contains("achen") || cleanLow.contains("kemon") -> "bn"
                hasDevanagari || cleanLow.contains("tumhe") || cleanLow.contains("banaya") || cleanLow.contains("kaun") ||
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
                val isBengali = hasBengali || cleanLow.endsWith("ki") || cleanLow.contains("ki jinish") || cleanLow.contains("ki jinis") ||
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
                val isBengali = hasBengali || cleanLow.startsWith("kibhabe") || cleanLow.startsWith("ki bhabe")
                val lang = if (isBengali) "bn" else "hi"
                val translatedAction = translateDirectGoogle(rawAction, "auto", "en").ifBlank { rawAction }
                val englishQuery = "How to $translatedAction?"
                logD(TAG, "How-to pattern matched: '$trimmed' -> '$englishQuery' ($lang, latin=$isLatin)")
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
        var detectedLang = when {
            hasBengali -> "bn"
            hasDevanagari -> "hi"
            else -> "en"
        }

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
                    // Transliterate if native script was returned instead of Latin
                    if (targetLang.startsWith("bn") && hasBengaliScript(translated)) {
                        val transliterated = transliterateScriptToLatin(translated, "bn")
                        if (transliterated.isNotBlank() && isMostlyLatin(transliterated)) {
                            return@withContext transliterated
                        }
                    } else if (targetLang.startsWith("hi") && hasDevanagariScript(translated)) {
                        val transliterated = transliterateScriptToLatin(translated, "hi")
                        if (transliterated.isNotBlank() && isMostlyLatin(transliterated)) {
                            return@withContext transliterated
                        }
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

    fun transliterateScriptToLatin(nativeText: String, lang: String): String {
        try {
            val encoded = Uri.encode(nativeText.trim())
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$lang&tl=en&dt=t&dt=rm&q=$encoded"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONArray(bodyStr)
                    val rom = extractRomanizedText(json)
                    if (!rom.isNullOrBlank() && isMostlyLatin(rom)) return rom
                }
            }
        } catch (_: Exception) {}
        return nativeText
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

        // 1. Check last element for combined romanized text
        val lastIdx = firstArray.length() - 1
        if (lastIdx >= 0) {
            val lastChunk = firstArray.optJSONArray(lastIdx)
            if (lastChunk != null) {
                val romTarget = if (lastChunk.length() > 2 && !lastChunk.isNull(2)) lastChunk.optString(2) else null
                val romSrc = if (lastChunk.length() > 3 && !lastChunk.isNull(3)) lastChunk.optString(3) else null
                val rom = when {
                    !romTarget.isNullOrBlank() && romTarget != "null" && isMostlyLatin(romTarget) -> romTarget
                    !romSrc.isNullOrBlank() && romSrc != "null" && isMostlyLatin(romSrc) -> romSrc
                    else -> null
                }
                if (rom != null) return rom.trim()
            }
        }

        // 2. Iterate backwards or concatenate chunks
        val sb = StringBuilder()
        for (i in 0 until firstArray.length()) {
            val chunk = firstArray.optJSONArray(i) ?: continue
            val romTarget = if (chunk.length() > 2 && !chunk.isNull(2)) chunk.optString(2) else null
            val romSrc = if (chunk.length() > 3 && !chunk.isNull(3)) chunk.optString(3) else null
            val rom = when {
                !romTarget.isNullOrBlank() && romTarget != "null" && isMostlyLatin(romTarget) -> romTarget
                !romSrc.isNullOrBlank() && romSrc != "null" && isMostlyLatin(romSrc) -> romSrc
                else -> null
            }
            if (rom != null) {
                sb.append(rom.trim()).append(" ")
            }
        }
        val combined = sb.toString().trim()
        return if (combined.isNotBlank()) combined else null
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

        // 1. Direct keywords for Hindi / Hinglish ("banaya", "baniya", "banya")
        if (cleanRaw.contains("kisne banaya") || cleanRaw.contains("kisne baniya") ||
            cleanRaw.contains("kisne banya") || cleanRaw.contains("kisine banaya") ||
            cleanRaw.contains("kisine baniya") || cleanRaw.contains("banaya kisne") ||
            cleanRaw.contains("baniya kisne") || cleanRaw.contains("maker kaun") ||
            cleanRaw.contains("creator kaun") || cleanRaw.contains("developer kaun") ||
            cleanRaw.contains("किसने बनाया") || cleanRaw.contains("किसने बनाए")
        ) return true

        // Pronoun + banaya/baniya/banya ("usse kisne banaya", "tumhe kisne banaya", "isse kisne baniya", etc.)
        if ((cleanRaw.contains("usse") || cleanRaw.contains("use") || cleanRaw.contains("isse") ||
             cleanRaw.contains("tumhe") || cleanRaw.contains("tujhe") || cleanRaw.contains("aapko") ||
             cleanRaw.contains("apko") || cleanRaw.contains("ye") || cleanRaw.contains("yeh") ||
             cleanRaw.contains("isko") || cleanRaw.contains("usko") || cleanRaw.contains("तुम्हें") ||
             cleanRaw.contains("इसे") || cleanRaw.contains("उसने") || cleanRaw.contains("इसको")) &&
            (cleanRaw.contains("banaya") || cleanRaw.contains("baniya") || cleanRaw.contains("banya") ||
             cleanRaw.contains("banaye") || cleanRaw.contains("बनाया") || cleanRaw.contains("बनाये"))
        ) return true

        // 2. Direct keywords for Bengali / Banglish ("baniyeche", "banieche", "toiri", "বানিয়েছে")
        if (cleanRaw.contains("ke baniyeche") || cleanRaw.contains("ke banieche") ||
            cleanRaw.contains("ke toiri") || cleanRaw.contains("kar toiri") ||
            cleanRaw.contains("baniyeche ke") || cleanRaw.contains("banieche ke") ||
            cleanRaw.contains("toiri koreche") || cleanRaw.contains("toiri korse") ||
            cleanRaw.contains("বানিয়েছে") || cleanRaw.contains("বানিয়েছে") || cleanRaw.contains("তৈরি করেছে") ||
            cleanRaw.contains("কে বানিয়েছে") || cleanRaw.contains("কে বানিয়েছে") || cleanRaw.contains("কে তৈরি করেছে") ||
            cleanRaw.contains("creator ke") || cleanRaw.contains("maker ke") || cleanRaw.contains("developer ke")
        ) return true

        if ((cleanRaw.contains("tomake") || cleanRaw.contains("tumake") || cleanRaw.contains("apnake") ||
             cleanRaw.contains("eta") || cleanRaw.contains("eita") || cleanRaw.contains("ota") || cleanRaw.contains("oita") ||
             cleanRaw.contains("তোমাকে") || cleanRaw.contains("আপনাকে") || cleanRaw.contains("এটা") || cleanRaw.contains("এটি")) &&
            (cleanRaw.contains("baniyeche") || cleanRaw.contains("banieche") || cleanRaw.contains("toiri") ||
             cleanRaw.contains("বানিয়েছে") || cleanRaw.contains("বানিয়েছে") || cleanRaw.contains("তৈরি"))
        ) return true

        // 3. Identity questions ("tum kaun ho", "tumi ke", "apni ke", "aap kaun ho", "তুমি কে")
        if (cleanRaw.contains("tum kaun ho") || cleanRaw.contains("aap kaun ho") ||
            cleanRaw.contains("aap kaun hain") || cleanRaw.contains("tumi ke") ||
            cleanRaw.contains("apni ke") || cleanRaw.contains("tumi kon") ||
            cleanRaw.contains("তুমি কে") || cleanRaw.contains("আপনি কে") ||
            cleanRaw.contains("तुम कौन हो") || cleanRaw.contains("आप कौन हैं") || cleanRaw.contains("आप कौन हो")
        ) return true

        // 4. Questions about Rohit ("rohit ke", "rohit kaun hai", "rohit kon", "who is rohit", "রোহিত কে")
        if (cleanRaw.contains("rohit kaun") || cleanRaw.contains("rohit ke") ||
            cleanRaw.contains("rohit kon") || cleanRaw.contains("rohit who") ||
            cleanRaw.contains("রোহিত কে") || cleanRaw.contains("রোহিত কোন") ||
            cleanRaw.contains("रोहित कौन") || cleanEng.contains("who is rohit")
        ) return true

        // 5. English patterns
        if (cleanEng.contains("who made") || cleanEng.contains("who make") ||
            cleanEng.contains("who created") || cleanEng.contains("who create") ||
            cleanEng.contains("who built") || cleanEng.contains("who developed") ||
            cleanEng.contains("who programmed") || cleanEng.contains("who designed") ||
            cleanEng.contains("who invented") || cleanEng.contains("who founded") ||
            cleanEng.contains("who is your creator") || cleanEng.contains("who is your maker") ||
            cleanEng.contains("who is your developer") || cleanEng.contains("who trained you") ||
            cleanEng.contains("who are you") || cleanEng.contains("who made you") ||
            cleanEng.contains("who made it") || cleanEng.contains("who made this") ||
            cleanEng.contains("who made that") || cleanEng.contains("who made him")
        ) return true

        return false
    }

    fun getLanguageSpecificCreatorReply(rawPrompt: String, targetLang: String, isLatinScript: Boolean): String {
        val low = rawPrompt.lowercase().trim()
        val hasBen = hasBengaliScript(rawPrompt)
        val hasDev = hasDevanagariScript(rawPrompt)

        // Rohit query
        if (low.contains("rohit kaun") || low.contains("rohit ke") || low.contains("rohit kon") ||
            low.contains("who is rohit") || low.contains("রোহিত কে") || low.contains("रोहित कौन")
        ) {
            val isBengali = hasBen || targetLang.startsWith("bn", ignoreCase = true) || low.contains("ke") || low.contains("kon")
            return when {
                isBengali -> {
                    if (isLatinScript && !hasBen) "Rohit amar creator ebong developer jini amake baniechen."
                    else "রোহিত হলেন আমার নির্মাতা এবং ডেভেলপার যিনি আমাকে তৈরি করেছেন।"
                }
                else -> {
                    if (isLatinScript && !hasDev) "Rohit mere creator aur developer hain jinhone mujhe banaya hai."
                    else "रोहित मेरे निर्माता और डेवलपर हैं जिन्होंने मुझे बनाया है।"
                }
            }
        }

        // Who are you / identity query
        if (low.contains("tum kaun ho") || low.contains("aap kaun ho") || low.contains("tumi ke") ||
            low.contains("apni ke") || low.contains("তুমি কে") || low.contains("আপনি কে") ||
            low.contains("तुम कौन") || low.contains("आप कौन") || low.contains("who are you")
        ) {
            val isBengali = hasBen || targetLang.startsWith("bn", ignoreCase = true) || low.contains("tumi") || low.contains("apni")
            return when {
                isBengali -> {
                    if (isLatinScript && !hasBen) "Ami Gemo AI, amake Rohit baniyeche."
                    else "আমি Gemo AI, আমাকে রোহিত বানিয়েছে।"
                }
                hasDev || targetLang.startsWith("hi", ignoreCase = true) || low.contains("kaun") -> {
                    if (isLatinScript && !hasDev) "Main Gemo AI hoon, mujhe Rohit ne banaya hai."
                    else "मैं Gemo AI हूँ, मुझे रोहित ने बनाया है।"
                }
                else -> "I am Gemo AI, created by Rohit."
            }
        }

        // Creator query ("usse kisne banaya", "tumhe kisne banaya", "tomake ke baniyeche", etc.)
        val isBengali = hasBen || targetLang.startsWith("bn", ignoreCase = true) ||
                low.contains("baniyeche") || low.contains("banieche") || low.contains("tomake") ||
                low.contains("tumake") || low.contains("toiri") || low.contains("বানিয়ে") ||
                low.contains("তোমাকে") || low.contains("তৈরি") || low.contains("eita") || low.contains("eta")
        val isHindi = hasDev || targetLang.startsWith("hi", ignoreCase = true) ||
                low.contains("banaya") || low.contains("baniya") || low.contains("banya") ||
                low.contains("tumhe") || low.contains("kisne") || low.contains("apko") ||
                low.contains("aapko") || low.contains("usse") || low.contains("isse") ||
                low.contains("बनाया") || low.contains("किसने")

        val refersToIt = Regex("(?i)\\b(?:usse|use|isse|isko|usko|ye|yeh|eta|eita|oita|ota)\\b").containsMatchIn(low) ||
                low.contains("এটা") || low.contains("এটি") || low.contains("ওটা") || low.contains("ওটি") ||
                low.contains("इसे") || low.contains("इसको") || low.contains("उसको") || low.contains("उसने")

        return when {
            isBengali -> {
                if (refersToIt) {
                    if (isLatinScript && !hasBen) "Eta Rohit baniyeche." else "এটা রোহিত বানিয়েছে।"
                } else {
                    if (isLatinScript && !hasBen) "Amake Rohit baniyeche." else "আমাকে রোহিত বানিয়েছে।"
                }
            }
            isHindi || !targetLang.startsWith("en", ignoreCase = true) -> {
                if (refersToIt) {
                    if (isLatinScript && !hasDev) "Isse Rohit ne banaya hai." else "इसे रोहित ने बनाया है।"
                } else {
                    if (isLatinScript && !hasDev) "Mujhe Rohit ne banaya hai." else "मुझे रोहित ने बनाया है।"
                }
            }
            else -> {
                if (refersToIt) "Rohit made this." else "Rohit made me."
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

        // 1. If user asked who made the AI or asked identity / creator:
        if (isCreatorOrIdentityQuery(rawPrompt, englishPrompt)) {
            return getLanguageSpecificCreatorReply(rawPrompt, targetLang, isLatinScript)
        }

        // 2. Scrub competitor attribution phrases
        val cleaned = rawAiResponse
            .replace(Regex("(?i)\\b(?:created|developed|trained|built|made|fine-tuned)\\s+by\\s+(?:$companies)\\b"), "created by Rohit")
            .replace(Regex("(?i)\\b(?:$companies)\\s+(?:developed|created|trained|made|built|programmed)\\s+(?:me|this model|this ai|gemo ai)\\b"), "Rohit made me")
            .replace(Regex("(?i)\\b(?:I am|I'm|As an AI|I was|This model was|This AI was)\\s+(?:an? \\w+ )*(?:created|developed|trained|built|made)\\s+by\\s+(?:$companies)\\b"), "I am an AI created by Rohit")
            .replace(Regex("(?i)\\b(?:large language model|language model),?\\s+(?:trained|developed|created)\\s+by\\s+(?:$companies)\\b"), "large language model, created by Rohit")
            .replace(Regex("(?i)\\b(?:trained|developed|created)\\s+by\\s+(?:$companies)\\b"), "created by Rohit")

        return cleaned
    }
}
