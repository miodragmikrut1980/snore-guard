package org.gradle.wrapper;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/**
 * Transparent, small Gradle bootstrap compatible with the wrapper entry point.
 * NOT the official Gradle wrapper JAR: the build environment has no outbound
 * binary downloads. Downloads + SHA-256 verifies the OFFICIAL Gradle binary
 * distribution and then launches it. Source intentionally included in ZIP.
 */
public final class GradleWrapperMain {
    private static final String EXPECTED_VERSION = "gradle-8.11.1";
    private static final String EXPECTED_SHA = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6";

    public static void main(String[] args) throws Exception {
        Path project = Paths.get(".").toAbsolutePath().normalize();
        Path propsFile = project.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.isRegularFile(propsFile)) throw new IllegalStateException("Run this from the SnoreGuard project directory");
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(propsFile)) { props.load(is); }
        String source = props.getProperty("distributionUrl", "");
        String sha = props.getProperty("distributionSha256Sum", "");
        if (!source.equals("https://services.gradle.org/distributions/gradle-8.11.1-bin.zip") || !EXPECTED_SHA.equals(sha)) {
            throw new SecurityException("Unexpected Gradle distribution or checksum. See gradle-wrapper.properties");
        }
        String homeEnv = System.getenv("GRADLE_USER_HOME");
        Path gradleHome = homeEnv == null || homeEnv.isEmpty()
            ? Paths.get(System.getProperty("user.home"), ".gradle") : Paths.get(homeEnv);
        Path install = gradleHome.resolve("snoreguard-bootstrap");
        Path distribution = install.resolve(EXPECTED_VERSION);
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path executable = distribution.resolve("bin").resolve(windows ? "gradle.bat" : "gradle");
        Files.createDirectories(install);
        Path lockFile = install.resolve(".download.lock");
        try (FileChannel lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (!Files.isRegularFile(executable)) {
                Path zip = install.resolve("gradle.zip.part");
                Files.deleteIfExists(zip);
                System.err.println("SnoreGuard: downloading Gradle 8.11.1 from its official host (first run only)…");
                URLConnection connection = new URL(source).openConnection();
                connection.setConnectTimeout(25_000);
                connection.setReadTimeout(120_000);
                try (InputStream input = connection.getInputStream()) {
                    Files.copy(input, zip, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!EXPECTED_SHA.equalsIgnoreCase(sha256(zip))) {
                    Files.deleteIfExists(zip);
                    throw new SecurityException("Gradle ZIP SHA-256 mismatch: download rejected");
                }
                try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
                    ZipEntry entry;
                    while ((entry = zin.getNextEntry()) != null) {
                        Path out = install.resolve(entry.getName()).normalize();
                        if (!out.startsWith(install)) throw new SecurityException("ZIP path traversal blocked");
                        if (entry.isDirectory()) Files.createDirectories(out);
                        else {
                            Files.createDirectories(out.getParent());
                            Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                        }
                        zin.closeEntry();
                    }
                } finally { Files.deleteIfExists(zip); }
                if (!Files.isRegularFile(executable)) throw new IOException("Downloaded Gradle distribution did not contain expected executable");
                if (!windows) executable.toFile().setExecutable(true, false);
            }
        }
        List<String> command = new ArrayList<>();
        if (windows) {
            command.add("cmd"); command.add("/c"); command.add(executable.toString());
        } else { command.add(executable.toString()); }
        Collections.addAll(command, args);
        Process child = new ProcessBuilder(command).directory(project.toFile()).inheritIO().start();
        System.exit(child.waitFor());
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = stream.read(buf)) >= 0) if (n > 0) digest.update(buf, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
