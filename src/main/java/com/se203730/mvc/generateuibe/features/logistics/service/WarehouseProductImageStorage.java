package com.se203730.mvc.generateuibe.features.logistics.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class WarehouseProductImageStorage {
    @Value("${app.cloudinary.cloud-name:}") private String cloudName;
    @Value("${app.cloudinary.api-key:}") private String apiKey;
    @Value("${app.cloudinary.api-secret:}") private String apiSecret;

    public record Image(String url, String publicId) {}

    public Image upload(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024) throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        try {
            byte[] b = file.getBytes();
            boolean jpg = b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
            boolean png = b.length >= 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71 && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10;
            boolean webp = b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            if (!jpg && !png && !webp) throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            Map<?, ?> result = cloudinary().uploader().upload(b, ObjectUtils.asMap("folder", "products", "resource_type", "image", "overwrite", false));
            Object url = result.get("secure_url");
            Object id = result.get("public_id");
            if (!(url instanceof String value) || !value.startsWith("https://") || !(id instanceof String publicId) || publicId.isBlank()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            return new Image(value, publicId);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, ex);
        }
    }

    public void cleanup(Image image) {
        try {
            cloudinary().uploader().destroy(image.publicId(), ObjectUtils.asMap("resource_type", "image", "invalidate", true));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not remove product image after rollback", ex);
        }
    }

    private Cloudinary cloudinary() {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        return new Cloudinary(ObjectUtils.asMap("cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
    }
}
