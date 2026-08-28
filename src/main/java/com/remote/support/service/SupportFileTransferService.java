package com.remote.support.service;

import com.remote.common.crypto.SharedCryptoService;
import com.remote.core.model.User;
import com.remote.core.repository.UserRepository;
import com.remote.support.dto.SupportFileTransferResponse;
import com.remote.support.model.SupportFileTransfer;
import com.remote.support.model.SupportFileTransferStatus;
import com.remote.support.model.SupportSession;
import com.remote.support.model.SupportSessionStatus;
import com.remote.support.repository.SupportFileTransferRepository;
import com.remote.support.repository.SupportSessionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class SupportFileTransferService {

    private static final long MAX_FILE_SIZE_BYTES =
            25L * 1024L * 1024L;

    private static final List<String> BLOCKED_EXTENSIONS = List.of(
            ".exe",
            ".bat",
            ".cmd",
            ".ps1",
            ".vbs",
            ".msi",
            ".scr",
            ".jar"
    );

    private final SupportFileTransferRepository supportFileTransferRepository;
    private final SupportSessionRepository supportSessionRepository;
    private final UserRepository userRepository;
    private final SharedCryptoService cryptoService;

    public SupportFileTransferService(
            SupportFileTransferRepository supportFileTransferRepository,
            SupportSessionRepository supportSessionRepository,
            UserRepository userRepository,
            SharedCryptoService cryptoService
    ) {
        this.supportFileTransferRepository =
                supportFileTransferRepository;

        this.supportSessionRepository =
                supportSessionRepository;

        this.userRepository =
                userRepository;

        this.cryptoService =
                cryptoService;
    }

    @Transactional(readOnly = true)
    public List<SupportFileTransferResponse> getFiles(
            String username,
            String sessionCode
    ) {
        SupportSession session =
                getSession(sessionCode);

        User currentUser =
                getUser(username);

        checkSessionParticipant(
                session,
                currentUser
        );

        return supportFileTransferRepository
                .findBySupportSessionOrderByCreatedAtDesc(session)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SupportFileTransferResponse uploadFromOperator(
            String username,
            String sessionCode,
            MultipartFile multipartFile
    ) {
        SupportSession session =
                getSession(sessionCode);

        User operator =
                getUser(username);

        checkSessionActive(session);
        checkOperator(session, operator);

        if (session.getClient() == null) {
            throw new IllegalArgumentException(
                    "Клиент ещё не подключился к сессии"
            );
        }

        validateFile(multipartFile);

        try {
            ByteArrayOutputStream encryptedOutputStream =
                    new ByteArrayOutputStream();

            cryptoService.encryptStream(
                    multipartFile.getInputStream(),
                    encryptedOutputStream
            );

            SupportFileTransfer fileTransfer =
                    new SupportFileTransfer();

            fileTransfer.setSupportSession(session);
            fileTransfer.setSender(operator);
            fileTransfer.setRecipient(session.getClient());

            fileTransfer.setOriginalFilename(
                    cleanFilename(
                            multipartFile.getOriginalFilename()
                    )
            );

            fileTransfer.setContentType(
                    multipartFile.getContentType()
            );

            fileTransfer.setSizeBytes(
                    multipartFile.getSize()
            );

            fileTransfer.setFileData(
                    encryptedOutputStream.toByteArray()
            );

            fileTransfer.setStatus(
                    SupportFileTransferStatus.PENDING
            );

            SupportFileTransfer savedFile =
                    supportFileTransferRepository.save(
                            fileTransfer
                    );

            return toResponse(savedFile);

        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Ошибка отправки файла",
                    e
            );
        }
    }

    @Transactional
    public SupportFileTransferResponse accept(
            String username,
            String sessionCode,
            Long fileId
    ) {
        SupportFileTransfer file =
                getFile(fileId);

        if (!file.getSupportSession()
                .getSessionCode()
                .equals(sessionCode)) {

            throw new IllegalArgumentException(
                    "Файл не относится к этой сессии техподдержки"
            );
        }

        return acceptFile(
                username,
                fileId
        );
    }

    @Transactional
    public SupportFileTransferResponse reject(
            String username,
            String sessionCode,
            Long fileId
    ) {
        SupportFileTransfer file =
                getFile(fileId);

        if (!file.getSupportSession()
                .getSessionCode()
                .equals(sessionCode)) {

            throw new IllegalArgumentException(
                    "Файл не относится к этой сессии техподдержки"
            );
        }

        return rejectFile(
                username,
                fileId
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> download(
            String username,
            String sessionCode,
            Long fileId
    ) {
        SupportFileTransfer file =
                getFile(fileId);

        if (!file.getSupportSession()
                .getSessionCode()
                .equals(sessionCode)) {

            throw new IllegalArgumentException(
                    "Файл не относится к этой сессии техподдержки"
            );
        }

        return downloadFile(
                username,
                fileId
        );
    }

    @Transactional
    public SupportFileTransferResponse acceptFile(
            String username,
            Long fileId
    ) {
        SupportFileTransfer file =
                getFile(fileId);

        User client =
                getUser(username);

        checkRecipient(file, client);
        checkSessionActive(
                file.getSupportSession()
        );

        if (file.getStatus()
                != SupportFileTransferStatus.PENDING) {

            throw new IllegalArgumentException(
                    "Решение по файлу уже принято"
            );
        }

        file.accept();

        SupportFileTransfer savedFile =
                supportFileTransferRepository.save(file);

        return toResponse(savedFile);
    }

    @Transactional
    public SupportFileTransferResponse rejectFile(
            String username,
            Long fileId
    ) {
        SupportFileTransfer file =
                getFile(fileId);

        User client =
                getUser(username);

        checkRecipient(file, client);
        checkSessionActive(
                file.getSupportSession()
        );

        if (file.getStatus()
                != SupportFileTransferStatus.PENDING) {

            throw new IllegalArgumentException(
                    "Решение по файлу уже принято"
            );
        }

        file.reject();

        SupportFileTransfer savedFile =
                supportFileTransferRepository.save(file);

        return toResponse(savedFile);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadFile(
            String username,
            Long fileId
    ) {
        SupportFileTransfer file =
                getFile(fileId);

        User currentUser =
                getUser(username);

        checkSessionParticipant(
                file.getSupportSession(),
                currentUser
        );

        boolean isSender =
                file.getSender() != null
                        && file.getSender()
                        .getId()
                        .equals(currentUser.getId());

        boolean isRecipient =
                file.getRecipient() != null
                        && file.getRecipient()
                        .getId()
                        .equals(currentUser.getId());

        if (!isSender && !isRecipient) {
            throw new IllegalArgumentException(
                    "Нет доступа к этому файлу"
            );
        }

        if (isRecipient
                && file.getStatus()
                != SupportFileTransferStatus.ACCEPTED) {

            throw new IllegalArgumentException(
                    "Файл можно скачать только после принятия"
            );
        }

        try {
            InputStream decryptedInputStream =
                    cryptoService.decryptStream(
                            new ByteArrayInputStream(
                                    file.getFileData()
                            )
                    );

            byte[] decryptedBytes =
                    decryptedInputStream.readAllBytes();

            String encodedFilename =
                    URLEncoder.encode(
                                    file.getOriginalFilename(),
                                    StandardCharsets.UTF_8
                            )
                            .replace("+", "%20");

            MediaType mediaType =
                    MediaType.APPLICATION_OCTET_STREAM;

            if (file.getContentType() != null
                    && !file.getContentType().isBlank()) {

                try {
                    mediaType = MediaType.parseMediaType(
                            file.getContentType()
                    );
                } catch (InvalidMediaTypeException e) {
                    mediaType =
                            MediaType.APPLICATION_OCTET_STREAM;
                }
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''"
                                    + encodedFilename
                    )
                    .body(decryptedBytes);

        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Ошибка скачивания файла",
                    e
            );
        }
    }

    private SupportFileTransferResponse toResponse(
            SupportFileTransfer file
    ) {
        return new SupportFileTransferResponse(
                file.getId(),
                file.getSupportSession().getId(),
                file.getSender().getId(),
                file.getSender().getUsername(),
                file.getRecipient().getId(),
                file.getRecipient().getUsername(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getStatus().name(),
                file.getCreatedAt(),
                file.getDecidedAt()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Файл не выбран"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Размер файла не должен превышать 25 МБ"
            );
        }

        String filename =
                cleanFilename(
                        file.getOriginalFilename()
                );

        if (filename.isBlank()) {
            throw new IllegalArgumentException(
                    "Некорректное имя файла"
            );
        }

        String lowerFilename =
                filename.toLowerCase();

        for (String extension : BLOCKED_EXTENSIONS) {
            if (lowerFilename.endsWith(extension)) {
                throw new IllegalArgumentException(
                        "Файлы этого типа запрещены к передаче"
                );
            }
        }
    }

    private String cleanFilename(String filename) {
        if (filename == null) {
            return "file";
        }

        return filename
                .replace("\\", "_")
                .replace("/", "_")
                .trim();
    }

    private SupportSession getSession(
            String sessionCode
    ) {
        return supportSessionRepository
                .findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Сессия техподдержки не найдена"
                ));
    }

    private SupportFileTransfer getFile(
            Long fileId
    ) {
        return supportFileTransferRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Файл не найден"
                ));
    }

    private User getUser(
            String username
    ) {
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не найден"
                ));
    }

    private void checkSessionActive(
            SupportSession session
    ) {
        if (session.getStatus()
                != SupportSessionStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Файлы доступны только в активной сессии"
            );
        }
    }

    private void checkOperator(
            SupportSession session,
            User user
    ) {
        if (session.getOperator() == null
                || !session.getOperator()
                .getId()
                .equals(user.getId())) {

            throw new IllegalArgumentException(
                    "Только оператор может отправлять файлы"
            );
        }
    }

    private void checkRecipient(
            SupportFileTransfer file,
            User user
    ) {
        if (file.getRecipient() == null
                || !file.getRecipient()
                .getId()
                .equals(user.getId())) {

            throw new IllegalArgumentException(
                    "Только клиент может принять или отклонить файл"
            );
        }
    }

    private void checkSessionParticipant(
            SupportSession session,
            User user
    ) {
        boolean isOperator =
                session.getOperator() != null
                        && session.getOperator()
                        .getId()
                        .equals(user.getId());

        boolean isClient =
                session.getClient() != null
                        && session.getClient()
                        .getId()
                        .equals(user.getId());

        if (!isOperator && !isClient) {
            throw new IllegalArgumentException(
                    "Нет доступа к этой сессии техподдержки"
            );
        }
    }
}
