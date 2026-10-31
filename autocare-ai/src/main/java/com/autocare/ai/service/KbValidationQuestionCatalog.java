package com.autocare.ai.service;

import com.autocare.ai.model.enums.UserRole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class KbValidationQuestionCatalog {

    private static final Pattern QUESTION_ROW = Pattern.compile(
            "^\\|\\s*(KB-AUTO-\\d{3})\\s*\\|\\s*#?(\\d+)(?:\\s*\\*\\(([^)]*)\\)\\*)?\\s*\\|\\s*\"([^\"]+)\"\\s*\\|\\s*([^|]+)\\|\\s*([^|]+)\\|\\s*$");
    private static final Pattern EXPECTED_SECTION = Pattern.compile("Sections?\\s+([0-9.]+(?:\\s*(?:and|,)\\s*[0-9.]+)*)");
    private static final Pattern SECTION_NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)*");
    private static final String TABLE_START = "## 6. Sample Retrieval Test Questions";
    private static final String TABLE_END = "## 7.";

    private final List<ValidationQuestion> questions;

    public KbValidationQuestionCatalog() {
        this.questions = loadQuestions();
        if (questions.size() != 16) {
            throw new IllegalStateException(
                    "KB-METADATA.md §6 must define 16 validation questions; found " + questions.size());
        }
    }

    public List<ValidationQuestion> forDocument(String docCode) {
        return questions.stream().filter(question -> question.docCode().equals(docCode)).toList();
    }

    private List<ValidationQuestion> loadQuestions() {
        try {
            String markdown;
            try (var input = new ClassPathResource("kb/KB-METADATA.md").getInputStream()) {
                markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            int start = markdown.indexOf(TABLE_START);
            int end = markdown.indexOf(TABLE_END, start);
            if (start < 0 || end < 0) {
                throw new IllegalStateException("KB-METADATA.md does not contain its §6 validation table");
            }

            List<ValidationQuestion> result = new ArrayList<>();
            for (String line : markdown.substring(start, end).lines().toList()) {
                Matcher row = QUESTION_ROW.matcher(line);
                if (!row.matches()) {
                    continue;
                }
                String docCode = row.group(1);
                String roleMarker = row.group(3) == null ? "" : row.group(3);
                String questionText = row.group(4).trim();
                String grounding = row.group(5).trim();
                String behavior = row.group(6).trim();
                List<String> sections = parseSections(grounding);
                boolean audienceCheck = grounding.startsWith("Audience filter:");
                UserRole role = audienceCheck && questionText.toLowerCase().contains("owner")
                        ? UserRole.OWNER
                        : roleMarker.toLowerCase().contains("advisor") && !audienceCheck
                                ? UserRole.ADVISOR
                                : UserRole.OWNER;
                result.add(new ValidationQuestion(
                        docCode, Integer.parseInt(row.group(2)), questionText, sections, behavior, role, audienceCheck));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the bundled KB-METADATA.md validation table", exception);
        }
    }

    private List<String> parseSections(String grounding) {
        Matcher group = EXPECTED_SECTION.matcher(grounding);
        if (!group.find()) {
            return List.of();
        }
        Matcher numbers = SECTION_NUMBER.matcher(group.group(1));
        List<String> sections = new ArrayList<>();
        while (numbers.find()) {
            sections.add(numbers.group());
        }
        return List.copyOf(sections);
    }

    public record ValidationQuestion(
            String docCode,
            int number,
            String question,
            List<String> expectedSections,
            String expectedBehavior,
            UserRole role,
            boolean audienceCheck) {
    }
}
