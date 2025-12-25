package com.migratehero.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 统一联系人 DTO - 用于 Google 和 Microsoft 联系人格式转换
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contact {

    private String id;
    private String displayName;
    private String givenName;
    private String middleName;
    private String familyName;
    private String nickname;
    private String company;
    private String jobTitle;
    private String department;
    private String notes;
    private LocalDate birthday;
    private String photoUrl;
    private byte[] photoData;

    private List<EmailAddress> emailAddresses;
    private List<PhoneNumber> phoneNumbers;
    private List<Address> addresses;
    private List<String> groups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailAddress {
        private String email;
        private String type; // home, work, other
        private boolean primary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhoneNumber {
        private String number;
        private String type; // mobile, home, work, other
        private boolean primary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String type; // home, work, other
        private boolean primary;
    }
}
