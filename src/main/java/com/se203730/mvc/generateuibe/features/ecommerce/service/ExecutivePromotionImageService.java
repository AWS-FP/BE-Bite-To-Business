package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Uploads a campaign image; the returned URL is attached via the promotion update API. */
@Service
public class ExecutivePromotionImageService {
    private static final long MAX_BYTES = 5L * 1024L * 1024L;
    @Value("${app.cloudinary.cloud-name:}") private String cloudName;
    @Value("${app.cloudinary.api-key:}") private String apiKey;
    @Value("${app.cloudinary.api-secret:}") private String apiSecret;

    public record UploadedImage(String imageUrl, String publicId) {}

    public UploadedImage upload(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        try {
            byte[] bytes = file.getBytes();
            if (!isSupportedImage(bytes)) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
            Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                "folder", "btb/ecommerce/promotions", "resource_type", "image", "overwrite", false));
            Object url = result.get("secure_url");
            Object id = result.get("public_id");
            if (!(url instanceof String secureUrl) || !secureUrl.startsWith("https://") ||
                !(id instanceof String publicId) || publicId.isBlank()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            return new UploadedImage(secureUrl, publicId);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, ex);
        }
    }

    private static boolean isSupportedImage(byte[] bytes) {
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        boolean png = bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
        boolean webp = bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        return jpeg || png || webp;
    }
}
