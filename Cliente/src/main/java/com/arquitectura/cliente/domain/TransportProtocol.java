package com.arquitectura.cliente.domain;

public enum TransportProtocol {
	TCP,
	UDP;

	public static TransportProtocol fromString(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("El protocolo es obligatorio");
		}
		try {
			return TransportProtocol.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Protocolo invalido. Use TCP o UDP");
		}
	}
}

