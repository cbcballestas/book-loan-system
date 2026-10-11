package com.cballestas.repository;

import com.cballestas.entities.BookEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class BookRepository implements PanacheRepositoryBase<BookEntity, UUID> {

    public Uni<BookEntity> findByName(String name) {
        return find("name", name).firstResult();
    }

    public Uni<Integer> decreaseStock(UUID id, int quantity) {
        return update("stock = stock - ?1 where id = ?2 and stock >= ?1", quantity, id);
    }
}
