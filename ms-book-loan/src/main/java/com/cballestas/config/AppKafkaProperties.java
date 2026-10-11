package com.cballestas.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "app.kafka-config")
public interface AppKafkaProperties {

    @WithDefault("audit-topic")
    String auditTopic();

    @WithDefault("audit-group")
    String auditGroupId();

    @WithDefault("earliest")
    String autoOffsetReset();
}