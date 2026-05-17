package com.remote.service;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SessionPermissionService {

    private static final Set<String> PERSONAL_ALLOWED_ACTIONS = Set.of(
            "MOUSE_MOVE",
            "MOUSE_CLICK",
            "MOUSE_WHEEL",
            "KEY_PRESS",
            "KEY_RELEASE",
            "KEY_COMBO",
            "SOFT_SLEEP",
            "SOFT_WAKE",
            "FILE_DOWNLOAD",
            "FILE_PAUSE",
            "FILE_RESUME",
            "FILE_CANCEL",
            "FILE_START",
            "FILE_CHUNK",
            "FILE_END"
    );

    private static final Set<String> EDUCATION_STUDENT_ALLOWED_ACTIONS = Set.of(
            "FILE_PAUSE",
            "FILE_RESUME",
            "FILE_CANCEL"
    );

    private static final Set<String> SUPPORT_OPERATOR_ALLOWED_ACTIONS = Set.of(
            "MOUSE_MOVE",
            "MOUSE_CLICK",
            "MOUSE_WHEEL",
            "KEY_PRESS",
            "KEY_RELEASE",
            "FILE_DOWNLOAD",
            "FILE_PAUSE",
            "FILE_RESUME",
            "FILE_CANCEL"
    );

    private static final Set<String> PRESENTATION_REMOTE_ALLOWED_ACTIONS = Set.of(
            "KEY_PRESS",
            "KEY_RELEASE",
            "MOUSE_MOVE",
            "MOUSE_CLICK",
            "MOUSE_WHEEL"
    );

    public boolean isCommandAllowed(String profile, String action) {
        String normalizedProfile = normalizeProfile(profile);

        if (action == null || action.isBlank()) {
            return false;
        }

        return switch (normalizedProfile) {
            case "personal" -> PERSONAL_ALLOWED_ACTIONS.contains(action);
            case "education_student" -> EDUCATION_STUDENT_ALLOWED_ACTIONS.contains(action);
            case "support_operator" -> SUPPORT_OPERATOR_ALLOWED_ACTIONS.contains(action);
            case "presentation_remote" -> PRESENTATION_REMOTE_ALLOWED_ACTIONS.contains(action);
            default -> false;
        };
    }

    public String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return "personal";
        }

        return profile.trim().toLowerCase();
    }
}