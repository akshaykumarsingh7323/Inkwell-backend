package com.inkwell.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "author_earnings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorEarning {

    @Id
    private String authorId;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalEarnings;

    @Version
    private Long version;
}
