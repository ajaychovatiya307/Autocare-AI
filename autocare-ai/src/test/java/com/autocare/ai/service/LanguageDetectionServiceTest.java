package com.autocare.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LanguageDetectionServiceTest {

    private final LanguageDetectionService service = new LanguageDetectionService();

    @Test
    void detectsLatinLanguagesFromCommonWords() {
        assertThat(service.detectLanguage("What is the service interval for my car?")).isEqualTo("en");
        assertThat(service.detectLanguage("¿Cuál es el intervalo de servicio para mi coche?")).isEqualTo("es");
        assertThat(service.detectLanguage("Quel est le calendrier de service pour ma voiture?")).isEqualTo("fr");
        assertThat(service.detectLanguage("Was ist der Serviceplan für mein Auto?")).isEqualTo("de");
    }

    @Test
    void detectsLanguagesFromDistinctWritingSystems() {
        assertThat(service.detectLanguage("Какой график обслуживания автомобиля?")).isEqualTo("ru");
        assertThat(service.detectLanguage("Який графік технічного обслуговування автомобіля?")).isEqualTo("uk");
        assertThat(service.detectLanguage("これは車の点検予定です")).isEqualTo("ja");
        assertThat(service.detectLanguage("차량 정비 주기는 어떻게 되나요")).isEqualTo("ko");
        assertThat(service.detectLanguage("ما هو جدول صيانة السيارة؟")).isEqualTo("ar");
    }

    @Test
    void returnsUndeterminedForTextWithoutEnoughLanguageEvidence() {
        assertThat(service.detectLanguage("AC-EV-1")).isEqualTo("und");
        assertThat(service.detectLanguage("service")).isEqualTo("und");
    }

    @Test
    void usesValidLanguageHintOnlyWhenTextIsUndetermined() {
        assertThat(service.detectLanguage("service", "pt-BR")).isEqualTo("pt-BR");
        assertThat(service.detectLanguage("What is the service interval?", "pt-BR")).isEqualTo("en");
        assertThat(service.detectLanguage("service", "not a language")).isEqualTo("und");
    }

    @Test
    void rejectsBlankQueries() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.detectLanguage("  "));
    }
}
