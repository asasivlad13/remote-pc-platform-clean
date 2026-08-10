package com.remote.websocket.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.remote.education.service.EducationControlService;
import com.remote.support.service.SupportSessionService;
import com.remote.websocket.agent.AgentWebSocketHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.remote.common.ServerConstants.MESSAGE_REMOTE_FILE_DOWNLOAD;
import static com.remote.common.ServerConstants.MESSAGE_REMOTE_FILE_DOWNLOAD_COMPLETE;
import static com.remote.common.ServerConstants.MESSAGE_REMOTE_FILE_ERROR;
import static com.remote.common.ServerConstants.MESSAGE_REMOTE_FILE_LIST;
import static com.remote.common.ServerConstants.MESSAGE_REMOTE_FILE_LIST_RESULT;
import static com.remote.common.ServerConstants.PROFILE_EDUCATION_STUDENT;
import static com.remote.common.ServerConstants.PROFILE_PERSONAL;
import static com.remote.common.ServerConstants.PROFILE_SUPPORT_OPERATOR_VIEW_CLIENT;

@Service
public class RemoteFileWebSocketService {

    private final AgentWebSocketHandler agentWebSocketHandler;
    private final EducationControlService educationControlService;
    private final SupportSessionService supportSessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> requestOwners = new ConcurrentHashMap<>();

    public RemoteFileWebSocketService(@Lazy AgentWebSocketHandler agentWebSocketHandler,
                                      EducationControlService educationControlService,
                                      SupportSessionService supportSessionService) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.educationControlService = educationControlService;
        this.supportSessionService = supportSessionService;
    }

    public void handleRemoteFileList(WebSocketSession session,
                                     JsonNode json,
                                     String profile,
                                     String username) throws Exception {
        if (!json.has("pcId")) {
            sendError(session, null, "PC is not specified");
            return;
        }

        Long pcId = json.get("pcId").asLong();
        String requestId = json.has("requestId")
                ? json.get("requestId").asText()
                : UUID.randomUUID().toString();

        String path = json.has("path") && !json.get("path").isNull()
                ? json.get("path").asText()
                : "ROOTS";

        if (!isAccessAllowed(profile, username, pcId, json)) {
            sendError(
                    session,
                    requestId,
                    "Доступ к файлам удалённого ПК запрещён для текущего сценария"
            );
            return;
        }

        requestOwners.put(requestId, session);

        ObjectNode command = objectMapper.createObjectNode();
        command.put("type", MESSAGE_REMOTE_FILE_LIST);
        command.put("requestId", requestId);
        command.put("path", path);

        agentWebSocketHandler.sendCommandToAgent(pcId, command);
    }

    public void handleRemoteFileDownload(WebSocketSession session,
                                         JsonNode json,
                                         String profile,
                                         String username) throws Exception {
        if (!json.has("pcId") || !json.has("path")) {
            sendError(session, null, "PC or file path is not specified");
            return;
        }

        Long pcId = json.get("pcId").asLong();
        String requestId = json.has("requestId")
                ? json.get("requestId").asText()
                : UUID.randomUUID().toString();

        String path = json.get("path").asText();

        if (!isAccessAllowed(profile, username, pcId, json)) {
            sendError(
                    session,
                    requestId,
                    "Скачивание файлов с удалённого ПК запрещено для текущего сценария"
            );
            return;
        }

        requestOwners.put(requestId, session);

        ObjectNode command = objectMapper.createObjectNode();
        command.put("type", MESSAGE_REMOTE_FILE_DOWNLOAD);
        command.put("requestId", requestId);
        command.put("path", path);

        agentWebSocketHandler.sendCommandToAgent(pcId, command);
    }

    public void forwardRemoteFileMessage(JsonNode json) throws Exception {
        String requestId = json.has("requestId")
                ? json.get("requestId").asText()
                : null;

        if (requestId == null || requestId.isBlank()) {
            return;
        }

        WebSocketSession owner = requestOwners.get(requestId);

        if (owner == null || !owner.isOpen()) {
            requestOwners.remove(requestId);
            return;
        }

        owner.sendMessage(
                new TextMessage(objectMapper.writeValueAsString(json))
        );

        String type = json.has("type")
                ? json.get("type").asText()
                : "";

        if (MESSAGE_REMOTE_FILE_LIST_RESULT.equals(type)
                || MESSAGE_REMOTE_FILE_DOWNLOAD_COMPLETE.equals(type)
                || MESSAGE_REMOTE_FILE_ERROR.equals(type)) {
            requestOwners.remove(requestId);
        }
    }

    public void removeOwnerSession(WebSocketSession session) {
        requestOwners.entrySet()
                .removeIf(entry -> entry.getValue().getId().equals(session.getId()));
    }

    private boolean isAccessAllowed(String profile,
                                    String username,
                                    Long pcId,
                                    JsonNode json) {
        if (PROFILE_PERSONAL.equals(profile)) {
            return true;
        }

        if (PROFILE_EDUCATION_STUDENT.equals(profile)) {
            String educationCode = json.has("educationCode")
                    ? json.get("educationCode").asText()
                    : null;

            return educationCode != null
                    && !educationCode.isBlank()
                    && !"unknown".equals(username)
                    && educationControlService.hasControlInSession(
                    username,
                    educationCode
            );
        }

        if (PROFILE_SUPPORT_OPERATOR_VIEW_CLIENT.equals(profile)) {
            String supportCode = json.has("supportCode")
                    ? json.get("supportCode").asText()
                    : null;

            return supportCode != null
                    && !supportCode.isBlank()
                    && !"unknown".equals(username)
                    && supportSessionService.hasOperatorControl(
                    username,
                    supportCode,
                    pcId
            );
        }

        return false;
    }

    private void sendError(WebSocketSession session,
                           String requestId,
                           String message) throws Exception {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("type", MESSAGE_REMOTE_FILE_ERROR);

        if (requestId != null) {
            error.put("requestId", requestId);
        }

        error.put("message", message);

        session.sendMessage(new TextMessage(error.toString()));
    }
}