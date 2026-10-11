package com.cballestas.consumer;

import com.cballestas.entities.AuditLogEntity;
import com.cballestas.event.AuditEvent;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class AuditLogConsumer {

    @Incoming("audit-topic")
    public Uni<Void> consumeAndSave(AuditEvent event) {
        Log.infof("--> [Kafka] Evento recibido: %s", event);

        AuditLogEntity audit = new AuditLogEntity(event);

        // Guardar reactivamente en MongoDB
        return audit.persist()
                .replaceWithVoid()
                .invoke(() -> Log.infof("--> [MongoDB] Evento guardado: %s", audit.id));
    }
}
