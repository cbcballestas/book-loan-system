package com.cballestas.repository;

import com.cballestas.entities.OrderEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<OrderEntity, UUID> {
}
