package com.inkwell.category.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "post_tags", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"postId", "tagId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long tagId;
}
