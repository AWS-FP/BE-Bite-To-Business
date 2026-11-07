package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeliveryEstimateService {
    private final RestClient client;

    @Value("${app.vietmap.services-key:}")
    private String apiKey;

    public DeliveryEstimateService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);
        client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public List<Suggestion> search(String text, String city) {
        if (text == null || text.trim().length() < 2 || text.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        JsonNode response = get("/api/autocomplete/v4", "text", text.trim(), false, origin(city));
        if (!response.isArray()) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        List<Suggestion> suggestions = new ArrayList<>();
        for (JsonNode item : response) {
            String id = item.path("ref_id").asText("");
            String display = item.path("display").asText("");
            if (!id.isBlank() && !display.isBlank()) suggestions.add(new Suggestion(id, display));
        }
        return suggestions;
    }

    public Estimate estimate(String refId, String city) {
        if (refId == null || refId.isBlank() || refId.length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Origin origin = origin(city);
        JsonNode place = get("/api/place/v4", "refid", refId, false, origin);
        if (!place.path("lat").isNumber() || !place.path("lng").isNumber()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        double lat = place.path("lat").asDouble();
        double lng = place.path("lng").asDouble();
        return estimateCoordinates(lat, lng, city, place.path("display").asText(""));
    }

    public Estimate estimateCoordinates(double lat, double lng, String city, String address) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Origin origin = origin(city);
        JsonNode route = get("/api/route/v4", "point", lat + "," + lng, true, origin);
        JsonNode path = route.path("paths").path(0);
        if (!"OK".equals(route.path("code").asText()) || !path.path("distance").isNumber()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        double metres = path.path("distance").asDouble();
        if (!Double.isFinite(metres) || metres < 0) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        JsonNode geometry = path.path("points");
        JsonNode points = geometry.path("coordinates");
        if (!"LineString".equals(geometry.path("type").asText()) || !points.isArray() || points.size() < 2) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        List<List<Double>> coordinates = new ArrayList<>();
        for (JsonNode point : points) {
            if (!point.isArray() || point.size() < 2 || !point.path(0).isNumber() || !point.path(1).isNumber()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            double longitude = point.path(0).asDouble();
            double latitude = point.path(1).asDouble();
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            coordinates.add(List.of(longitude, latitude));
        }
        long fee = (long) Math.ceil(Math.max(0, metres - 5000) / 1000) * 5000;
        return new Estimate(address, lat, lng, metres, fee, coordinates);
    }

    private Origin origin(String city) {
        if ("hcm".equals(city)) return new Origin(10.77172, 106.70418);
        if ("hanoi".equals(city)) return new Origin(21.0292789, 105.8295441);
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private JsonNode get(String path, String param, String value, boolean route, Origin origin) {
        if (apiKey == null || apiKey.isBlank()) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("https://maps.vietmap.vn" + path)
                .queryParam("apikey", apiKey);
        if (route) builder.queryParam("point", origin.lat() + "," + origin.lng());
        builder.queryParam(param, value);
        if (route) builder.queryParam("vehicle", "motorcycle").queryParam("points_encoded", false);
        else if (path.contains("autocomplete")) builder.queryParam("focus", origin.lat() + "," + origin.lng()).queryParam("display_type", 1);
        URI uri = builder.build().encode().toUri();
        try {
            JsonNode result = client.get().uri(uri).retrieve().body(JsonNode.class);
            if (result == null) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            return result;
        } catch (org.springframework.web.client.RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    public record Suggestion(String refId, String display) {}
    public record Estimate(String address, double lat, double lng, double distanceMetres, long fee, List<List<Double>> coordinates) {}
    private record Origin(double lat, double lng) {}
}
