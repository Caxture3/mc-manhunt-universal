package com.caxture.randomanhunt.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class AtomicFileWriter {
    @FunctionalInterface
    interface MoveOperation {
        void move(Path source, Path destination) throws IOException;
    }

    private final MoveOperation moveOperation;

    public AtomicFileWriter() {
        this(AtomicFileWriter::atomicMove);
    }

    AtomicFileWriter(MoveOperation moveOperation) {
        this.moveOperation = Objects.requireNonNull(moveOperation, "moveOperation");
    }

    public void replace(Path target, String content) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path parent = Objects.requireNonNull(absolute.getParent(), "target parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            moveOperation.move(temporary, absolute);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void atomicMove(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
