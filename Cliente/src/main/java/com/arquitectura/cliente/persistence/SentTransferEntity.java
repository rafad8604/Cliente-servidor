package com.arquitectura.cliente.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad para auditoría de transferencias enviadas al servidor.
 * Almacena metadatos de mensajes y archivos enviados.
 */
@Entity
@Table(name = "sent_transfers")
public class SentTransferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Instant sentAt;

    @Column(nullable = false)
    private String serverHost;

    @Column(nullable = false)
    private int serverPort;

    @Column(nullable = false)
    private String protocol; // TCP o UDP

    @Column
    private String recipientId;

    @Column(nullable = false)
    private String type; // MESSAGE o FILE

    @Column
    private String fileName;

    @Column
    private String messagePreview;

    @Column
    private Long sizeBytes;

    @Column(nullable = false)
    private String status; // SENT, PENDING, FAILED, etc.

    @Column
    private String serverDocumentId;

    @Column
    private String errorMessage;

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMessagePreview() {
        return messagePreview;
    }

    public void setMessagePreview(String messagePreview) {
        this.messagePreview = messagePreview;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getServerDocumentId() {
        return serverDocumentId;
    }

    public void setServerDocumentId(String serverDocumentId) {
        this.serverDocumentId = serverDocumentId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

