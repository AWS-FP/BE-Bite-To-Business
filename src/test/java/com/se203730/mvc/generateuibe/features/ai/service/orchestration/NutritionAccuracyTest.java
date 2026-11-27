package com.se203730.mvc.generateuibe.features.ai.service.orchestration;
import com.se203730.mvc.generateuibe.features.ai.service.tools.NutritionCriteria;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NutritionAccuracyTest {
 @Test void bothConditionsMustMatchTheRequestedThreshold() {
  var criteria=NutritionCriteria.parse("Tìm nguyên liệu giàu đạm trên 15g/100g, nhiều chất xơ");
  assertTrue(criteria.active());assertTrue(criteria.supported());
  assertFalse(criteria.matches(26D,0D),"Meat without fiber cannot satisfy both conditions");
  assertFalse(criteria.matches(5D,2D),"Cookies below the requested protein threshold cannot match");
  assertFalse(criteria.matches(15D,10D),"Above is strict, not greater-or-equal");
  assertFalse(criteria.matches(25D,null),"Unknown fiber is not evidence of fiber");
  assertTrue(criteria.matches(20.5D,12.2D));
 }
 @Test void unitsAndMissingThresholdsCannotBeSilentlyRelaxed() {
  assertFalse(NutritionCriteria.per100g("100 ml"));assertFalse(NutritionCriteria.per100g("1 khẩu phần"));
  assertTrue(NutritionCriteria.per100g("100g"));
  assertNull(NutritionCriteria.grams("20%"));assertNull(NutritionCriteria.grams("< 3 g"));
  assertEquals(0.5,NutritionCriteria.grams("500mg"));
  var wrongBasis=NutritionCriteria.parse("protein trên 15g/100ml");
  assertFalse(wrongBasis.supported());assertFalse(wrongBasis.matches(30D,10D));
 }
}
