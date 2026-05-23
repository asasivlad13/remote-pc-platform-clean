package com.remote.service;

import com.remote.model.*;
import com.remote.repository.SupportFileTransferRepository;
import com.remote.repository.SupportSessionRepository;
import com.remote.repository.UserRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupportFileTransferService {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L;

    private final SupportFileTransferRepository supportFileTransferRepository;
    private final SupportSessionRepository supportSessionRepository;
    private final UserRepository userRepository;

    public SupportFileTransferService(SupportFileTransferRepository supportFileTransferRepository,
                                      SupportSessionRepository supportSessionRepository,
                                      UserRepository userRepository) {
        this.supportFileTransferRepository = supportFileTransferRepository;
        this.supportSessionRepository = supportSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFiles(String username, String sessionCode) {
        User currentUser = findUser(username);
        SupportSession session = findSession(sessionCode);
        checkActiveParticipant(session, currentUser);

        return supportFileTransferRepository.findBySupportSessionOrderByCreatedAtDesc(session)
                .stream()
                .map(file -> toResponse(file, currentUser))
                .toList();
    }

    @Transactional
    public Map<String, Object> uploadFromOperator(String username, String sessionCode, MultipartFile multipartFile) {
        User operator = findUser(username);
        SupportSession session = findSession(sessionCode);
        checkActiveParticipant(session, operator);

        if (session.getOperator() == null || !session.getOperator().getId().equals(operator.getId())) {
            throw new IllegalArgumentException("Файлы может отправлять только оператор");
        }

        if (session.getClient() == null) {
            throw new IllegalArgumentException("Клиент ещё не подключился к сессии");
        }

        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран");
        }

        if (multipartFile.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Файл слишком большой. Максимум 25 МБ");
        }

        SupportFileTransfer file = new SupportFileTransfer();
        file.setSupportSession(session);
        file.setSender(operator);
        file.setRecipient(session.getClient());
        file.setOriginalFilename(safeFilename(multipartFile.getOriginalFilename()));
        file.setContentType(multipartFile.getContentType() != null
                ? multipartFile.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        file.setSizeBytes(multipartFile.getSize());
        file.setStatus(SupportFileTransferStatus.PENDING);

        try {
            file.setFileData(multipartFile.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать файл");
        }

        return toResponse(supportFileTransferRepository.save(file), operator);
    }

    @Transactional
    public Map<String, Object> accept(String username, String sessionCode, Long fileId) {
        User client = findUser(username);
        SupportSession session = findSession(sessionCode);
        checkActiveParticipant(session, client);
        checkClient(session, client);

        SupportFileTransfer file = findFileInSession(fileId, session);
        file.setStatus(SupportFileTransferStatus.ACCEPTED);
        file.setDecidedAt(LocalDateTime.now());

        return toResponse(supportFileTransferRepository.save(file), client);
    }

    @Transactional
    public Map<String, Object> reject(String username, String sessionCode, Long fileId) {
        User client = findUser(username);
        SupportSession session = findSession(sessionCode);
        checkActiveParticipant(session, client);
        checkClient(session, client);

        SupportFileTransfer file = findFileInSession(fileId, session);
        file.setStatus(SupportFileTransferStatus.REJECTED);
        file.setDecidedAt(LocalDateTime.now());

        return toResponse(supportFileTransferRepository.save(file), client);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> download(String username, String sessionCode, Long fileId) {
        User currentUser = findUser(username);
        SupportSession session = findSession(sessionCode);
        checkActiveParticipant(session, currentUser);

        SupportFileTransfer file = findFileInSession(fileId, session);

        boolean isOperator = session.getOperator() != null
                && session.getOperator().getId().equals(currentUser.getId());
        boolean isClient = session.getClient() != null
                && session.getClient().getId().equals(currentUser.getId());

        if (isClient && file.getStatus() != SupportFileTransferStatus.ACCEPTED) {
            throw new IllegalArgumentException("Сначала примите файл");
        }

        if (!isOperator && !isClient) {
            throw new IllegalArgumentException("Нет доступа к файлу");
        }

        String contentType = file.getContentType() != null
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(file.getFileData());
    }

    private SupportSession findSession(String sessionCode) {
        return supportSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Сессия технической поддержки не найдена"));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private SupportFileTransfer findFileInSession(Long fileId, SupportSession session) {
        SupportFileTransfer file = supportFileTransferRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));

        if (file.getSupportSession() == null || !file.getSupportSession().getId().equals(session.getId())) {
            throw new IllegalArgumentException("Файл не относится к этой сессии");
        }

        return file;
    }

    private void checkActiveParticipant(SupportSession session, User currentUser) {
        if (session.getStatus() != SupportSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Файлы доступны только в активной сессии техподдержки");
        }

        boolean isOperator = session.getOperator() != null
                && session.getOperator().getId().equals(currentUser.getId());
        boolean isClient = session.getClient() != null
                && session.getClient().getId().equals(currentUser.getId());

        if (!isOperator && !isClient) {
            throw new IllegalArgumentException("Нет доступа к файлам этой сессии");
        }
    }

    private void checkClient(SupportSession session, User client) {
        if (session.getClient() == null || !session.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Только клиент этой сессии может выполнить действие");
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }

        return filename.replace("\\", "_").replace("/", "_").trim();
    }

    private Map<String, Object> toResponse(SupportFileTransfer file, User currentUser) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", file.getId());
        response.put("filename", file.getOriginalFilename());
        response.put("contentType", file.getContentType());
        response.put("sizeBytes", file.getSizeBytes());
        response.put("sizeText", formatSize(file.getSizeBytes()));
        response.put("status", file.getStatus());
        response.put("createdAt", file.getCreatedAt());
        response.put("decidedAt", file.getDecidedAt());
        response.put("senderId", file.getSender().getId());
        response.put("senderUsername", file.getSender().getUsername());
        response.put("recipientId", file.getRecipient().getId());
        response.put("recipientUsername", file.getRecipient().getUsername());
        response.put("mine", file.getSender().getId().equals(currentUser.getId()));

        return response;
    }

    private String formatSize(Long bytes) {
        if (bytes == null) {
            return "0 Б";
        }

        if (bytes < 1024) {
            return bytes + " Б";
        }

        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f КБ", kb);
        }

        double mb = kb / 1024.0;
        return String.format("%.1f МБ", mb);
    }
}
