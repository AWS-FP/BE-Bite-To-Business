package com.se203730.mvc.generateuibe.features.delivery.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.DeliveryItemResponse;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.DeliveryStopResponse;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.DeliveryTripResponse;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.DeliveryTripSummaryResponse;
import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryStop;
import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryTrip;
import com.se203730.mvc.generateuibe.features.delivery.enums.DeliveryTripStatus;
import com.se203730.mvc.generateuibe.features.delivery.repository.DeliveryStopRepository;
import com.se203730.mvc.generateuibe.features.delivery.repository.DeliveryTripRepository;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import com.se203730.mvc.generateuibe.features.customer.enums.OrderStatus;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderPackageRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.projection.PackageComboItemView;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DeliveryTripService {
    private static final int SPEED_KM_PER_HOUR = 40;

    private static final List<DeliveryTripStatus> ACTIVE_STATUSES = List.of(
            DeliveryTripStatus.PREPARING, DeliveryTripStatus.READY, DeliveryTripStatus.IN_DELIVERY);

    private final DeliveryTripRepository trips;

    private final DeliveryStopRepository stops;

    private final OrderRepository orders;

    private final OrderPackageRepository packages;

    private final AccountAccessService accounts;

    private final DatabaseClock clock;

    public Page<DeliveryTripSummaryResponse> list(
            UUID staffId,
            DeliveryTripStatus status,
            int page,
            int size) {
        requireShipper(staffId);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
        Page<DeliveryTrip> result = trips.findAll((root, query, builder) -> {
            var assigned = builder.equal(root.get("deliveryStaffId"), staffId);
            return status == null
                    ? assigned
                    : builder.and(assigned, builder.equal(root.get("status"), status));
        }, PageRequest.of(page, size, sort));

        List<UUID> orderIds = result.stream().map(DeliveryTrip::getOrderId).toList();
        Map<UUID, Order> orderMap = orders.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return result.map(trip -> DeliveryTripSummaryResponse.from(trip,
                requireOrder(orderMap.get(trip.getOrderId()))));
    }

    public DeliveryTripResponse current(UUID staffId) {
        requireShipper(staffId);
        DeliveryTrip trip = trips.findByDeliveryStaffIdAndStatusIn(staffId, ACTIVE_STATUSES)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_TRIP_NOT_FOUND));
        return response(loadData(trip, findOrder(trip.getOrderId())));
    }

    public DeliveryTripResponse detail(UUID staffId, UUID tripId) {
        requireShipper(staffId);
        DeliveryTrip trip = trips.findByIdAndDeliveryStaffId(tripId, staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_TRIP_NOT_FOUND));
        return response(loadData(trip, findOrder(trip.getOrderId())));
    }

    @Transactional
    public DeliveryTripResponse start(UUID staffId, UUID tripId) {
        TripData data = lockAssignedTrip(staffId, tripId);
        requireNotCancelled(data.order());
        DeliveryTrip trip = data.trip();
        if (trip.getStatus() == DeliveryTripStatus.IN_DELIVERY) {
            requireInDelivery(data);
            return response(data);
        }
        if (trip.getStatus() == DeliveryTripStatus.COMPLETED) {
            if (data.order().getStatus() != OrderStatus.COMPLETED) {
                throw new BusinessException(ErrorCode.DELIVERY_DATA_INVALID);
            }
            return response(data);
        }
        if (trip.getStatus() != DeliveryTripStatus.READY) {
            throw new BusinessException(ErrorCode.DELIVERY_TRIP_INVALID_STATE);
        }
        requireProcessingOrder(data.order());
        if (!orders.hasSuccessfulPayment(data.order().getId(), data.order().getTotalAmount())) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_REQUIRED);
        }
        requireReadyStops(data);

        Instant now = clock.now();
        trip.setStatus(DeliveryTripStatus.IN_DELIVERY);
        trip.setStartedAt(now);
        trip.setUpdatedAt(now);
        trips.save(trip);
        return response(data);
    }

    @Transactional
    public DeliveryTripResponse confirmStop(UUID staffId, UUID tripId, UUID stopId) {
        TripData data = lockAssignedTrip(staffId, tripId);
        DeliveryStop stop = data.stops().stream().filter(item -> item.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_STOP_NOT_FOUND));
        requireNotCancelled(data.order());
        OrderPackage parcel = data.packages().get(stop.getOrderPackageId());
        if (parcel.getCancelledAt() != null) {
            throw new BusinessException(ErrorCode.DELIVERY_PACKAGE_CANCELLED);
        }
        if (stop.getDeliveredAt() != null) {
            return response(data);
        }
        requireInDelivery(data);

        Instant now = clock.now();
        stop.setDeliveredAt(now);
        stop.setUpdatedAt(now);
        data.trip().setUpdatedAt(now);
        stops.save(stop);
        trips.save(data.trip());
        return response(data);
    }

    @Transactional
    public DeliveryTripResponse complete(UUID staffId, UUID tripId) {
        TripData data = lockAssignedTrip(staffId, tripId);
        requireNotCancelled(data.order());
        if (data.trip().getStatus() == DeliveryTripStatus.COMPLETED) {
            if (data.order().getStatus() != OrderStatus.COMPLETED) {
                throw new BusinessException(ErrorCode.DELIVERY_DATA_INVALID);
            }
            return response(data);
        }
        requireInDelivery(data);
        List<OrderPackage> active = activePackages(data);
        requireActivePackages(active);
        Map<UUID, DeliveryStop> stopMap = stopsByPackage(data);
        for (OrderPackage parcel : active) {
            DeliveryStop stop = stopMap.get(parcel.getId());
            if (stop == null || stop.getDeliveredAt() == null) {
                throw new BusinessException(ErrorCode.DELIVERY_PACKAGES_NOT_DELIVERED);
            }
        }

        Instant now = clock.now();
        data.trip().setStatus(DeliveryTripStatus.COMPLETED);
        data.trip().setCompletedAt(now);
        data.trip().setUpdatedAt(now);
        data.order().setStatus(OrderStatus.COMPLETED);
        data.order().setCompletedAt(now);
        data.order().setUpdatedAt(now);
        trips.save(data.trip());
        orders.save(data.order());
        return response(data);
    }

    private void requireShipper(UUID staffId) {
        if (accounts.requireActiveUser(staffId).getRole() != UserRole.DELIVERY_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private TripData lockAssignedTrip(UUID staffId, UUID tripId) {
        requireShipper(staffId);
        UUID orderId = trips.findAssignedOrderId(tripId, staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_TRIP_NOT_FOUND));
        // Mọi command giao hàng khóa Order trước rồi Trip để tránh cập nhật chéo và deadlock.
        Order order = orders.findForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_DATA_INVALID));
        DeliveryTrip trip = trips.findAssignedForUpdate(tripId, staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_TRIP_NOT_FOUND));
        if (!orderId.equals(trip.getOrderId())) {
            throw new BusinessException(ErrorCode.DELIVERY_DATA_INVALID);
        }
        return loadData(trip, order);
    }

    private Order findOrder(UUID id) {
        return orders.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_DATA_INVALID));
    }

    private Order requireOrder(Order order) {
        if (order == null) {
            throw new BusinessException(ErrorCode.DELIVERY_DATA_INVALID);
        }
        return order;
    }

    private TripData loadData(DeliveryTrip trip, Order order) {
        Map<UUID, OrderPackage> parcels = packages.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .collect(Collectors.toMap(OrderPackage::getId, Function.identity()));
        List<DeliveryStop> tripStops = stops.findByTripIdOrderByStopOrderAsc(trip.getId());
        for (DeliveryStop stop : tripStops) {
            if (!parcels.containsKey(stop.getOrderPackageId())) {
                throw new BusinessException(ErrorCode.DELIVERY_DATA_INVALID);
            }
            if (stop.getDeliveredAt() != null && (trip.getStartedAt() == null
                    || stop.getDeliveredAt().isBefore(trip.getStartedAt()))) {
                throw new BusinessException(ErrorCode.DELIVERY_DATA_INVALID);
            }
        }
        return new TripData(trip, order, parcels, tripStops);
    }

    private List<OrderPackage> activePackages(TripData data) {
        return data.packages().values().stream().filter(parcel -> parcel.getCancelledAt() == null)
                .toList();
    }

    private Map<UUID, DeliveryStop> stopsByPackage(TripData data) {
        return data.stops().stream()
                .collect(Collectors.toMap(DeliveryStop::getOrderPackageId, Function.identity()));
    }

    private void requireActivePackages(List<OrderPackage> active) {
        if (active.isEmpty()) {
            throw new BusinessException(ErrorCode.DELIVERY_NO_ACTIVE_PACKAGES);
        }
    }

    private void requireReadyStops(TripData data) {
        List<OrderPackage> active = activePackages(data);
        requireActivePackages(active);
        Map<UUID, DeliveryStop> stopMap = stopsByPackage(data);
        var packagesWithItems = packages.findItems(data.order().getId()).stream()
                .map(item -> item.getPackageId()).collect(Collectors.toSet());
        for (OrderPackage parcel : active) {
            DeliveryStop stop = stopMap.get(parcel.getId());
            if (stop == null || stop.getDestinationLatitude() == null
                    || stop.getDestinationLongitude() == null || stop.getLegDistanceKm() == null
                    || stop.getLegDistanceKm().signum() < 0 || stop.getDeliveredAt() != null
                    || !packagesWithItems.contains(parcel.getId())) {
                throw new BusinessException(ErrorCode.DELIVERY_ROUTE_NOT_READY);
            }
        }
    }

    private void requireNotCancelled(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED || order.getCancelledAt() != null) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED);
        }
    }

    private void requireProcessingOrder(Order order) {
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.ORDER_NOT_READY_FOR_DELIVERY);
        }
    }

    private void requireInDelivery(TripData data) {
        if (data.trip().getStatus() != DeliveryTripStatus.IN_DELIVERY
                || data.trip().getStartedAt() == null) {
            throw new BusinessException(ErrorCode.DELIVERY_TRIP_INVALID_STATE);
        }
        requireProcessingOrder(data.order());
    }

    private DeliveryTripResponse response(TripData data) {
        Map<UUID, List<DeliveryItemResponse>> items = deliveryItems(data.order().getId());
        List<DeliveryStopResponse> result = new ArrayList<>();
        Instant departure = data.trip().getStartedAt() != null
                ? data.trip().getStartedAt()
                : data.trip().getPlannedDepartureAt();
        BigDecimal distance = BigDecimal.ZERO;
        List<OrderPackage> active = activePackages(data);
        boolean distanceKnown = !active.isEmpty() && stopsByPackage(data).keySet()
                .containsAll(active.stream().map(OrderPackage::getId).toList());
        int delivered = 0;
        for (DeliveryStop stop : data.stops()) {
            OrderPackage parcel = data.packages().get(stop.getOrderPackageId());
            Instant eta = null;
            if (parcel.getCancelledAt() == null) {
                if (stop.getDeliveredAt() != null) {
                    delivered++;
                }
                if (stop.getLegDistanceKm() == null) {
                    distanceKnown = false;
                } else {
                    distance = distance.add(stop.getLegDistanceKm());
                }
                if (distanceKnown && departure != null) {
                    eta = departure.plusSeconds(durationSeconds(distance));
                }
            }
            result.add(new DeliveryStopResponse(stop.getId(), parcel.getId(),
                    parcel.getPackageNumber(), parcel.getPackageName(), parcel.getDeliveryDate(),
                    parcel.getDeliveryAddress(), stop.getStopOrder(), stop.getDestinationLatitude(),
                    stop.getDestinationLongitude(), stop.getLegDistanceKm(), eta,
                    stop.getDeliveredAt(), parcel.getCancelledAt(),
                    items.getOrDefault(parcel.getId(), List.of())));
        }

        Long duration = distanceKnown ? durationSeconds(distance) : null;
        Instant arrival = duration != null && departure != null
                ? departure.plusSeconds(duration)
                : null;
        return new DeliveryTripResponse(DeliveryTripSummaryResponse.from(data.trip(), data.order()),
                data.order().getContactName(), data.order().getContactPhone(),
                data.order().getNote(),
                new DeliveryTripResponse.Origin(data.trip().getOriginLatitude(),
                        data.trip().getOriginLongitude()),
                data.trip().getRoutePolyline(), SPEED_KM_PER_HOUR, distanceKnown ? distance : null,
                duration, arrival, active.size(), delivered, List.copyOf(result));
    }

    private Map<UUID, List<DeliveryItemResponse>> deliveryItems(UUID orderId) {
        Map<UUID, Map<UUID, List<DeliveryItemResponse.ComboComponent>>> components = new HashMap<>();
        for (PackageComboItemView item : packages.findComboItems(orderId)) {
            components.computeIfAbsent(item.getPackageId(), key -> new HashMap<>())
                    .computeIfAbsent(item.getOrderItemId(), key -> new ArrayList<>())
                    .add(new DeliveryItemResponse.ComboComponent(item.getSku(), item.getNameVi(),
                            item.getNameEn(), item.getUnitCode(), item.getQuantity()));
        }
        Map<UUID, List<DeliveryItemResponse>> result = new HashMap<>();
        for (var item : packages.findItems(orderId)) {
            List<DeliveryItemResponse.ComboComponent> comboItems = components
                    .getOrDefault(item.getPackageId(), Map.of())
                    .getOrDefault(item.getOrderItemId(), List.of());
            result.computeIfAbsent(item.getPackageId(), key -> new ArrayList<>())
                    .add(new DeliveryItemResponse(item.getOrderItemId(), item.getSku(),
                            item.getNameVi(), item.getNameEn(), item.getUnitCode(),
                            item.getQuantity(),
                            item.getCustomerNote(), List.copyOf(comboItems)));
        }
        return result;
    }

    private long durationSeconds(BigDecimal distanceKm) {
        return distanceKm.multiply(BigDecimal.valueOf(3600))
                .divide(BigDecimal.valueOf(SPEED_KM_PER_HOUR), 0, RoundingMode.CEILING)
                .longValueExact();
    }

    private record TripData(
            DeliveryTrip trip,
            Order order,
            Map<UUID, OrderPackage> packages,
            List<DeliveryStop> stops
    ) {
    }
}
