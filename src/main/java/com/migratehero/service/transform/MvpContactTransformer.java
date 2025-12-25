package com.migratehero.service.transform;

import com.migratehero.service.connector.ews.MvpEwsConnector.ContactDetail;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.io.text.VCardWriter;
import ezvcard.property.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.UUID;

/**
 * MVP 联系人转换器 - 将 EWS 联系人转换为 vCard 格式
 */
@Slf4j
@Component
public class MvpContactTransformer {

    /**
     * 将 EWS 联系人转换为 vCard 格式字符串
     */
    public String toVCard(ContactDetail contact) {
        try {
            VCard vCard = new VCard();

            // UID
            String uid = contact.getId() != null ? contact.getId() : UUID.randomUUID().toString();
            vCard.setUid(new Uid(uid));

            // 姓名
            StructuredName structuredName = new StructuredName();
            structuredName.setFamily(contact.getLastName());
            structuredName.setGiven(contact.getFirstName());
            if (contact.getMiddleName() != null) {
                structuredName.getAdditionalNames().add(contact.getMiddleName());
            }
            vCard.setStructuredName(structuredName);

            // 完整显示名称
            if (contact.getDisplayName() != null) {
                vCard.setFormattedName(contact.getDisplayName());
            } else {
                String fullName = buildFullName(contact);
                vCard.setFormattedName(fullName);
            }

            // 公司和职位
            if (contact.getCompany() != null || contact.getJobTitle() != null || contact.getDepartment() != null) {
                Organization org = new Organization();
                if (contact.getCompany() != null) {
                    org.getValues().add(contact.getCompany());
                }
                if (contact.getDepartment() != null) {
                    org.getValues().add(contact.getDepartment());
                }
                vCard.setOrganization(org);

                if (contact.getJobTitle() != null) {
                    vCard.addTitle(contact.getJobTitle());
                }
            }

            // 邮箱地址
            if (contact.getEmailAddresses() != null) {
                for (String email : contact.getEmailAddresses()) {
                    if (email != null && !email.isEmpty()) {
                        Email emailProp = new Email(email);
                        emailProp.getTypes().add(ezvcard.parameter.EmailType.WORK);
                        vCard.addEmail(emailProp);
                    }
                }
            }

            // 电话号码
            if (contact.getPhoneNumbers() != null) {
                for (String phone : contact.getPhoneNumbers()) {
                    if (phone != null && !phone.isEmpty()) {
                        Telephone tel = parsePhoneNumber(phone);
                        vCard.addTelephoneNumber(tel);
                    }
                }
            }

            // 工作地址
            if (contact.getBusinessAddress() != null && !contact.getBusinessAddress().isEmpty()) {
                Address addr = parseAddress(contact.getBusinessAddress());
                addr.getTypes().add(ezvcard.parameter.AddressType.WORK);
                vCard.addAddress(addr);
            }

            // 家庭地址
            if (contact.getHomeAddress() != null && !contact.getHomeAddress().isEmpty()) {
                Address addr = parseAddress(contact.getHomeAddress());
                addr.getTypes().add(ezvcard.parameter.AddressType.HOME);
                vCard.addAddress(addr);
            }

            // 备注
            if (contact.getNotes() != null && !contact.getNotes().isEmpty()) {
                vCard.addNote(contact.getNotes());
            }

            // 转换为字符串
            StringWriter sw = new StringWriter();
            try (VCardWriter writer = new VCardWriter(sw, VCardVersion.V3_0)) {
                writer.write(vCard);
            }
            return sw.toString();

        } catch (Exception e) {
            log.error("Failed to convert contact to vCard: {}", e.getMessage(), e);
            // 回退到手动构建
            return buildVCardManually(contact);
        }
    }

    private String buildFullName(ContactDetail contact) {
        StringBuilder sb = new StringBuilder();
        if (contact.getFirstName() != null) {
            sb.append(contact.getFirstName());
        }
        if (contact.getMiddleName() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(contact.getMiddleName());
        }
        if (contact.getLastName() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(contact.getLastName());
        }
        return sb.length() > 0 ? sb.toString() : "Unknown";
    }

