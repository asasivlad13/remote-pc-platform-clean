package com.remote.auth.service;

import com.remote.auth.model.DeviceCredential;
import com.remote.auth.model.DeviceCredentialRevokeReason;
import com.remote.auth.repository.DeviceCredentialRepository;
import com.remote.core.model.User;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceCredentialService {

    private static final int MAX_RAW_CREDENTIAL_LENGTH =
            256;

    private final DeviceCredentialRepository credentialRepository;
    private final PcRepository pcRepository;
    private final SecureTokenService secureTokenService;

    public DeviceCredentialService(
            DeviceCredentialRepository credentialRepository,
            PcRepository pcRepository,
            SecureTokenService secureTokenService
    ) {
        this.credentialRepository =
                credentialRepository;

        this.pcRepository =
                pcRepository;

        this.secureTokenService =
                secureTokenService;
    }

    /*
     * Первоначальная выдача credential.
     *
     * Выполняется только для ПК, принадлежащего
     * ожидаемому пользователю.
     *
     * Pc блокируется даже тогда, когда строки
     * device_credentials ещё нет. Благодаря этому
     * два параллельных provisioning-запроса
     * не смогут одновременно создать credential.
     */
    @Transactional
    public IssuedDeviceCredential issueCredential(
            Long pcId,
            Long ownerUserId
    ) {
        Pc pc =
                requireOwnedPcForUpdate(
                        pcId,
                        ownerUserId
                );

        if (credentialRepository
                .findByPcId(
                        pc.getId()
                )
                .isPresent()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Device credential already exists"
            );
        }

        Instant now =
                Instant.now();

        String rawCredential =
                secureTokenService
                        .generateToken();

        DeviceCredential credential =
                new DeviceCredential();

        credential.setPc(
                pc
        );

        credential.setCredentialVersion(
                1
        );

        credential.setCredentialHash(
                secureTokenService
                        .hashToken(
                                rawCredential
                        )
        );

        credential.setCreatedAt(
                now
        );

        credentialRepository.save(
                credential
        );

        return new IssuedDeviceCredential(
                rawCredential,
                credential.getCredentialVersion(),
                now
        );
    }

    /*
     * Проверяет installationId + device credential.
     *
     * Raw credential нигде не сохраняется.
     * Для сравнения вычисляется SHA-256 и сравнивается
     * с текущим hash в constant-time форме.
     *
     * При успешной проверке обновляется last_used_at.
     */
    @Transactional
    public Optional<AuthenticatedDevice> authenticate(
            UUID installationId,
            String rawCredential
    ) {
        if (installationId == null
                || !isValidRawCredential(
                rawCredential
        )) {
            return Optional.empty();
        }

        Pc pc =
                pcRepository
                        .findByInstallationId(
                                installationId
                        )
                        .orElse(null);

        if (pc == null
                || pc.getId() == null) {

            return Optional.empty();
        }

        DeviceCredential credential =
                credentialRepository
                        .findByPcIdForUpdate(
                                pc.getId()
                        )
                        .orElse(null);

        if (credential == null
                || credential.getRevokedAt() != null) {

            return Optional.empty();
        }

        if (!credentialMatches(
                credential,
                rawCredential.strip()
        )) {
            return Optional.empty();
        }

        User owner =
                pc.getUser();

        /*
         * Device credential не должен обходить
         * состояние пользовательского аккаунта.
         */
        if (owner == null
                || owner.getId() == null
                || owner.getEmail() == null
                || !owner.isEnabled()
                || !owner.isAccountNonLocked()
                || !owner.isAccountNonExpired()
                || !owner.isCredentialsNonExpired()) {

            return Optional.empty();
        }

        credential.setLastUsedAt(
                Instant.now()
        );

        credentialRepository.save(
                credential
        );

        return Optional.of(
                new AuthenticatedDevice(
                        pc.getId(),
                        pc.getInstallationId(),
                        owner.getId(),
                        owner.getEmail()
                )
        );
    }

    /*
     * Rotation полностью заменяет текущий secret.
     *
     * Старый credential сразу перестаёт подходить,
     * потому что в БД остаётся только hash нового.
     */
    @Transactional
    public IssuedDeviceCredential rotateCredential(
            Long pcId,
            Long ownerUserId
    ) {
        Pc pc =
                requireOwnedPcForUpdate(
                        pcId,
                        ownerUserId
                );

        DeviceCredential credential =
                credentialRepository
                        .findByPcIdForUpdate(
                                pc.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Device credential not found"
                                        )
                        );

        if (credential.getRevokedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Device credential is revoked"
            );
        }

        int nextVersion;

        try {
            nextVersion =
                    Math.addExact(
                            credential.getCredentialVersion(),
                            1
                    );

        } catch (ArithmeticException e) {
            throw new IllegalStateException(
                    "Device credential version overflow",
                    e
            );
        }

        Instant now =
                Instant.now();

        String rawCredential =
                secureTokenService
                        .generateToken();

        credential.setCredentialHash(
                secureTokenService
                        .hashToken(
                                rawCredential
                        )
        );

        credential.setCredentialVersion(
                nextVersion
        );

        credential.setRotatedAt(
                now
        );

        /*
         * Новый credential ещё ни разу
         * не использовался для device-auth.
         */
        credential.setLastUsedAt(
                null
        );

        credentialRepository.save(
                credential
        );

        return new IssuedDeviceCredential(
                rawCredential,
                nextVersion,
                now
        );
    }

    /*
     * Отзыв credential идемпотентен.
     *
     * Если credential уже был отозван,
     * первая причина и время отзыва сохраняются.
     */
    @Transactional
    public void revokeCredential(
            Long pcId,
            Long ownerUserId,
            DeviceCredentialRevokeReason reason
    ) {
        if (reason == null) {
            throw new IllegalArgumentException(
                    "Device credential revoke reason is required"
            );
        }

        Pc pc =
                requireOwnedPcForUpdate(
                        pcId,
                        ownerUserId
                );

        DeviceCredential credential =
                credentialRepository
                        .findByPcIdForUpdate(
                                pc.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Device credential not found"
                                        )
                        );

        if (credential.getRevokedAt() != null) {
            return;
        }

        credential.setRevokedAt(
                Instant.now()
        );

        credential.setRevocationReason(
                reason
        );

        credentialRepository.save(
                credential
        );
    }

    private Pc requireOwnedPcForUpdate(
            Long pcId,
            Long ownerUserId
    ) {
        if (pcId == null
                || ownerUserId == null) {

            throw new IllegalArgumentException(
                    "PC id and owner user id are required"
            );
        }

        Pc pc =
                pcRepository
                        .findByIdForUpdate(
                                pcId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "PC not found"
                                        )
                        );

        User owner =
                pc.getUser();

        if (owner == null
                || owner.getId() == null
                || !ownerUserId.equals(
                owner.getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "PC belongs to another user"
            );
        }

        return pc;
    }

    private boolean isValidRawCredential(
            String rawCredential
    ) {
        if (rawCredential == null
                || rawCredential.isBlank()) {

            return false;
        }

        return rawCredential.strip()
                .length()
                <= MAX_RAW_CREDENTIAL_LENGTH;
    }

    private boolean credentialMatches(
            DeviceCredential credential,
            String rawCredential
    ) {
        String expectedHash =
                credential.getCredentialHash();

        if (expectedHash == null
                || expectedHash.isBlank()) {

            return false;
        }

        String actualHash =
                secureTokenService
                        .hashToken(
                                rawCredential
                        );

        return MessageDigest.isEqual(
                expectedHash.getBytes(
                        StandardCharsets.US_ASCII
                ),
                actualHash.getBytes(
                        StandardCharsets.US_ASCII
                )
        );
    }

    /*
     * Raw credential возвращается только при
     * первоначальной выдаче или rotation.
     *
     * После завершения запроса сервер уже не способен
     * восстановить его из credential_hash.
     */
    public record IssuedDeviceCredential(
            String credential,
            int version,
            Instant issuedAt
    ) {
    }

    /*
     * Результат успешной device-auth не содержит
     * raw credential или его hash.
     */
    public record AuthenticatedDevice(
            Long pcId,
            UUID installationId,
            Long userId,
            String email
    ) {
    }
}