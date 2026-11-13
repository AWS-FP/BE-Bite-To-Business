package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class VietmapGeocodingService {
    private final RestClient client;

    @Value("${app.vietmap.services-key:}")
    private String apiKey;

    public VietmapGeocodingService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public Location addressToCoordinates(String address, String city) {
        if (address == null || address.trim().length() < 2 || address.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String focus;
        if ("hcm".equals(city)) focus = "10.77172,106.70418";
        else if ("hanoi".equals(city)) focus = "21.0292789,105.8295441";
        else throw new BusinessException(ErrorCode.INVALID_REQUEST);
        JsonNode results = get(UriComponentsBuilder.fromUriString("https://maps.vietmap.vn/api/search/v4")
                .queryParam("apikey", apiKey).queryParam("text", address.trim())
                .queryParam("focus", focus).queryParam("display_type", 1));
        String refId = results.path(0).path("ref_id").asText("");
        if (refId.isBlank()) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        JsonNode place = get(UriComponentsBuilder.fromUriString("https://maps.vietmap.vn/api/place/v4")
                .queryParam("apikey", apiKey).queryParam("refid", refId));
        if (!place.path("lat").isNumber() || !place.path("lng").isNumber()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        return new Location(place.path("display").asText(""), place.path("lat").asDouble(), place.path("lng").asDouble());
    }

    public Location coordinatesToAddress(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        JsonNode results = get(UriComponentsBuilder.fromUriString("https://maps.vietmap.vn/api/reverse/v4")
                .queryParam("apikey", apiKey).queryParam("lat", lat).queryParam("lng", lng).queryParam("display_type", 1));
        String address = results.path(0).path("display").asText("");
        if (address.isBlank()) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        return new Location(address, lat, lng);
    }

    private JsonNode get(UriComponentsBuilder uri) {
        if (apiKey == null || apiKey.isBlank()) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        try {
            JsonNode result = client.get().uri(uri.build().encode().toUri()).retrieve().body(JsonNode.class);
            if (result == null) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            return result;
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    public record Location(String address, double lat, double lng) {}
}
