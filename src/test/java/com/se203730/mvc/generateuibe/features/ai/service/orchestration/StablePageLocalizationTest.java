package com.se203730.mvc.generateuibe.features.ai.service.orchestration;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.*;
import com.se203730.mvc.generateuibe.features.ai.client.BedrockClient;
import com.se203730.mvc.generateuibe.features.ai.dto.RunAgentInput;
import com.se203730.mvc.generateuibe.features.ai.repository.AiModelRequestRepository;
import com.se203730.mvc.generateuibe.features.ai.service.a2ui.A2uiValidator;
import com.se203730.mvc.generateuibe.features.ai.service.context.AiPromptLibrary;
import com.se203730.mvc.generateuibe.features.ai.service.tools.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StablePageLocalizationTest {
 @Test void comparisonRedirectCarriesTheSubjectAsDataRatherThanAppendingAnEnglishSentence()throws Exception {
  var mapper=new ObjectMapper();var model=mock(BedrockClient.class);var library=new AiPromptLibrary(mapper,Set.of());
  var service=new GenerativeUiService(model,library,new A2uiValidator(library),mock(ProductSearchTool.class),mock(PageDataTool.class),null,null,null,mock(AiModelRequestRepository.class),mapper);
  when(model.checkUserInput(anyString())).thenReturn(new BedrockClient.GuardrailCheck(false,null));
  when(model.converse(anyString(),anyString(),anyInt(),anyFloat())).thenReturn(new BedrockClient.ModelReply("{\"scope\":\"SHOPPING\",\"intents\":[\"CHANGE_UI_PREFERENCE\"],\"slots\":{\"target_view\":\"table\"}}",1,1,1));
  var events=new ArrayList<Map<String,Object>>();
  service.run(new RunAgentInput("t","r",List.of(new RunAgentInput.ChatMessage("user","xem dạng bảng để so giá")),new RunAgentInput.AgentState("vi",null,null,"gia vị",null,null,false),"chat"),events::add);
  var redirect=mapper.valueToTree(events.stream().filter(e->"open_ai_page".equals(e.get("name"))).findFirst().orElseThrow().get("value"));
  assertEquals("gia vị",redirect.path("subject").asString());assertEquals("table",redirect.path("view").asString());assertEquals("xem dạng bảng để so giá",redirect.path("prompt").asString());
 }
 @Test void changingLanguageKeepsIdsOrderAndCompositionWithoutSearchingOrRecommendingAgain()throws Exception {
  var mapper=new ObjectMapper();var model=mock(BedrockClient.class);var products=mock(ProductSearchTool.class);var pages=mock(PageDataTool.class);
  var library=new AiPromptLibrary(mapper,Set.of());var service=new GenerativeUiService(model,library,new A2uiValidator(library),products,pages,null,null,null,mock(AiModelRequestRepository.class),mapper);
  when(model.checkUserInput(anyString())).thenReturn(new BedrockClient.GuardrailCheck(false,null));
  when(model.converse(anyString(),anyString(),anyInt(),anyFloat())).thenReturn(new BedrockClient.ModelReply("{\"scope\":\"SHOPPING\",\"intents\":[\"SEARCH_PRODUCT\"],\"slots\":{\"product_name\":\"cam vàng\"}}",1,1,1),new BedrockClient.ModelReply("{\"header.title\":\"Navel oranges\"}",1,1,1));
  String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
  var selected=Map.of("/products",List.of(first,second));
  when(pages.relocalize(selected,"en","cam vàng")).thenReturn(new PageDataTool.PageData(Map.of("products",List.of(Map.of("productId",first,"name","Navel orange"),Map.of("productId",second,"name","Other orange"))),Map.of(),Map.of("/products",2),"cam vàng","relevance",Map.of()));
  var layout=mapper.readTree("""
  {"version":"v0.9","updateComponents":{"surfaceId":"page-saved","components":[
  {"id":"root","component":"Column","children":["header","grid"]},
  {"id":"header","component":"PageHeader","title":"Cam vàng","variant":"hero"},
  {"id":"grid","component":"ProductGrid","items":{"path":"/products"},"columns":2}]}}
  """);
  var events=new ArrayList<Map<String,Object>>();
  service.run(new RunAgentInput("t","r",List.of(new RunAgentInput.ChatMessage("user","cam vàng")),new RunAgentInput.AgentState("en",null,null,null,null,null,false),"page",new RunAgentInput.StablePage(layout,selected,"vi")),events::add);
  var event=events.stream().filter(e->"a2ui".equals(e.get("name"))).findFirst().orElseThrow();
  var messages=mapper.valueToTree(event.get("value"));
  assertEquals(first,messages.get(1).path("updateDataModel").path("value").path("products").get(0).path("productId").asString());
  assertEquals(second,messages.get(1).path("updateDataModel").path("value").path("products").get(1).path("productId").asString());
  var components=messages.get(2).path("updateComponents").path("components");
  assertEquals("hero",components.get(1).path("variant").asString());assertEquals("Navel oranges",components.get(1).path("title").asString());assertEquals(2,components.get(2).path("columns").asInt());
  verifyNoInteractions(products);verify(model,times(2)).converse(anyString(),anyString(),anyInt(),anyFloat());
 }
}
