package com.remote.file.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileCleanupService {

    private final Path storageDir;
    private final Duration maxAge;

    public FileCleanupService(
            @Value("${remote.files.storage-dir:uploads/remote-files}") String storageDir,
            @Value("${remote.files.max-age-hours:24}") long maxAgeHours
    ) throws IOException {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.maxAge = Duration.ofHours(maxAgeHours);

        Files.createDirectories(this.storageDir);
    }

    @Scheduled(fixedDelayString = "${remote.files.cleanup-delay-ms:3600000}")
    public void cleanupOldFiles() {
        try {
            if (!Files.exists(storageDir)) {
                return;
            }

            Instant now = Instant.now();

            try (Stream<Path> files = Files.list(storageDir)) {
                files
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Instant lastModified =
                                        Files.getLastModifiedTime(path).toInstant();

                                if (lastModified.plus(maxAge).isBefore(now)) {
                                    Files.deleteIfExists(path);

                                    log.info(
                                            "Old uploaded file deleted: fileName={}",
                                            path.getFileName()
                                    );
                                }
                            } catch (IOException e) {
                                log.warn(
                                        "Failed to clean up uploaded file: path={}",
                                        path,
                                        e
                                );
                            }
                        });
            }

        } catch (IOException e) {
            log.error(
                    "File cleanup failed: storageDir={}",
                    storageDir,
                    e
            );
        }
    }
}