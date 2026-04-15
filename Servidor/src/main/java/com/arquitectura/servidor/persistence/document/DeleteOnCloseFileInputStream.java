package com.arquitectura.servidor.persistence.document;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class DeleteOnCloseFileInputStream extends FilterInputStream {

    private final Path filePath;

    DeleteOnCloseFileInputStream(Path filePath) throws IOException {
        super(Files.newInputStream(filePath));
        this.filePath = filePath;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            Files.deleteIfExists(filePath);
        }
    }
}

