package com.opencrm.crm.buyingrole;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link BuyingRole} as the closed storage value, not as caller-supplied text.
 */
@Converter
public class BuyingRoleConverter implements AttributeConverter<BuyingRole, String> {

    @Override
    public String convertToDatabaseColumn(BuyingRole role) {
        if (role == null) {
            return null;
        }
        return role.storageValue();
    }

    @Override
    public BuyingRole convertToEntityAttribute(String stored) {
        return BuyingRole.fromStorage(stored);
    }
}
