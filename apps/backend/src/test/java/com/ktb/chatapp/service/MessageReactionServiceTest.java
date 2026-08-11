package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.model.Message;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class MessageReactionServiceTest {

    @Mock private MongoTemplate mongoTemplate;

    private MessageReactionService service;

    @BeforeEach
    void setUp() {
        service = new MessageReactionService(mongoTemplate);
    }

    @Test
    void addReaction_usesAtomicAddToSetAndReturnsUpdatedDocument() {
        Message updated = Message.builder().id("message-1").build();
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Message.class))).thenReturn(updated);

        service.updateReaction("message-1", "👍", "user-1", "add");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                any(Query.class),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(Message.class));
        Document update = updateCaptor.getValue().getUpdateObject();
        assertThat(update)
                .isEqualTo(new Document("$addToSet", new Document("reactions.👍", "user-1")));
    }

    @Test
    void invalidReactionPath_isRejectedBeforeMongoAccess() {
        assertThatThrownBy(() -> service.updateReaction("message-1", "$bad", "user-1", "add"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mongoTemplate, never()).findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Message.class));
    }
}
