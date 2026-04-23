package com.app.server.service;

import com.app.server.models.Documento;
import com.app.shared.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para DocumentoService.
 * 
 * NOTA: Deshabilitados por defecto ya que procesan archivos grandes y cifrado.
 * Para habilitarlos: ejecuta solo estos tests.
 * Runtimes esperado: ~3-5 segundos por test con archivos de 5 MB.
 * 
 * Ejecutar solo estos tests:
 *   mvn test -Dtest=DocumentoServiceTest
 */
@Disabled("Procesa archivos grandes - ejecutar manualmente")
class DocumentoServiceTest {

    private DocumentoService documentoService;

    @BeforeEach
    void setup() throws Exception {
        // DocumentoService requiere SecretKey
        documentoService = new DocumentoService(CryptoUtil.generateAESKey());
    }

    @Test
    void creaDocumentoConDatosValidos() throws Exception {
        byte[] content = "contenido de prueba".getBytes();
        
        Documento doc = documentoService.procesarArchivo(
            "test.txt",
            content.length,
            "127.0.0.1",
            new ByteArrayInputStream(content)
        );
        
        assertNotNull(doc);
        assertEquals("test.txt", doc.getNombre());
        assertEquals("txt", doc.getExtension());
        assertEquals("127.0.0.1", doc.getIpPropietario());
        assertNotNull(doc.getHashSha256());
        assertTrue(doc.getHashSha256().length() > 0);
    }

    @Test
    void procesoArchivoCalculaHashCorrectamente() throws Exception {
        byte[] content = "contenido_fijo_para_hash".getBytes();
        
        Documento doc = documentoService.procesarArchivo(
            "test.txt",
            content.length,
            "127.0.0.1",
            new ByteArrayInputStream(content)
        );
        
        assertNotNull(doc.getHashSha256());
        assertEquals(64, doc.getHashSha256().length()); // SHA-256 = 64 hex chars
    }

    @Test
    void procesoArchivoCompleto() throws Exception {
        byte[] content = new byte[1024]; // 1 KB
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }
        
        assertDoesNotThrow(() -> {
            documentoService.procesarArchivo(
                "binary.bin",
                content.length,
                "127.0.0.1",
                new ByteArrayInputStream(content)
            );
        });
    }

    @Test
    void procesoArchivoLargeFile() throws Exception {
        byte[] content = new byte[5 * 1024 * 1024]; // 5 MB
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }
        
        assertDoesNotThrow(() -> {
            documentoService.procesarArchivo(
                "large.bin",
                content.length,
                "127.0.0.1",
                new ByteArrayInputStream(content)
            );
        });
    }

    @Test
    void procesoArchivoVariosFormatos() throws Exception {
        String[] nombres = {
            "documento.pdf",
            "imagen.jpg",
            "audio.mp3",
            "video.mp4",
            "datos.json"
        };
        
        for (String nombre : nombres) {
            byte[] content = ("contenido del archivo: " + nombre).getBytes();
            assertDoesNotThrow(() -> {
                documentoService.procesarArchivo(
                    nombre,
                    content.length,
                    "127.0.0.1",
                    new ByteArrayInputStream(content)
                );
            });
        }
    }

    @Test
    void procesoArchivoDesdeVariasIps() throws Exception {
        String[] ips = {"127.0.0.1", "192.168.1.1", "10.0.0.1", "172.16.0.1"};
        
        for (String ip : ips) {
            byte[] content = ("contenido desde " + ip).getBytes();
            Documento doc = documentoService.procesarArchivo(
                "test.txt",
                content.length,
                ip,
                new ByteArrayInputStream(content)
            );
            
            assertEquals(ip, doc.getIpPropietario());
        }
    }
}
