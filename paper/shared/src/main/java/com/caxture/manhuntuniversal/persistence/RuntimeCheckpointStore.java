package com.caxture.manhuntuniversal.persistence;

import com.caxture.manhuntuniversal.config.AtomicFileWriter;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;

/** Owns only the checkpoint file. A failed read never mutates or removes the file. */
public final class RuntimeCheckpointStore {
    @FunctionalInterface public interface Writer { void replace(Path file,String value) throws IOException; }
    private static final int MAX_BYTES=8*1024*1024;
    private final Path file;
    private final Writer writer;
    public RuntimeCheckpointStore(Path file,AtomicFileWriter writer) { this(file,writer::replace); }
    public RuntimeCheckpointStore(Path file,Writer writer) { this.file=file;this.writer=writer; }
    public Optional<RuntimeCheckpoint> load() throws IOException {
        if(!Files.exists(file))return Optional.empty();
        try {
            if(Files.size(file)>MAX_BYTES)throw new IOException("Checkpoint exceeds 8 MiB");
            YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString(Files.readString(file,StandardCharsets.UTF_8));
            return Optional.of(RuntimeCheckpointCodec.read(yaml));
        } catch(IOException error){throw error;}
        catch(Exception error){throw new IOException("Invalid or unsupported runtime checkpoint: "+file,error);}
    }
    public void save(RuntimeCheckpoint checkpoint) throws IOException {
        String serialized=RuntimeCheckpointCodec.write(checkpoint).saveToString();
        if(serialized.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Checkpoint exceeds 8 MiB");
        writer.replace(file,serialized);
    }
}
