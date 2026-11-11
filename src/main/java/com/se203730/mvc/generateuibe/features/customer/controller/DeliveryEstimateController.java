package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.service.DeliveryEstimateService;
import com.se203730.mvc.generateuibe.features.customer.service.VietmapGeocodingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/delivery-estimate")
@Tag(name = "Shared - Delivery Estimates")
public class DeliveryEstimateController {
    private final DeliveryEstimateService service;
    private final VietmapGeocodingService geocoding;

    public DeliveryEstimateController(DeliveryEstimateService service, VietmapGeocodingService geocoding) {
        this.service = service;
        this.geocoding = geocoding;
    }

    @GetMapping("/addresses")
    public ResponseObject<List<DeliveryEstimateService.Suggestion>> search(@RequestParam String q, @RequestParam(defaultValue = "hcm") String city) {
        return new ResponseObject<>(200, "Success", service.search(q, city));
    }

    @GetMapping
    public ResponseObject<DeliveryEstimateService.Estimate> estimate(@RequestParam String refId, @RequestParam(defaultValue = "hcm") String city) {
        return new ResponseObject<>(200, "Success", service.estimate(refId, city));
    }

    @GetMapping("/geocode")
    public ResponseObject<VietmapGeocodingService.Location> geocode(@RequestParam String address, @RequestParam(defaultValue = "hcm") String city) {
        return new ResponseObject<>(200, "Success", geocoding.addressToCoordinates(address, city));
    }

    @GetMapping("/reverse")
    public ResponseObject<VietmapGeocodingService.Location> reverse(@RequestParam double lat, @RequestParam double lng) {
        return new ResponseObject<>(200, "Success", geocoding.coordinatesToAddress(lat, lng));
    }

    @GetMapping("/coordinates")
    public ResponseObject<DeliveryEstimateService.Estimate> estimateCoordinates(@RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "hcm") String city) {
        return new ResponseObject<>(200, "Success", service.estimateCoordinates(lat, lng, city, ""));
    }
}
