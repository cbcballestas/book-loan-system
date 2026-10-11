package com.cballestas.entities;

import com.cballestas.event.AuditEvent;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@MongoEntity(collection = "audit_logs")
public class AuditLogEntity extends ReactivePanacheMongoEntity {
    public String action;       // CREATE, UPDATE, DELETE
    public String resource;     // Nombre de la colección afectada (ej: "products")
    public String request;
    public String response;
    public LocalDateTime timestamp;

    public AuditLogEntity() {
    }

    public AuditLogEntity(AuditEvent event) {
        this.action = event.action();
        this.resource = event.resource();
        this.request = event.request();
        this.response = event.response();
        this.timestamp = LocalDateTime.now(ZoneId.systemDefault());
    }
}
