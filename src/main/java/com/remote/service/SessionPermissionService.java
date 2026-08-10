package com.remote.service;

import org.springframework.stereotype.Service;

import java.util.Set;

import static com.remote.common.ServerConstants.*;

@Service
public class SessionPermissionService {

    private static final Set<String> PERSONAL_ALLOWED_ACTIONS = Set.of(
            ACTION_MOUSE_MOVE,
            ACTION_MOUSE_CLICK,
            ACTION_MOUSE_WHEEL,
            ACTION_KEY_PRESS,
            ACTION_KEY_RELEASE,
            ACTION_KEY_COMBO,
            ACTION_SOFT_SLEEP,
            ACTION_SOFT_WAKE,
            ACTION_FILE_DOWNLOAD,
            ACTION_FILE_PAUSE,
            ACTION_FILE_RESUME,
            ACTION_FILE_CANCEL,
            ACTION_FILE_START,
            ACTION_FILE_CHUNK,
            ACTION_FILE_END
    );

    private static final Set<String> EDUCATION_STUDENT_ALLOWED_ACTIONS = Set.of(
            ACTION_FILE_PAUSE,
            ACTION_FILE_RESUME,
            ACTION_FILE_CANCEL
    );

    private static final Set<String> SUPPORT_OPERATOR_ALLOWED_ACTIONS = Set.of(
            ACTION_MOUSE_MOVE,
            ACTION_MOUSE_CLICK,
            ACTION_MOUSE_WHEEL,
            ACTION_KEY_PRESS,
            ACTION_KEY_RELEASE,
            ACTION_FILE_DOWNLOAD,
            ACTION_FILE_PAUSE,
            ACTION_FILE_RESUME,
            ACTION_FILE_CANCEL
    );

    private static final Set<String> PRESENTATION_REMOTE_ALLOWED_ACTIONS = Set.of(
            ACTION_KEY_PRESS,
            ACTION_KEY_RELEASE,
            ACTION_MOUSE_MOVE,
            ACTION_MOUSE_CLICK,
            ACTION_MOUSE_WHEEL
    );

    public boolean isCommandAllowed(String profile, String action) {
        String normalizedProfile = normalizeProfile(profile);

        if (action == null || action.isBlank()) {
            return false;
        }

        return switch (normalizedProfile) {
            case PROFILE_PERSONAL -> PERSONAL_ALLOWED_ACTIONS.contains(action);
            case PROFILE_EDUCATION_STUDENT -> EDUCATION_STUDENT_ALLOWED_ACTIONS.contains(action);
            case "support_operator" -> SUPPORT_OPERATOR_ALLOWED_ACTIONS.contains(action);
            case PROFILE_PRESENTATION_REMOTE -> PRESENTATION_REMOTE_ALLOWED_ACTIONS.contains(action);
            default -> false;
        };
    }

    public String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return PROFILE_PERSONAL;
        }

        return profile.trim().toLowerCase();
    }
}