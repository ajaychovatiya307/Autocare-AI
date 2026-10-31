package com.autocare.ai.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatClientTypeTest {

    @Test
    void hasExactlyTwoTypesInExpectedOrder() {
        assertThat(ChatClientType.values())
                .containsExactly(ChatClientType.QUERY_TRANSFORM, ChatClientType.ANSWER);
    }

    @Test
    void valueOfResolvesEachTypeByName() {
        assertThat(ChatClientType.valueOf("QUERY_TRANSFORM")).isEqualTo(ChatClientType.QUERY_TRANSFORM);
        assertThat(ChatClientType.valueOf("ANSWER")).isEqualTo(ChatClientType.ANSWER);
    }
}
