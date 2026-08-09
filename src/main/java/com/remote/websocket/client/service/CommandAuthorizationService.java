package com.remote.websocket.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.remote.education.service.EducationControlService;
import com.remote.service.SessionPermissionService;
import com.remote.support.service.SupportSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.remote.common.ServerConstants.ACTION_GAMEPAD_CONNECT;
import static com.remote.common.ServerConstants.ACTION_GAMEPAD_DISCONNECT;
import static com.remote.common.ServerConstants.ACTION_GAMEPAD_STATE;
import static com.remote.common.ServerConstants.ACTION_KEY_COMBO;
import static com.remote.common.ServerConstants.ACTION_KEY_PRESS;
import static com.remote.common.ServerConstants.ACTION_KEY_RELEASE;
import static com.remote.common.ServerConstants.ACTION_MOUSE_CLICK;
import static com.remote.common.ServerConstants.ACTION_MOUSE_MOVE;
import static com.remote.common.ServerConstants.ACTION_MOUSE_WHEEL;

@Service
public class CommandAuthorizationService {

    @Autowired
    private SessionPermissionService sessionPermissionService;

    @Autowired
    private EducationControlService educationControlService;

    @Autowired
    private SupportSessionService supportSessionService;

    public boolean isAllowed(String profile,
                             String action,
                             String username,
                             Long pcId,
                             JsonNode json) {

        if ("personal".equals(profile)) {
            return sessionPermissionService.isCommandAllowed(profile, action)
                    || isGamepadAction(action);
        }

        if ("education_student".equals(profile)) {
            String educationCode = json.has("educationCode")
                    ? json.get("educationCode").asText()
                    : null;

            return educationCode != null
                    && !educationCode.isBlank()
                    && !"unknown".equals(username)
                    && isRemoteControlAction(action)
                    && educationControlService.hasControlInSession(username, educationCode);
        }

        if ("support_operator_view_client".equals(profile)) {
            String supportCode = json.has("supportCode")
                    ? json.get("supportCode").asText()
                    : null;

            return supportCode != null
                    && !supportCode.isBlank()
                    && !"unknown".equals(username)
                    && isRemoteControlAction(action)
                    && supportSessionService.hasOperatorControl(username, supportCode, pcId);
        }

        return false;
    }

    private boolean isGamepadAction(String action) {
        return ACTION_GAMEPAD_CONNECT.equals(action)
                || ACTION_GAMEPAD_STATE.equals(action)
                || ACTION_GAMEPAD_DISCONNECT.equals(action);
    }

    private boolean isRemoteControlAction(String action) {
        return ACTION_MOUSE_MOVE.equals(action)
                || ACTION_MOUSE_CLICK.equals(action)
                || ACTION_MOUSE_WHEEL.equals(action)
                || ACTION_KEY_PRESS.equals(action)
                || ACTION_KEY_RELEASE.equals(action)
                || ACTION_KEY_COMBO.equals(action);
    }
}