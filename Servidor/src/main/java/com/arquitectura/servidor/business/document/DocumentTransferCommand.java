package com.arquitectura.servidor.business.document;

public record DocumentTransferCommand(String senderId,
                                      String recipientId,
                                      String senderIp,
                                      String type,
                                      String fileName,
                                      String message) {
}

