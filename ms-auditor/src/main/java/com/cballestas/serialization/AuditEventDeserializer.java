package com.cballestas.serialization;

import com.cballestas.event.AuditEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class AuditEventDeserializer extends ObjectMapperDeserializer<AuditEvent> {

    public AuditEventDeserializer() {
        super(AuditEvent.class);
    }
}
