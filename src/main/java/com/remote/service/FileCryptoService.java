package com.remote.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class FileCryptoService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int IV_SIZE_BYTES = 12;
    private static final int TAG_SIZE_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoData generateCryptoData() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE_BITS);

        SecretKey secretKey = keyGenerator.generateKey();

        byte[] iv = new byte[IV_SIZE_BYTES];
        secureRandom.nextBytes(iv);

        return new CryptoData(
                Base64.getEncoder().encodeToString(secretKey.getEncoded()),
                Base64.getEncoder().encodeToString(iv)
        );
    }

    public void encrypt(InputStream inputStream,
                        OutputStream outputStream,
                        String base64Key,
                        String base64Iv) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        byte[] ivBytes = Base64.getDecoder().decode(base64Iv);

        SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE_BITS, ivBytes);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        try (CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher)) {
            inputStream.transferTo(cipherOutputStream);
        }
    }

    public record CryptoData(String encryptionKey, String iv) {
    }
}