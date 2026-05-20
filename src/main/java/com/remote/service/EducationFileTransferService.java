package com.remote.service;

import com.remote.model.*;
import com.remote.repository.EducationFileTransferRepository;
import com.remote.repository.EducationSessionParticipantRepository;
import com.remote.repository.EducationSessionRepository;
import com.remote.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EducationFileTransferService {

    private static final Path STORAGE_DIR = Path.of("uploads", "education");
    private static final long MAX_FILE_SIZE = 100L * 1024L * 1024L;

    private final EducationFileTransferRepository fileRepository;
    private final EducationSessionRepository sessionRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final EducationSessionEventService eventService;

    public EducationFileTransferService(EducationFileTransferRepository fileRepository,
                                        EducationSessionRepository sessionRepository,
                                        EducationSessionParticipantRepository participantRepository,
                                        UserRepository userRepository,
                                        EducationSessionEventService eventService) {
        this.fileRepository = fileRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.eventService = eventService;
    }

    @Transactional
    public EducationFileTransfer upload(String username,
                                        String sessionCode,
                                        Long recipientId,
                                        MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Файл слишком большой. Максимум 100 МБ");
        }

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        if (session.getStatus() != EducationSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Учебная сессия не активна");
        }

        User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        boolean senderIsTeacher = session.getTeacher().getId().equals(sender.getId());
        boolean senderIsApprovedStudent = participantRepository
                .findByEducationSessionAndStudent(session, sender)
                .map(p -> p.getStatus() == EducationParticipantStatus.APPROVED)
                .orElse(false);

        if (!senderIsTeacher && !senderIsApprovedStudent) {
            throw new IllegalArgumentException("Вы не являетесь участником этой сессии");
        }

        User recipient = null;

        if (recipientId != null) {
            recipient = userRepository.findById(recipientId)
                    .orElseThrow(() -> new IllegalArgumentException("Получатель не найден"));
        }

        if (!senderIsTeacher) {
            recipient = session.getTeacher();
        }

        try {
            Files.createDirectories(STORAGE_DIR);

            String originalFilename = safeFilename(file.getOriginalFilename());
            String storedFilename = UUID.randomUUID() + "_" + originalFilename;

            Path target = STORAGE_DIR.resolve(storedFilename);
            Files.copy(file.getInputStream(), target);

            EducationFileTransfer transfer = new EducationFileTransfer();
            transfer.setEducationSession(session);
            transfer.setSender(sender);
            transfer.setRecipient(recipient);
            transfer.setOriginalFilename(originalFilename);
            transfer.setStoredFilename(storedFilename);
            transfer.setContentType(file.getContentType());
            transfer.setSizeBytes(file.getSize());

            EducationFileTransfer saved = fileRepository.save(transfer);

            String recipientText = recipient == null
                    ? "всем участникам"
                    : "пользователю " + recipient.getUsername();

            eventService.log(
                    session,
                    sender,
                    EducationSessionEventType.FILE_SENT,
                    "Пользователь " + sender.getUsername() + " отправил файл \"" + originalFilename + "\" " + recipientText
            );

            return saved;

        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось сохранить файл: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<EducationFileTransfer> getVisibleFiles(String username, String sessionCode) {
        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        boolean isTeacher = session.getTeacher().getId().equals(user.getId());

        List<EducationFileTransfer> files = fileRepository.findByEducationSessionOrderByCreatedAtDesc(session);

        if (isTeacher) {
            return files;
        }

        return files.stream()
                .filter(file -> file.getStatus() == EducationFileTransferStatus.AVAILABLE)
                .filter(file ->
                        file.getRecipient() == null
                                || file.getRecipient().getId().equals(user.getId())
                                || file.getSender().getId().equals(user.getId())
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public EducationFileTransfer getFileForDownload(String username, Long fileId) {
        EducationFileTransfer file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = file.getEducationSession();

        boolean isTeacher = session.getTeacher().getId().equals(user.getId());
        boolean isSender = file.getSender().getId().equals(user.getId());
        boolean isRecipient = file.getRecipient() != null && file.getRecipient().getId().equals(user.getId());
        boolean isForAll = file.getRecipient() == null;

        if (!isTeacher && !isSender && !isRecipient && !isForAll) {
            throw new IllegalArgumentException("Нет доступа к файлу");
        }

        return file;
    }

    public Resource loadResource(EducationFileTransfer file) {
        try {
            Path path = STORAGE_DIR.resolve(file.getStoredFilename()).normalize();
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Файл недоступен");
            }

            return resource;
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось прочитать файл: " + e.getMessage());
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }

        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}