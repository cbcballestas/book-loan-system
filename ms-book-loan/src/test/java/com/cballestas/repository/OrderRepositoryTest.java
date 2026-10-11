package com.cballestas.repository;

import com.cballestas.entities.OrderEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRepositoryTest {

    @Test
    void repositoryIsAnApplicationScopedPanacheRepositoryForOrders() {
        OrderRepository repository = new OrderRepository();

        assertTrue(OrderRepository.class.isAnnotationPresent(ApplicationScoped.class));
        assertTrue(repository instanceof PanacheRepositoryBase);

        ParameterizedType repositoryType = (ParameterizedType) OrderRepository.class
                .getGenericInterfaces()[0];
        assertEquals(PanacheRepositoryBase.class, repositoryType.getRawType());
        assertEquals(OrderEntity.class, repositoryType.getActualTypeArguments()[0]);
        assertEquals(UUID.class, repositoryType.getActualTypeArguments()[1]);
    }
}
