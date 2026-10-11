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
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class BookService {
    public static final String BOOK_NOT_FOUND_MESSAGE = "Book not found";
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    @WithSession
    public Uni<PaginatedResponseDto<BookDto>> findAll(int pageIndex, int pageSize, String sortBy) {
        // 1. Crear el objeto de paginación (Panache usa índices basados en 0)
        Page page = Page.of(pageIndex, pageSize);

        // 2. Definir el ordenamiento opcional
        Sort sort = Sort.by(sortBy != null ? sortBy : "id");

        // 3. Ejecutar la consulta paginada
        PanacheQuery<BookEntity> query = BookEntity.<BookEntity>findAll(sort).page(page);

        return Uni.combine().all().unis(
                query.list(),
                query.pageCount(),
                query.count()
        ).asTuple().onItem().transform(tuple -> new PaginatedResponseDto<>(
                tuple.getItem1().stream().map(bookMapper::toDto).toList(), // List<BookEntity>
                pageIndex,
                pageSize,
                tuple.getItem2(), // totalPages (int)
                tuple.getItem3()  // totalElements (long)
        ));
    }

    @WithSession
    public Uni<BookDto> findById(UUID id) {
        return bookRepository.findById(id)
                .flatMap(existingBook -> {
                    if (existingBook == null) {
                        return Uni.createFrom().failure(new CustomNotFoundException(BOOK_NOT_FOUND_MESSAGE));
                    } else {
                        return Uni.createFrom().item(bookMapper.toDto(existingBook));
                    }
                });
    }

    @WithTransaction
    public Uni<BookDto> save(BookRequestDto bookRequestDto) {
        return bookRepository.findByName(bookRequestDto.getName().trim())
                .flatMap(existingBook -> {
                    if (existingBook != null) {
                        return Uni.createFrom().failure(new CustomDuplicateException("Book with the same name already exists"));
                    } else {
                        return bookRepository.persist(bookMapper.toEntity(bookRequestDto)).map(bookMapper::toDto);
                    }
                });
    }

    @WithTransaction
    public Uni<BookDto> update(UUID id, BookRequestDto bookRequestDto) {
        return bookRepository.findById(id)
                .flatMap(existingBook -> {
                    if (existingBook == null) {
                        return Uni.createFrom().failure(new CustomNotFoundException(BOOK_NOT_FOUND_MESSAGE));
                    } else {
                        existingBook.setName(bookRequestDto.getName());
                        existingBook.setPrice(bookRequestDto.getPrice());
                        existingBook.setStock(bookRequestDto.getStock());
                        return bookRepository.persist(existingBook).map(bookMapper::toDto);
                    }
                });
    }

    @WithTransaction
    public Uni<BookDto> decreaseStock(UUID id, StockDecreaseRequestDto stockDecreaseRequest) {
        int quantity = stockDecreaseRequest.quantity();

        return bookRepository.decreaseStock(id, quantity)
                .flatMap(updatedRows -> {
                    if (updatedRows > 0) {
                        return bookRepository.findById(id).map(bookMapper::toDto);
                    }

                    return bookRepository.findById(id)
                            .flatMap(book -> {
                                if (book == null) {
                                    return Uni.createFrom().failure(
                                            new CustomNotFoundException(BOOK_NOT_FOUND_MESSAGE));
                                }

                                return Uni.createFrom().failure(
                                        new CustomInsufficientStockException("Insufficient stock"));
                            });
                });
    }

    @WithTransaction
    public Uni<Void> delete(UUID id) {
        return bookRepository.findById(id)
                .flatMap(existingBook -> {
                    if (existingBook == null) {
                        return Uni.createFrom().failure(new CustomNotFoundException(BOOK_NOT_FOUND_MESSAGE));
                    } else {
                        return bookRepository.delete(existingBook);
                    }
                });
    }
}