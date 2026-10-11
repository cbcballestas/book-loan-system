package com.cballestas.producer;

import com.cballestas.config.AppKafkaProperties;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

@ApplicationScoped
public class AuditEventProducer {

    @Inject
    @Channel("audit-topic")
    MutinyEmitter<AuditEvent> emitter;

    @Inject
    AppKafkaProperties appKafkaProperties ;

    public Uni<Void> sendAuditLogEvent(AuditEvent auditEvent) {
        Log.infof("--> [Kafka] Enviando evento al tópico: %s", appKafkaProperties.auditTopic());
        return emitter.send(auditEvent);
    }
}
