package com.se203730.mvc.generateuibe.features.customer.dto.response;
import java.util.List;
public record HomePageComboPageResponse(List<HomePageComboResponse> items,int page,int size,boolean hasNext) {}
