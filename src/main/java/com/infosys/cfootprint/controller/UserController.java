package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.UpdateAvatarRequest;
import com.infosys.cfootprint.dto.UpdateProfileRequest;
import com.infosys.cfootprint.dto.UserResponse;
import com.infosys.cfootprint.model.AvatarImage;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return ResponseEntity.ok(userService.getUserById(userDetails.getId()));
        }
        return ResponseEntity.status(401).build();
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return ResponseEntity.ok(userService.updateProfile(userDetails.getId(), request));
        }
        return ResponseEntity.status(401).build();
    }

    @PutMapping("/avatar")
    public ResponseEntity<UserResponse> updateAvatar(
            @Valid @RequestBody UpdateAvatarRequest request,
            Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return ResponseEntity.ok(userService.updateAvatar(userDetails.getId(), request.getAvatar()));
        }
        return ResponseEntity.status(401).build();
    }

    @PostMapping("/upload-avatar")
    public ResponseEntity<Map<String, String>> uploadAvatarImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            String avatarImageId = userService.uploadAvatarImage(user, file);
            String avatarUrl = "/api/users/avatar/" + user.getId();
            return ResponseEntity.ok(Map.of(
                    "avatarImageId", avatarImageId,
                    "avatarUrl", avatarUrl
            ));
        }
        return ResponseEntity.status(401).build();
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<byte[]> getAvatarImage(@PathVariable UUID userId) {
        AvatarImage img = userService.getAvatarImage(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + img.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .body(img.getData());
    }
}
