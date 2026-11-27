package com.se203730.mvc.generateuibe.features.ai.service.tools;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Numeric filtering uses verified recorded values and a matching 100 g basis, not nutrient-name matches. */
public record NutritionCriteria(boolean protein, boolean fiber, Double proteinMin, Double fiberMin,
        boolean proteinInclusive, boolean fiberInclusive, boolean supported) {
    private static String fold(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd');
    }
    public static NutritionCriteria parse(String request) {
        String text = fold(request);
        boolean protein = Pattern.compile("\\b(dam|protein)\\b").matcher(text).find();
        boolean fiber = Pattern.compile("\\b(chat xo|fiber|fibre)\\b").matcher(text).find();
        var p = threshold(text, "(?:chat dam|dam|protein)");
        var f = threshold(text, "(?:chat xo|fiber|fibre)");
        boolean unsupported = p.value == null && Pattern.compile("(?:dam|protein)\\s*(?:tren|hon|>|>=|≥|it nhat|tu|over|above|at least)?\\s*\\d").matcher(text).find()
                || f.value == null && Pattern.compile("(?:chat xo|fiber|fibre)\\s*(?:tren|hon|>|>=|≥|it nhat|tu|over|above|at least)?\\s*\\d").matcher(text).find();
        if ((protein || fiber) && p.value == null && f.value == null
                && Pattern.compile("(?:tren|hon|it nhat|over|above|at least|>=|>)\\s*\\d").matcher(text).find()) unsupported = true;
        return new NutritionCriteria(protein, fiber, p.value, f.value, p.inclusive, f.inclusive, !unsupported);
    }
    private record Threshold(Double value, boolean inclusive) {}
    private static Threshold threshold(String text, String name) {
        var match = Pattern.compile(name + "\\s*(tren|hon|>|>=|≥|it nhat|tu|over|above|at least)?\\s*(\\d+(?:[.,]\\d+)?)\\s*g\\s*(?:/|per|tren)\\s*100\\s*g").matcher(text);
        if (!match.find()) return new Threshold(null, false);
        return new Threshold(Double.parseDouble(match.group(2).replace(',', '.')),
                match.group(1) != null && Pattern.compile(">=|≥|it nhat|tu|at least").matcher(match.group(1)).matches());
    }
    public boolean active() {return protein || fiber;}
    public boolean matches(Double proteinGrams, Double fiberGrams) {
        return supported && (!protein || sufficient(proteinGrams, proteinMin, proteinInclusive))
                && (!fiber || sufficient(fiberGrams, fiberMin, fiberInclusive));
    }
    private boolean sufficient(Double actual, Double minimum, boolean inclusive) {
        return actual != null && (minimum == null ? actual > 0 : inclusive ? actual >= minimum : actual > minimum);
    }
    public static boolean per100g(String basis) {
        return basis != null && fold(basis).trim().matches("(?:(?:tren|per|trong)\\s*)?100\\s*g(?:\\s*(?:san pham|product))?");
    }
    public static String nutrient(String name) {
        String value = fold(name == null ? "" : name).trim();
        if (value.matches("(?:chat )?dam|protein")) return "protein";
        if (value.matches("chat xo|fiber|fibre|dietary fib(?:er|re)")) return "fiber";
        return "other";
    }
    public static Double grams(String amount) {
        if (amount == null) return null;
        var match = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(mg|g)\\s*$", Pattern.CASE_INSENSITIVE).matcher(amount);
        if (!match.matches()) return null;
        double result = Double.parseDouble(match.group(1).replace(',', '.'));
        return "mg".equalsIgnoreCase(match.group(2)) ? result / 1000 : result;
    }
}
