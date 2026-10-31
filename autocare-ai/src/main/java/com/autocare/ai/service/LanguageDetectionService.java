package com.autocare.ai.service;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects the language of a query before conditional query transformation (SRS §2.1).
 * Returns a BCP-47 language tag, or {@code und} when the text is insufficient to determine one.
 */
@Service
public class LanguageDetectionService {

    private static final String UNDETERMINED = "und";
    private static final Pattern WORDS = Pattern.compile("[\\p{L}]+");
    private static final Map<String, Set<String>> LATIN_LANGUAGE_MARKERS = Map.of(
            "en", Set.of("a", "and", "are", "car", "for", "how", "is", "my", "of", "the", "what", "when", "with"),
            "es", Set.of("coche", "con", "cuál", "de", "el", "en", "es", "la", "los", "mi", "para", "qué", "un"),
            "fr", Set.of("au", "avec", "bonjour", "de", "des", "est", "la", "le", "les", "mon", "pour", "quelle"),
            "de", Set.of("auto", "das", "der", "die", "ein", "ist", "mit", "mein", "und", "was", "wie", "zu"),
            "it", Set.of("auto", "che", "con", "del", "della", "il", "la", "per", "quale", "un", "una"),
            "pt", Set.of("carro", "com", "da", "de", "do", "é", "meu", "para", "qual", "que", "uma"),
            "nl", Set.of("auto", "de", "een", "het", "hoe", "is", "mijn", "met", "voor", "wat"));
    private static final Set<Integer> UKRAINIAN_LETTERS =
            Set.of((int) 'і', (int) 'ї', (int) 'є', (int) 'ґ', (int) 'І', (int) 'Ї', (int) 'Є', (int) 'Ґ');

    public String detectLanguage(String query) {
        Assert.hasText(query, "query must not be blank");

        String scriptLanguage = detectDistinctScript(query);
        if (scriptLanguage != null) {
            return scriptLanguage;
        }

        return detectLatinLanguage(query);
    }

    /**
     * Uses a supplied BCP-47 language hint only when the query text itself is undetermined.
     */
    public String detectLanguage(String query, String languageHint) {
        String detectedLanguage = detectLanguage(query);
        if (!UNDETERMINED.equals(detectedLanguage) || languageHint == null || languageHint.isBlank()) {
            return detectedLanguage;
        }

        Locale hint = Locale.forLanguageTag(languageHint.trim());
        String normalizedHint = hint.toLanguageTag();
        if (UNDETERMINED.equals(hint.getLanguage()) || normalizedHint.equals("und")) {
            return detectedLanguage;
        }
        return normalizedHint;
    }

    private String detectDistinctScript(String query) {
        int letterCount = 0;
        int ukrainianLetterCount = 0;
        int kanaCount = 0;
        int hanCount = 0;
        int cyrillicCount = 0;
        int arabicCount = 0;
        int hebrewCount = 0;
        int devanagariCount = 0;
        int bengaliCount = 0;
        int tamilCount = 0;
        int teluguCount = 0;
        int thaiCount = 0;
        int hangulCount = 0;
        int greekCount = 0;

        for (int offset = 0; offset < query.length();) {
            int codePoint = query.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isLetter(codePoint)) {
                continue;
            }

            letterCount++;
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            switch (script) {
                case CYRILLIC -> {
                    cyrillicCount++;
                    if (UKRAINIAN_LETTERS.contains(codePoint)) {
                        ukrainianLetterCount++;
                    }
                }
                case ARABIC -> arabicCount++;
                case HEBREW -> hebrewCount++;
                case DEVANAGARI -> devanagariCount++;
                case BENGALI -> bengaliCount++;
                case TAMIL -> tamilCount++;
                case TELUGU -> teluguCount++;
                case THAI -> thaiCount++;
                case HANGUL -> hangulCount++;
                case HAN -> hanCount++;
                case HIRAGANA, KATAKANA -> kanaCount++;
                case GREEK -> greekCount++;
                default -> {
                }
            }
        }

        if (letterCount == 0) {
            return null;
        }
        if (hasDominantScript(kanaCount, letterCount)) {
            return "ja";
        }
        if (hasDominantScript(hangulCount, letterCount)) {
            return "ko";
        }
        if (hasDominantScript(cyrillicCount, letterCount)) {
            return ukrainianLetterCount > 0 ? "uk" : "ru";
        }
        if (hasDominantScript(arabicCount, letterCount)) {
            return "ar";
        }
        if (hasDominantScript(hebrewCount, letterCount)) {
            return "he";
        }
        if (hasDominantScript(devanagariCount, letterCount)) {
            return "hi";
        }
        if (hasDominantScript(bengaliCount, letterCount)) {
            return "bn";
        }
        if (hasDominantScript(tamilCount, letterCount)) {
            return "ta";
        }
        if (hasDominantScript(teluguCount, letterCount)) {
            return "te";
        }
        if (hasDominantScript(thaiCount, letterCount)) {
            return "th";
        }
        if (hasDominantScript(greekCount, letterCount)) {
            return "el";
        }
        if (hasDominantScript(hanCount, letterCount)) {
            return "zh";
        }
        return null;
    }

    private boolean hasDominantScript(int scriptLetters, int totalLetters) {
        return scriptLetters > 0 && scriptLetters * 2 >= totalLetters;
    }

    private String detectLatinLanguage(String query) {
        Map<String, Integer> scores = new java.util.HashMap<>();
        var matcher = WORDS.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            LATIN_LANGUAGE_MARKERS.forEach((language, markers) -> {
                if (markers.contains(word)) {
                    scores.merge(language, 1, Integer::sum);
                }
            });
        }

        List<Map.Entry<String, Integer>> rankedLanguages = new ArrayList<>(scores.entrySet());
        rankedLanguages.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
        if (rankedLanguages.isEmpty() || rankedLanguages.get(0).getValue() < 2) {
            return UNDETERMINED;
        }
        if (rankedLanguages.size() > 1
                && rankedLanguages.get(0).getValue().equals(rankedLanguages.get(1).getValue())) {
            return UNDETERMINED;
        }
        return rankedLanguages.get(0).getKey();
    }
}
