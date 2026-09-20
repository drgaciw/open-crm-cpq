package com.opencrm.ai.selection.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.selection.SelectionTypes.SelectionPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Predicate;

/** Single-writer durable policy journal; activation and rollback always re-check eligibility. */
public final class FilePolicyRepository {
    private final Path root;
    private final ObjectMapper mapper;

    public FilePolicyRepository(Path root, ObjectMapper mapper) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.mapper = mapper;
        Files.createDirectories(this.root);
    }

    public synchronized void saveProposal(SelectionPolicy policy) throws IOException {
        Path file = path("policy", policy.version());
        if (Files.exists(file)) {
            if (!read(policy.version()).equals(policy)) throw new IllegalArgumentException("Policy version is immutable");
            return;
        }
        atomicWrite(file, mapper.writeValueAsBytes(policy));
    }

    public synchronized void activate(String version, Predicate<SelectionPolicy> eligible) throws IOException {
        SelectionPolicy policy = read(version);
        if (revoked(version) || !eligible.test(policy)) throw new IllegalStateException("Policy is not eligible");
        atomicWrite(path("active", policy.profileId()), version.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized Optional<SelectionPolicy> active(String profileId) throws IOException {
        Path pointer = path("active", profileId);
        if (!Files.exists(pointer)) return Optional.empty();
        String version = Files.readString(pointer);
        if (revoked(version)) return Optional.empty();
        SelectionPolicy policy = read(version);
        if (!policy.profileId().equals(profileId)) throw new IOException("Policy profile mismatch");
        return Optional.of(policy);
    }

    public synchronized void rollback(String profileId, Predicate<SelectionPolicy> eligible) throws IOException {
        var current = active(profileId).orElseThrow(() -> new IllegalStateException("No active policy"));
        if (current.rollbackVersion() == null || current.rollbackVersion().isBlank())
            throw new IllegalStateException("No rollback policy");
        var previous = read(current.rollbackVersion());
        if (!previous.profileId().equals(profileId)) throw new IllegalStateException("Rollback profile mismatch");
        activate(previous.version(), eligible);
    }

    public synchronized void revoke(String version) throws IOException {
        read(version);
        atomicWrite(path("revoked", version), new byte[]{1});
    }

    public boolean revoked(String version) { return Files.exists(path("revoked", version)); }

    public SelectionPolicy read(String version) throws IOException {
        SelectionPolicy policy = mapper.readValue(path("policy", version).toFile(), SelectionPolicy.class);
        if (!policy.version().equals(version)) throw new IOException("Policy version mismatch");
        return policy;
    }

    private Path path(String kind, String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing policy identifier");
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(id.getBytes(StandardCharsets.UTF_8)));
            return root.resolve(kind + "-" + hash + ".json");
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private void atomicWrite(Path destination, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(root, ".policy-", ".tmp");
        try {
            try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
