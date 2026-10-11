package com.cballestas.entities;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class OrderEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<OrderItemEntity> items = new ArrayList<>();


    // Método de conveniencia para asociar el Detalle con el Maestro
    public void addItem(OrderItemEntity item) {
        items.add(item);
        item.order = this; // Asigna la FK del Maestro en el Detalle
    }

    public void calculateTotalAmount() {
        this.totalAmount = items.stream()
                .map(item -> item.getSubtotal() != null ? BigDecimal.valueOf(item.getSubtotal()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
