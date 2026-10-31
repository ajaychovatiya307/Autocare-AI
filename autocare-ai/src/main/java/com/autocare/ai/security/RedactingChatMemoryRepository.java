package com.autocare.ai.security;

import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

public final class RedactingChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMemoryRepository delegate;

    public RedactingChatMemoryRepository(ChatMemoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<String> findConversationIds() {
        return delegate.findConversationIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return redact(delegate.findByConversationId(conversationId));
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        delegate.saveAll(conversationId, redact(messages));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        delegate.deleteByConversationId(conversationId);
    }

    private List<Message> redact(List<Message> messages) {
        return messages.stream()
                .map(message -> {
                    String text = RedactionPatterns.redact(message.getText());
                    if (message instanceof UserMessage) {
                        return (Message) new UserMessage(text);
                    }
                    if (message instanceof AssistantMessage) {
                        return new AssistantMessage(text);
                    }
                    if (message instanceof SystemMessage) {
                        return new SystemMessage(text);
                    }
                    throw new IllegalArgumentException(
                            "Unsupported chat memory message type: " + message.getMessageType());
                })
                .toList();
    }
}
