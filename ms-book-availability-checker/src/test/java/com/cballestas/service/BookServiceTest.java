package com.cballestas.service;

import com.cballestas.dto.BookDto;
import com.cballestas.dto.BookRequestDto;
import com.cballestas.dto.PaginatedResponseDto;
import com.cballestas.dto.StockDecreaseRequestDto;
import com.cballestas.entities.BookEntity;
import com.cballestas.exception.CustomDuplicateException;
import com.cballestas.exception.CustomInsufficientStockException;
import com.cballestas.exception.CustomNotFoundException;
import com.cballestas.mapper.BookMapper;
import com.cballestas.repository.BookRepository;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    BookRepository bookRepository;

    @Mock
    BookMapper bookMapper;

    @Test
    void findAllBuildsPaginatedDtoUsingDefaultSort() {
        BookEntity book = book("Dune", 8);
        BookDto dto = dto(book);
        @SuppressWarnings("unchecked")
        PanacheQuery<BookEntity> query = mock(PanacheQuery.class);
        when(query.page(any(Page.class))).thenReturn(query);
        when(query.list()).thenReturn(Uni.createFrom().item(List.of(book)));
        when(query.pageCount()).thenReturn(Uni.createFrom().item(1));
        when(query.count()).thenReturn(Uni.createFrom().item(1L));
        when(bookMapper.toDto(book)).thenReturn(dto);

        try (MockedStatic<PanacheEntityBase> ignored = mockStatic(PanacheEntityBase.class, invocation -> query)) {

            PaginatedResponseDto<BookDto> result = service().findAll(0, 10, null)
                    .await().indefinitely();

            assertEquals(List.of(dto), result.content);
            assertEquals(0, result.pageIndex);
            assertEquals(10, result.pageSize);
            assertEquals(1, result.totalPages);
            assertEquals(1L, result.totalElements);
        }

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(query).page(pageCaptor.capture());
        assertEquals(0, pageCaptor.getValue().index);
        assertEquals(10, pageCaptor.getValue().size);
    }

    @Test
    void findByIdReturnsMappedBookWhenPresent() {
        UUID id = UUID.randomUUID();
        BookEntity book = book("Dune", 8);
        BookDto dto = dto(book);
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().item(book));
        when(bookMapper.toDto(book)).thenReturn(dto);

        assertSame(dto, service().findById(id).await().indefinitely());

        verify(bookRepository).findById(id);
        verify(bookMapper).toDto(book);
    }

    @Test
    void findByIdFailsWhenBookIsMissing() {
        UUID id = UUID.randomUUID();
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().nullItem());

        CustomNotFoundException exception = assertThrows(
                CustomNotFoundException.class,
                () -> service().findById(id).await().indefinitely());

        assertEquals(BookService.BOOK_NOT_FOUND_MESSAGE, exception.getMessage());
    }

    @Test
    void savePersistsMappedBookWhenNameIsAvailable() {
        BookRequestDto request = request("  Dune  ", 8);
        BookEntity book = book("Dune", 8);
        BookDto dto = dto(book);
        when(bookRepository.findByName("Dune")).thenReturn(Uni.createFrom().nullItem());
        when(bookMapper.toEntity(request)).thenReturn(book);
        when(bookRepository.persist(book)).thenReturn(Uni.createFrom().item(book));
        when(bookMapper.toDto(book)).thenReturn(dto);

        assertSame(dto, service().save(request).await().indefinitely());

        verify(bookRepository).findByName("Dune");
        verify(bookMapper).toEntity(request);
        verify(bookRepository).persist(book);
        verify(bookMapper).toDto(book);
    }

    @Test
    void saveFailsWhenNameAlreadyExists() {
        BookRequestDto request = request("Dune", 8);
        when(bookRepository.findByName("Dune")).thenReturn(Uni.createFrom().item(book("Dune", 8)));

        CustomDuplicateException exception = assertThrows(
                CustomDuplicateException.class,
                () -> service().save(request).await().indefinitely());

        assertEquals("Book with the same name already exists", exception.getMessage());
        verify(bookRepository, never()).persist(any(BookEntity.class));
        verify(bookMapper, never()).toEntity(any(BookRequestDto.class));
    }

    @Test
    void updatePersistsChangedBookWhenPresent() {
        UUID id = UUID.randomUUID();
        BookEntity existing = book("Old title", 2);
        BookRequestDto request = request("New title", 5);
        BookDto dto = dto(existing);
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().item(existing));
        when(bookRepository.persist(existing)).thenReturn(Uni.createFrom().item(existing));
        when(bookMapper.toDto(existing)).thenReturn(dto);

        assertSame(dto, service().update(id, request).await().indefinitely());

        assertEquals("New title", existing.getName());
        assertEquals(new BigDecimal("19.99"), existing.getPrice());
        assertEquals(5, existing.getStock());
        verify(bookRepository).persist(existing);
    }

    @Test
    void updateFailsWhenBookIsMissing() {
        UUID id = UUID.randomUUID();
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().nullItem());

        CustomNotFoundException exception = assertThrows(
                CustomNotFoundException.class,
                () -> service().update(id, request("Dune", 8)).await().indefinitely());

        assertEquals(BookService.BOOK_NOT_FOUND_MESSAGE, exception.getMessage());
        verify(bookRepository, never()).persist(any(BookEntity.class));
    }

    @Test
    void decreaseStockReturnsUpdatedBookWhenRowsWereUpdated() {
        UUID id = UUID.randomUUID();
        StockDecreaseRequestDto request = new StockDecreaseRequestDto(3);
        BookEntity book = book("Dune", 5);
        BookDto dto = dto(book);
        when(bookRepository.decreaseStock(id, 3)).thenReturn(Uni.createFrom().item(1));
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().item(book));
        when(bookMapper.toDto(book)).thenReturn(dto);

        assertSame(dto, service().decreaseStock(id, request).await().indefinitely());

        verify(bookRepository).decreaseStock(id, 3);
        verify(bookRepository).findById(id);
    }

    @Test
    void decreaseStockFailsWhenBookIsMissing() {
        UUID id = UUID.randomUUID();
        when(bookRepository.decreaseStock(id, 3)).thenReturn(Uni.createFrom().item(0));
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().nullItem());

        CustomNotFoundException exception = assertThrows(
                CustomNotFoundException.class,
                () -> service().decreaseStock(id, new StockDecreaseRequestDto(3)).await().indefinitely());

        assertEquals(BookService.BOOK_NOT_FOUND_MESSAGE, exception.getMessage());
    }

    @Test
    void decreaseStockFailsWhenStockIsInsufficient() {
        UUID id = UUID.randomUUID();
        when(bookRepository.decreaseStock(id, 3)).thenReturn(Uni.createFrom().item(0));
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().item(book("Dune", 2)));

        CustomInsufficientStockException exception = assertThrows(
                CustomInsufficientStockException.class,
                () -> service().decreaseStock(id, new StockDecreaseRequestDto(3)).await().indefinitely());

        assertEquals("Insufficient stock", exception.getMessage());
    }

    @Test
    void deleteRemovesBookWhenPresent() {
        UUID id = UUID.randomUUID();
        BookEntity book = book("Dune", 8);
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().item(book));
        when(bookRepository.delete(book)).thenReturn(Uni.createFrom().voidItem());

        assertNull(service().delete(id).await().indefinitely());

        verify(bookRepository).delete(book);
    }

    @Test
    void deleteFailsWhenBookIsMissing() {
        UUID id = UUID.randomUUID();
        when(bookRepository.findById(id)).thenReturn(Uni.createFrom().nullItem());

        CustomNotFoundException exception = assertThrows(
                CustomNotFoundException.class,
                () -> service().delete(id).await().indefinitely());

        assertEquals(BookService.BOOK_NOT_FOUND_MESSAGE, exception.getMessage());
        verify(bookRepository, never()).delete(any(BookEntity.class));
    }

    private BookService service() {
        return new BookService(bookRepository, bookMapper);
    }

    private static BookEntity book(String name, int stock) {
        return new BookEntity(UUID.randomUUID(), name, new BigDecimal("19.99"), stock);
    }

    private static BookDto dto(BookEntity book) {
        return new BookDto(book.getId(), book.getName(), book.getPrice(), book.getStock());
    }

    private static BookRequestDto request(String name, int stock) {
        return new BookRequestDto(name, new BigDecimal("19.99"), stock);
    }
}
