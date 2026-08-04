package com.infosys.cfootprint.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

public class AvatarUtils {

    private static final Map<String, String> AVATAR_MAP = Map.of(
            "male-1", "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix",
            "male-2", "https://api.dicebear.com/7.x/avataaars/svg?seed=Jack",
            "male-3", "https://api.dicebear.com/7.x/avataaars/svg?seed=Alexander",
            "male-4", "https://api.dicebear.com/7.x/avataaars/svg?seed=Ryan",
            "female-1", "https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka",
            "female-2", "https://api.dicebear.com/7.x/avataaars/svg?seed=Sophia",
            "female-3", "https://api.dicebear.com/7.x/avataaars/svg?seed=Bella",
            "female-4", "https://api.dicebear.com/7.x/avataaars/svg?seed=Emily"
    );

    public static final Set<String> ALLOWED_AVATARS = AVATAR_MAP.keySet();

    public static String generateAvatarUrl(String username) {
        if (username == null || username.trim().isEmpty()) {
            username = "User";
        }

        // Calculate deterministic salt character (A-Z) from username ASCII sum
        int charSum = 0;
        for (char c : username.toCharArray()) {
            charSum += c;
        }
        char saltChar = (char) ('A' + (Math.abs(charSum) % 26));
        String uniqueSeed = username + "_" + saltChar;

        return "https://api.dicebear.com/10.x/initial-face/svg?seed=" + URLEncoder.encode(uniqueSeed, StandardCharsets.UTF_8);
    }

    public static String getAvatarUrl(String username) {
        return generateAvatarUrl(username);
    }

    public static String getAvatarUrl(com.infosys.cfootprint.model.User user) {
        if (user != null && user.getAvatarImageId() != null && !user.getAvatarImageId().isBlank()) {
            return "/api/users/avatar/" + user.getId();
        }
        return generateAvatarUrl(user != null ? user.getUsername() : "User");
    }

    public static String getAvatarUrl(String avatarKey, String gender) {
        if (avatarKey != null && AVATAR_MAP.containsKey(avatarKey.toLowerCase())) {
            return AVATAR_MAP.get(avatarKey.toLowerCase());
        }
        
        if (gender != null && gender.equalsIgnoreCase("Female")) {
            return AVATAR_MAP.get("female-1");
        }
        return AVATAR_MAP.get("male-1");
    }

    public static boolean isValidAvatar(String avatarKey) {
        return avatarKey != null && AVATAR_MAP.containsKey(avatarKey.toLowerCase());
    }
}
