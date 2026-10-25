package com.se203730.mvc.generateuibe.features.auth.service;

import com.se203730.mvc.generateuibe.features.auth.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.auth.dto.request.RegisterRequest;
import com.se203730.mvc.generateuibe.features.auth.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.user.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import com.se203730.mvc.generateuibe.features.user.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.user.repository.UserRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationDeliveryTest {
    @Mock UserRepository users;
    @Mock UserProfileRepository profiles;
    @Mock RegistrationRequestRepository registrations;
    @Mock SupabaseAuthClient supabase;
    @Mock TransactionTemplate transaction;
    @InjectMocks RegistrationService service;

    @Test
    void storesDeliveryNoteAndBuildsInvoiceAddressWithoutDistrict() {
        UUID id = UUID.randomUUID();
        when(supabase.createPendingUser("delivery-test@gmail.com")).thenReturn(id);
        when(transaction.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        service.register(request("  Gọi trước khi giao  ", "Thành phố Hồ Chí Minh", null));

        ArgumentCaptor<UserProfile> profile = ArgumentCaptor.forClass(UserProfile.class);
        verify(profiles).saveAndFlush(profile.capture());
        assertThat(profile.getValue().getUserId()).isEqualTo(id);
        assertThat(profile.getValue().getDistrict()).isNull();
        assertThat(profile.getValue().getDeliveryNote()).isEqualTo("Gọi trước khi giao");
        assertThat(profile.getValue().getDeliveryAddress()).isEqualTo("12 Nguyễn Huệ, khu phố 1");
        assertThat(profile.getValue().getFullDeliveryAddress())
                .isEqualTo("12 Nguyễn Huệ, khu phố 1, Phường Sài Gòn, Thành phố Hồ Chí Minh");
        assertThat(profile.getValue().getInvoiceAddress()).isEqualTo(profile.getValue().getFullDeliveryAddress());
    }

    @Test
    void deliveryNoteAndDistrictAreOptionalButOtherCitiesAndLongNotesAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var emptyNote = request("  ", "Thành phố Hà Nội", null);
            assertThat(emptyNote.deliveryNote()).isNull();
            assertThat(validator.validate(emptyNote)).isEmpty();
            assertThat(validator.validate(request("a".repeat(1001), "Thành phố Hà Nội", null)))
                    .extracting(error -> error.getPropertyPath().toString()).contains("deliveryNote");
            assertThat(validator.validate(request(null, "Thành phố Đà Nẵng", null)))
                    .extracting(error -> error.getPropertyPath().toString()).contains("province");
        }
    }

    @Test
    void fullAddressStillIncludesDistrictForExistingProfiles() {
        UserProfile profile = new UserProfile();
        profile.setDeliveryAddress(" 12 Nguyễn Huệ ");
        profile.setWard("Phường Bến Nghé");
        profile.setDistrict("Quận 1");
        profile.setProvince("Thành phố Hồ Chí Minh");
        assertThat(profile.getFullDeliveryAddress())
                .isEqualTo("12 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh");
        assertThat(new UserProfile().getFullDeliveryAddress()).isEmpty();
    }

    private RegisterRequest request(String note, String city, String district) {
        return new RegisterRequest(
                "An", "Nguyễn", "delivery-test@gmail.com", "+84912345678",
                CustomerType.BUSINESS_COOPERATIVE, "Bếp Mộc", city, district, "Phường Sài Gòn",
                "12 Nguyễn Huệ, khu phố 1", note, true, null, null, null, null
        );
    }
}
