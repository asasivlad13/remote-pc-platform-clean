package com.remote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EducationCryptoService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public EducationCryptoService(@Value("${education.crypto.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("education.crypto.key must be a Base64 encoded 256-bit key");
        }

        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String encryptText(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            byte[] iv = generateIv();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(result);

        } catch (Exception e) {
            throw new IllegalStateException("Text encryption failed", e);
        }
    }

    public String decryptText(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }

        try {
            byte[] input = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] encrypted = new byte[input.length - IV_LENGTH_BYTES];

            System.arraycopy(input, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(input, IV_LENGTH_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plain = cipher.doFinal(encrypted);

            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException("Text decryption failed", e);
        }
    }

    public void encryptStream(InputStream inputStream, OutputStream outputStream) {
        try {
            byte[] iv = generateIv();
            outputStream.write(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            try (CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher)) {
                inputStream.transferTo(cipherOutputStream);
            }

        } catch (Exception e) {
            throw new IllegalStateException("File encryption failed", e);
        }
    }

    public InputStream decryptStream(InputStream encryptedInputStream) {
        try {
            byte[] iv = encryptedInputStream.readNBytes(IV_LENGTH_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new CipherInputStream(encryptedInputStream, cipher);

        } catch (Exception e) {
            throw new IllegalStateException("File decryption failed", e);
        }
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }
}