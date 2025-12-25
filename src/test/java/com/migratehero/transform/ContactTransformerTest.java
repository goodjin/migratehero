package com.migratehero.transform;

import com.migratehero.model.dto.Contact;
import com.migratehero.model.enums.ProviderType;
import com.migratehero.service.transform.ContactTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContactTransformerTest {

    private ContactTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new ContactTransformer();
    }

    @Test
    void transform_shouldReturnNullForNullInput() {
        assertNull(transformer.transform(null, ProviderType.MICROSOFT));
    }

    @Test
    void transform_shouldPreserveBasicFields() {
        Contact source = Contact.builder()
                .id("test-id")
                .givenName("John")
                .familyName("Doe")
                .displayName("John Doe")
                .company("Acme Corp")
                .jobTitle("Engineer")
                .build();

        Contact result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals("John", result.getGivenName());
        assertEquals("Doe", result.getFamilyName());
        assertEquals("John Doe", result.getDisplayName());
        assertEquals("Acme Corp", result.getCompany());
        assertEquals("Engineer", result.getJobTitle());
    }

    @Test
    void transform_shouldBuildDisplayNameFromParts() {
        Contact source = Contact.builder()
                .givenName("John")
                .middleName("William")
                .familyName("Doe")
                .build();

        Contact result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals("John William Doe", result.getDisplayName());
    }

    @Test
    void transform_shouldConvertEmailTypes_GoogleToMicrosoft() {
        Contact source = Contact.builder()
                .emailAddresses(Arrays.asList(
                        Contact.EmailAddress.builder().email("home@example.com").type("home").primary(true).build(),
                        Contact.EmailAddress.builder().email("work@example.com").type("work").primary(false).build()
                ))
                .build();

        Contact result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals(2, result.getEmailAddresses().size());
        assertEquals("personal", result.getEmailAddresses().get(0).getType());
        assertEquals("business", result.getEmailAddresses().get(1).getType());
    }

    @Test
    void transform_shouldConvertEmailTypes_MicrosoftToGoogle() {
        Contact source = Contact.builder()
                .emailAddresses(Arrays.asList(
                        Contact.EmailAddress.builder().email("personal@example.com").type("personal").primary(true).build(),
                        Contact.EmailAddress.builder().email("business@example.com").type("business").primary(false).build()
                ))
                .build();

        Contact result = transformer.transform(source, ProviderType.GOOGLE);

        assertEquals(2, result.getEmailAddresses().size());
        assertEquals("home", result.getEmailAddresses().get(0).getType());
        assertEquals("work", result.getEmailAddresses().get(1).getType());
    }

    @Test
    void transform_shouldConvertPhoneTypes() {
        Contact source = Contact.builder()
                .phoneNumbers(Arrays.asList(
                        Contact.PhoneNumber.builder().number("123456789").type("mobile").primary(true).build(),
                        Contact.PhoneNumber.builder().number("987654321").type("work").primary(false).build()
                ))
                .build();

        Contact result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals(2, result.getPhoneNumbers().size());
        assertEquals("mobile", result.getPhoneNumbers().get(0).getType());
        assertEquals("business", result.getPhoneNumbers().get(1).getType());
    }

    @Test
    void transformBatch_shouldTransformMultipleContacts() {
        List<Contact> sources = Arrays.asList(
                Contact.builder().id("1").givenName("John").build(),
                Contact.builder().id("2").givenName("Jane").build()
        );

        List<Contact> results = transformer.transformBatch(sources, ProviderType.MICROSOFT);

        assertEquals(2, results.size());
    }
}
