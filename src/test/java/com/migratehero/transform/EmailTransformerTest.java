package com.migratehero.transform;

import com.migratehero.model.dto.EmailMessage;
import com.migratehero.model.enums.ProviderType;
import com.migratehero.service.transform.EmailTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailTransformerTest {

    private EmailTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new EmailTransformer();
    }

    @Test
    void transform_shouldReturnNullForNullInput() {
        assertNull(transformer.transform(null, ProviderType.MICROSOFT));
    }

    @Test
    void transform_shouldPreserveBasicFields() {
        EmailMessage source = EmailMessage.builder()
                .id("test-id")
                .subject("Test Subject")
                .from("sender@example.com")
                .to(List.of("recipient@example.com"))
                .bodyHtml("<p>Hello</p>")
                .bodyText("Hello")
                .sentAt(Instant.now())
                .isRead(true)
                .isStarred(false)
                .isDraft(false)
                .build();

        EmailMessage result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals("Test Subject", result.getSubject());
        assertEquals("sender@example.com", result.getFrom());
        assertEquals(List.of("recipient@example.com"), result.getTo());
        assertEquals("<p>Hello</p>", result.getBodyHtml());
        assertEquals("Hello", result.getBodyText());
        assertTrue(result.isRead());
        assertFalse(result.isStarred());
        assertFalse(result.isDraft());
    }

    @Test
    void transform_shouldConvertGmailLabelsToOutlookFolders() {
        EmailMessage source = EmailMessage.builder()
                .labels(Arrays.asList("INBOX", "STARRED", "SPAM"))
                .build();

        EmailMessage result = transformer.transform(source, ProviderType.MICROSOFT);

        assertNotNull(result.getLabels());
        assertTrue(result.getLabels().contains("Inbox"));
        assertTrue(result.getLabels().contains("JunkEmail"));
    }

    @Test
    void transform_shouldConvertOutlookFoldersToGmailLabels() {
        EmailMessage source = EmailMessage.builder()
                .labels(Arrays.asList("Inbox", "SentItems", "JunkEmail"))
                .build();

        EmailMessage result = transformer.transform(source, ProviderType.GOOGLE);

        assertNotNull(result.getLabels());
        assertTrue(result.getLabels().contains("INBOX"));
        assertTrue(result.getLabels().contains("SENT"));
        assertTrue(result.getLabels().contains("SPAM"));
    }

    @Test
    void transformBatch_shouldTransformMultipleEmails() {
        List<EmailMessage> sources = Arrays.asList(
                EmailMessage.builder().id("1").subject("Email 1").build(),
                EmailMessage.builder().id("2").subject("Email 2").build(),
                EmailMessage.builder().id("3").subject("Email 3").build()
        );

        List<EmailMessage> results = transformer.transformBatch(sources, ProviderType.MICROSOFT);

        assertEquals(3, results.size());
        assertEquals("Email 1", results.get(0).getSubject());
        assertEquals("Email 2", results.get(1).getSubject());
        assertEquals("Email 3", results.get(2).getSubject());
    }

    @Test
    void transformBatch_shouldReturnEmptyListForNullInput() {
        assertTrue(transformer.transformBatch(null, ProviderType.MICROSOFT).isEmpty());
    }

    @Test
    void transformBatch_shouldReturnEmptyListForEmptyInput() {
        assertTrue(transformer.transformBatch(List.of(), ProviderType.MICROSOFT).isEmpty());
    }
}
