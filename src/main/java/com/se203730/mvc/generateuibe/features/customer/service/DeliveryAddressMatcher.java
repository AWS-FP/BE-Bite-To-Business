package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class DeliveryAddressMatcher {
    public boolean matches(String city, String address, QuotePackage existing) {
        if (city == null || address == null || !city.equals(existing.getCity()) || existing.getDeliveryAddress() == null) {
            return false;
        }
        String normalized = normalize(address);
        return !normalized.isBlank() && normalized.equals(normalize(existing.getDeliveryAddress()));
    }

    private String normalize(String address) {
        String value = Normalizer.normalize(address.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9/]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        value = value.replaceAll("\\bduong so (?=\\d)", "duong ");
        return value.replaceFirst("(?:^| )(?:thanh pho ho chi minh|tp ho chi minh|tp hcm|ho chi minh|hcm|thanh pho ha noi|tp ha noi|ha noi)$", "").trim();
    }
}
