package com.se203730.mvc.generateuibe.features.ai.enums;

/** Các ô song ngữ của sản phẩm được phép gợi ý bản dịch; maxLength khớp giới hạn của form tạo sản phẩm. */
public enum TranslationField {
    NAME("product name shown on cards and search results; keep it short like a catalog title", 200),
    DESCRIPTION("short marketing-neutral product description", 2000),
    SPEC("packaging specification, e.g. pack size and units per case", 500),
    TECHNICAL("technical specifications", 2000),
    STORAGE("storage and preparation instructions", 2000),
    COMPLIANCE("food safety and invoice information", 2000),
    ORIGIN_DETAIL("origin and sourcing information", 2000);

    private final String hint;
    private final int maxLength;

    TranslationField(String hint, int maxLength) {
        this.hint = hint;
        this.maxLength = maxLength;
    }

    public String hint() {
        return hint;
    }

    public int maxLength() {
        return maxLength;
    }
}
