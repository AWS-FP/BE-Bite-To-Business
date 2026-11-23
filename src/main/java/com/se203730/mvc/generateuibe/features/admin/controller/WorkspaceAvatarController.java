package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.admin.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.request.RestoreAvatarRequest;
import jakarta.validation.Valid;
import com.se203730.mvc.generateuibe.features.admin.service.WorkspaceAvatarService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/workspace/profile")
@PreAuthorize("hasAnyRole('ADMIN', 'ECOMMERCE_EXECUTIVE', 'LOGISTICS_STAFF', 'DELIVERY_STAFF')")
@Tag(name = "Workspace - Profile")
public class WorkspaceAvatarController {
    @Autowired
    private WorkspaceAvatarService avatars;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseObject<UserResponse>> updateAvatar(
            @CurrentUserUUID UUID userId,
            @RequestPart("file") MultipartFile file) {
        UserResponse user = avatars.updateAvatar(userId, file);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Avatar updated.", user));
    }
    @GetMapping("/avatars")
    public ResponseEntity<ResponseObject<List<String>>> recentAvatars(@CurrentUserUUID UUID userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", avatars.recent(userId)));
    }

    @PutMapping("/avatar/restore")
    public ResponseEntity<ResponseObject<UserResponse>> restoreAvatar(
            @CurrentUserUUID UUID userId, @Valid @RequestBody RestoreAvatarRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Avatar restored.", avatars.restore(userId, request.avatarUrl())));
    }
}
