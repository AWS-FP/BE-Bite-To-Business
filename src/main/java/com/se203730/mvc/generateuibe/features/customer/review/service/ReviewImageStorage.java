package com.se203730.mvc.generateuibe.features.customer.review.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ReviewImageStorage {
    public static final int MAX_IMAGES = 5;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    @Value("${app.cloudinary.cloud-name:}") private String cloudName;
    @Value("${app.cloudinary.api-key:}") private String apiKey;
    @Value("${app.cloudinary.api-secret:}") private String apiSecret;

    public record UploadedImage(String url, String publicId) {}

    public void validate(List<MultipartFile> images) {
        if (images == null || images.isEmpty() || images.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty() || image.getSize() > MAX_FILE_BYTES) {
                throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
            }
        }
    }

    public List<UploadedImage> uploadBatch(List<MultipartFile> images) {
        validate(images);
        Cloudinary cloudinary = configuredCloudinary();
        List<UploadedImage> uploaded = new ArrayList<>();
        try {
            for (MultipartFile file : images) {
                byte[] data = file.getBytes();
                if (!isSupportedImage(data)) {
                    throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
                }
                Map<?, ?> result = cloudinary.uploader().upload(data, ObjectUtils.asMap(
                        "folder", "product_reviews", "resource_type", "image", "overwrite", false));
                Object secureUrl = result.get("secure_url");
                Object publicId = result.get("public_id");
                if (!(secureUrl instanceof String url) || !url.startsWith("https://")
                        || !(publicId instanceof String id) || id.isBlank()) {
                    throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                }
                uploaded.add(new UploadedImage(url, id));
            }
        } catch (IOException e) {
            cleanup(uploaded);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, e);
        } catch (RuntimeException e) {
            cleanup(uploaded);
            throw e;
        }
        return List.copyOf(uploaded);
    }

    public void cleanup(List<UploadedImage> images) {
        for (UploadedImage image : images) deleteBestEffort(image.publicId());
    }

    public void deleteBestEffort(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            configuredCloudinary().uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", "image", "invalidate", true));
        } catch (IOException | RuntimeException e) {
            log.warn("Unable to remove review image from Cloudinary; public_id={}", publicId, e);
        }
    }

    private Cloudinary configuredCloudinary() {
        if (cloudName == null || cloudName.isBlank() || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
    }

    private boolean isSupportedImage(byte[] b) {
        boolean jpg = b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
        boolean png = b.length >= 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78
                && b[3] == 71 && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10;
        boolean webp = b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F'
                && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpg || png || webp;
    }
}
