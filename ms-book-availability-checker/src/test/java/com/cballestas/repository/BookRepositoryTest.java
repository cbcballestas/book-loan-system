package com.cballestas.repository;

import com.cballestas.entities.BookEntity;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookRepositoryTest {

    @Test
    void findByNameQueriesNameAndReturnsFirstResult() {
        RecordingBookRepository repository = new RecordingBookRepository();
        @SuppressWarnings("unchecked")
        PanacheQuery<BookEntity> query = mock(PanacheQuery.class);
        Uni<BookEntity> expected = Uni.createFrom().nullItem();
        repository.findResult = query;
        when(query.firstResult()).thenReturn(expected);

        assertSame(expected, repository.findByName("Dune"));
        assertEquals("name", repository.findQuery);
        assertArrayEquals(new Object[]{"Dune"}, repository.findParameters);
    }

    @Test
    void decreaseStockUsesConditionalAtomicUpdate() {
        RecordingBookRepository repository = new RecordingBookRepository();
        UUID id = UUID.randomUUID();
        Uni<Integer> expected = Uni.createFrom().item(1);
        repository.updateResult = expected;

        assertSame(expected, repository.decreaseStock(id, 3));
        assertEquals("stock = stock - ?1 where id = ?2 and stock >= ?1", repository.updateQuery);
        assertArrayEquals(new Object[]{3, id}, repository.updateParameters);
    }

    private static final class RecordingBookRepository extends BookRepository {
        private PanacheQuery<BookEntity> findResult;
        private String findQuery;
        private Object[] findParameters;
        private Uni<Integer> updateResult;
        private String updateQuery;
        private Object[] updateParameters;

        @Override
        public PanacheQuery<BookEntity> find(String query, Object... params) {
            findQuery = query;
            findParameters = params;
            return findResult;
        }

        @Override
        public Uni<Integer> update(String query, Object... params) {
            updateQuery = query;
            updateParameters = params;
            return updateResult;
        }
    }
}
