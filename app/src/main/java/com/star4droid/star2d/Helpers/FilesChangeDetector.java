package com.star4droid.star2d.Helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.Stream;

public class FilesChangeDetector {
    public static boolean detect(Path filesPath, Path jsonDataPath) throws IOException {
        Map<String, String> oldChecksums = readOldChecksums(jsonDataPath);
        Map<String, String> currentChecksums = computeCurrentChecksums(filesPath);
        boolean changed = !currentChecksums.equals(oldChecksums);
        if (changed) {
            saveChecksums(currentChecksums, jsonDataPath);
        }
        return changed;
    }

    private static Map<String, String> computeCurrentChecksums(Path root) throws IOException {
        Map<String, String> checksums = new HashMap<>();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Path must be a directory");
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> !Files.isDirectory(path))
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(path -> {
                     String relativePath = root.relativize(path).toString().replace('\\', '/');
                     try {
                         String checksum = calculateChecksum(path);
                         checksums.put(relativePath, checksum);
                     } catch (IOException e) {
                         throw new UncheckedIOException(e);
                     }
                 });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return checksums;
    }

    private static String calculateChecksum(Path path) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream is = Files.newInputStream(path);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                while (dis.read() != -1) {
                    // Read all bytes to compute digest
                }
            }
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static Map<String, String> readOldChecksums(Path jsonDataPath) throws IOException {
        if (!Files.exists(jsonDataPath)) {
            return new HashMap<>();
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonDataPath.toFile(), new TypeReference<Map<String, String>>() {});
    }

    private static void saveChecksums(Map<String, String> checksums, Path jsonDataPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(jsonDataPath.toFile(), checksums);
    }
}