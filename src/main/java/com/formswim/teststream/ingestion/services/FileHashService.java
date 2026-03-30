package com.formswim.teststream.ingestion.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class FileHashService {

    public String sha256(MultipartFile file) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(file.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read uploaded file for hashing", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
