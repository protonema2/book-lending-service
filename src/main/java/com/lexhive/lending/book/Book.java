package com.lexhive.lending.book;

import com.lexhive.lending.common.error.ResourceConflictException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A catalog title with a number of physical copies. {@code availableCopies} is changed by borrow/return through
 * atomic SQL updates in {@link BookRepository}, never by read-modify-write on this entity.
 */
@Entity
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String author;
    private String isbn;
    private int totalCopies;
    private int availableCopies;
    private Instant createdAt;
    private Instant updatedAt;

    protected Book() {
        // for JPA
    }

    public Book(String title, String author, String isbn, int totalCopies, Instant now) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Updates the catalog data. The number of copies on loan is preserved, so the total cannot drop below it.
     * Callers must hold a row lock (see {@link BookRepository#findByIdForUpdate}) to avoid racing with borrows.
     */
    public void update(String title, String author, String isbn, int totalCopies, Instant now) {
        int onLoan = copiesOnLoan();
        if (totalCopies < onLoan) {
            throw ResourceConflictException.copiesOnLoan(id, onLoan, totalCopies);
        }
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies - onLoan;
        this.updatedAt = now;
    }

    public int copiesOnLoan() {
        return totalCopies - availableCopies;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