    private Telephone parsePhoneNumber(String phoneStr) {
        Telephone tel = new Telephone(phoneStr);

        // 解析电话类型（格式如 "Mobile: +86-138-0000-0000"）
        String lowerPhone = phoneStr.toLowerCase();
        if (lowerPhone.startsWith("mobile:")) {
            tel = new Telephone(phoneStr.substring(7).trim());
            tel.getTypes().add(ezvcard.parameter.TelephoneType.CELL);
        } else if (lowerPhone.startsWith("business:")) {
            tel = new Telephone(phoneStr.substring(9).trim());
            tel.getTypes().add(ezvcard.parameter.TelephoneType.WORK);
        } else if (lowerPhone.startsWith("home:")) {
            tel = new Telephone(phoneStr.substring(5).trim());
            tel.getTypes().add(ezvcard.parameter.TelephoneType.HOME);
        } else {
            tel.getTypes().add(ezvcard.parameter.TelephoneType.VOICE);
        }

        return tel;
    }

    private Address parseAddress(String addressStr) {
        Address addr = new Address();
        // 简单处理：将整个地址放入 street 字段
        // 实际格式：street, city, state postalCode, country
        String[] parts = addressStr.split(",");
        if (parts.length >= 1) addr.setStreetAddress(parts[0].trim());
        if (parts.length >= 2) addr.setLocality(parts[1].trim());
        if (parts.length >= 3) {
            String stateZip = parts[2].trim();
            String[] stateZipParts = stateZip.split(" ");
            if (stateZipParts.length >= 1) addr.setRegion(stateZipParts[0]);
            if (stateZipParts.length >= 2) addr.setPostalCode(stateZipParts[1]);
        }
        if (parts.length >= 4) addr.setCountry(parts[3].trim());
        return addr;
    }

    /**
     * 手动构建 vCard 格式（回退方案）
     */
    private String buildVCardManually(ContactDetail contact) {
        StringBuilder sb = new StringBuilder();

        sb.append("BEGIN:VCARD\r\n");
        sb.append("VERSION:3.0\r\n");

        // 姓名
        sb.append("N:").append(nvl(contact.getLastName())).append(";")
                .append(nvl(contact.getFirstName())).append(";")
                .append(nvl(contact.getMiddleName())).append(";;\r\n");

        // 完整名称
        String fullName = contact.getDisplayName() != null ? contact.getDisplayName() : buildFullName(contact);
        sb.append("FN:").append(escapeVCardText(fullName)).append("\r\n");

        // 公司和职位
        if (contact.getCompany() != null) {
            sb.append("ORG:").append(escapeVCardText(contact.getCompany()));
            if (contact.getDepartment() != null) {
                sb.append(";").append(escapeVCardText(contact.getDepartment()));
            }
            sb.append("\r\n");
        }
        if (contact.getJobTitle() != null) {
            sb.append("TITLE:").append(escapeVCardText(contact.getJobTitle())).append("\r\n");
        }

        // 邮箱
        if (contact.getEmailAddresses() != null) {
            for (String email : contact.getEmailAddresses()) {
                if (email != null && !email.isEmpty()) {
                    sb.append("EMAIL;TYPE=WORK:").append(email).append("\r\n");
                }
            }
        }

        // 电话
        if (contact.getPhoneNumbers() != null) {
            for (String phone : contact.getPhoneNumbers()) {
                if (phone != null && !phone.isEmpty()) {
                    String type = "VOICE";
                    String number = phone;
                    if (phone.toLowerCase().startsWith("mobile:")) {
                        type = "CELL";
                        number = phone.substring(7).trim();
                    } else if (phone.toLowerCase().startsWith("business:")) {
                        type = "WORK";
                        number = phone.substring(9).trim();
                    } else if (phone.toLowerCase().startsWith("home:")) {
                        type = "HOME";
                        number = phone.substring(5).trim();
                    }
                    sb.append("TEL;TYPE=").append(type).append(":").append(number).append("\r\n");
                }
            }
        }

        // 备注
        if (contact.getNotes() != null && !contact.getNotes().isEmpty()) {
            sb.append("NOTE:").append(escapeVCardText(contact.getNotes())).append("\r\n");
        }

        sb.append("END:VCARD\r\n");

        return sb.toString();
    }

    private String nvl(String s) {
        return s != null ? escapeVCardText(s) : "";
    }

    private String escapeVCardText(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
