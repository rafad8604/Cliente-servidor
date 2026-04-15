package com.arquitectura.servidor.business.infrastructure;

import java.io.InputStream;

public record ProtocolResponse(String json, InputStream binaryPayload, long binaryPayloadLength) {

    public static ProtocolResponse jsonOnly(String json) {
        return new ProtocolResponse(json, null, 0L);
    }
}

