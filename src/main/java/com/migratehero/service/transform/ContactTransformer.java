package com.migratehero.service.transform;

import com.migratehero.model.dto.Contact;
import com.migratehero.model.enums.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 联系人数据转换器 - 处理 Google ↔ Microsoft 联系人格式转换
 */
@Slf4j
@Component
public class ContactTransformer {

    /**
     * 转换联系人以适配目标平台
     */
    public Contact transform(Contact source, ProviderType targetProvider) {
        if (source == null) {
            return null;
        }

        Contact.ContactBuilder builder = Contact.builder()
                .givenName(source.getGivenName())
                .middleName(source.getMiddleName())
                .familyName(source.getFamilyName())
                .displayName(buildDisplayName(source))
                .nickname(source.getNickname())
                .emailAddresses(transformEmailAddresses(source.getEmailAddresses(), targetProvider))
                .phoneNumbers(transformPhoneNumbers(source.getPhoneNumbers(), targetProvider))
                .addresses(transformAddresses(source.getAddresses(), targetProvider))
                .company(source.getCompany())
                .jobTitle(source.getJobTitle())
                .department(source.getDepartment())
                .birthday(source.getBirthday())
                .notes(source.getNotes())
                .photoUrl(source.getPhotoUrl())
                .photoData(source.getPhotoData())
                .groups(source.getGroups());

        return builder.build();
    }

    /**
     * 构建显示名称
     */
    private String buildDisplayName(Contact contact) {
        if (contact.getDisplayName() != null && !contact.getDisplayName().isBlank()) {
            return contact.getDisplayName();
        }

        StringBuilder sb = new StringBuilder();
        if (contact.getGivenName() != null) {
            sb.append(contact.getGivenName());
        }
        if (contact.getMiddleName() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(contact.getMiddleName());
        }
        if (contact.getFamilyName() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(contact.getFamilyName());
        }
        return sb.length() > 0 ? sb.toString() : "Unnamed Contact";
    }

    /**
     * 转换邮箱地址类型
     */
    private List<Contact.EmailAddress> transformEmailAddresses(List<Contact.EmailAddress> emails, ProviderType targetProvider) {
        if (emails == null || emails.isEmpty()) {
            return new ArrayList<>();
        }

        List<Contact.EmailAddress> result = new ArrayList<>();
        for (Contact.EmailAddress email : emails) {
            String type = transformEmailType(email.getType(), targetProvider);
            result.add(Contact.EmailAddress.builder()
                    .email(email.getEmail())
                    .type(type)
                    .primary(email.isPrimary())
                    .build());
        }
        return result;
    }

    /**
     * 转换邮箱类型标签
     */
    private String transformEmailType(String type, ProviderType targetProvider) {
        if (type == null) {
            return targetProvider == ProviderType.MICROSOFT ? "personal" : "home";
        }

        String lowerType = type.toLowerCase();
        if (targetProvider == ProviderType.MICROSOFT) {
            return switch (lowerType) {
                case "home" -> "personal";
                case "work" -> "business";
                case "other" -> "other";
                default -> "personal";
            };
        } else {
            return switch (lowerType) {
                case "personal" -> "home";
                case "business" -> "work";
                case "other" -> "other";
                default -> "home";
            };
        }
    }

    /**
     * 转换电话号码类型
     */
    private List<Contact.PhoneNumber> transformPhoneNumbers(List<Contact.PhoneNumber> phones, ProviderType targetProvider) {
        if (phones == null || phones.isEmpty()) {
            return new ArrayList<>();
        }

        List<Contact.PhoneNumber> result = new ArrayList<>();
        for (Contact.PhoneNumber phone : phones) {
            String type = transformPhoneType(phone.getType(), targetProvider);
            result.add(Contact.PhoneNumber.builder()
                    .number(phone.getNumber())
                    .type(type)
                    .primary(phone.isPrimary())
                    .build());
        }
        return result;
    }

    /**
     * 转换电话类型标签
     */
    private String transformPhoneType(String type, ProviderType targetProvider) {
        if (type == null) {
            return targetProvider == ProviderType.MICROSOFT ? "mobile" : "mobile";
        }

        String lowerType = type.toLowerCase();
        if (targetProvider == ProviderType.MICROSOFT) {
            return switch (lowerType) {
                case "mobile", "cell" -> "mobile";
                case "home" -> "home";
                case "work" -> "business";
                case "main" -> "business";
                case "fax", "workfax" -> "businessFax";
                case "homefax" -> "homeFax";
                case "pager" -> "pager";
                default -> "other";
            };
        } else {
            return switch (lowerType) {
                case "mobile" -> "mobile";
                case "home" -> "home";
                case "business", "work" -> "work";
                case "businessfax" -> "workFax";
                case "homefax" -> "homeFax";
                case "pager" -> "pager";
                default -> "other";
            };
        }
    }

    /**
     * 转换地址类型
     */
    private List<Contact.Address> transformAddresses(List<Contact.Address> addresses, ProviderType targetProvider) {
        if (addresses == null || addresses.isEmpty()) {
            return new ArrayList<>();
        }

        List<Contact.Address> result = new ArrayList<>();
        for (Contact.Address address : addresses) {
            String type = transformAddressType(address.getType(), targetProvider);
            result.add(Contact.Address.builder()
                    .street(address.getStreet())
                    .city(address.getCity())
                    .state(address.getState())
                    .postalCode(address.getPostalCode())
                    .country(address.getCountry())
                    .type(type)
                    .build());
        }
        return result;
    }

    /**
     * 转换地址类型标签
     */
    private String transformAddressType(String type, ProviderType targetProvider) {
        if (type == null) {
            return targetProvider == ProviderType.MICROSOFT ? "home" : "home";
        }

        String lowerType = type.toLowerCase();
        if (targetProvider == ProviderType.MICROSOFT) {
            return switch (lowerType) {
                case "home" -> "home";
                case "work" -> "business";
                case "other" -> "other";
                default -> "home";
            };
        } else {
            return switch (lowerType) {
                case "home" -> "home";
                case "business" -> "work";
                case "other" -> "other";
                default -> "home";
            };
        }
    }

    /**
     * 批量转换联系人
     */
    public List<Contact> transformBatch(List<Contact> sources, ProviderType targetProvider) {
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }

        List<Contact> results = new ArrayList<>(sources.size());
        for (Contact source : sources) {
            try {
                results.add(transform(source, targetProvider));
            } catch (Exception e) {
                log.error("Failed to transform contact: {}", source.getId(), e);
            }
        }
        return results;
    }
}
