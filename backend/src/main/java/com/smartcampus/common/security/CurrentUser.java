package com.smartcampus.common.security;

import java.io.Serializable;
import java.util.Objects;

/**
 * Authenticated principal representation available via SecurityContextHolder.
 * Referenced by controllers and notification endpoints.
 * Non-final to allow Spring RequestScope CGLIB proxying when injected into singletons.
 */
public class CurrentUser implements Serializable {

    private final long id;
    private final String email;
    private final String role;

    public CurrentUser() {
        this(0L, "anonymous", "ANONYMOUS");
    }

    public CurrentUser(long id, String email, String role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String role() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CurrentUser that = (CurrentUser) o;
        return id == that.id && Objects.equals(email, that.email) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email, role);
    }

    @Override
    public String toString() {
        return "CurrentUser{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
