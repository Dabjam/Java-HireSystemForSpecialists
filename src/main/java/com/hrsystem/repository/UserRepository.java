package com.hrsystem.repository;

import com.hrsystem.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // ---------------------------------------------------------------
    // ORM-методы (Spring Data — генерирует SQL автоматически)
    // ---------------------------------------------------------------

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<UserEntity> findAllByOrderByIdAsc();

    // ---------------------------------------------------------------
    // Нативные SQL-запросы (прямой SQL к PostgreSQL)
    // ---------------------------------------------------------------

    /**
     * Нативный SQL: количество пользователей по каждой роли.
     * Используется для аналитики в дашборде администратора.
     *
     * @return массив объектов [role, count]
     */
    @Query(value = """
            SELECT
                role,
                COUNT(*) AS cnt
            FROM users
            GROUP BY role
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> countUsersByRole();

    /**
     * Нативный SQL: список активных пользователей с датой регистрации.
     * Позволяет увидеть новых участников системы.
     *
     * @param limit максимальное количество записей
     * @return массив объектов [id, email, role, created_at]
     */
    @Query(value = """
            SELECT
                id,
                email,
                role,
                created_at
            FROM users
            WHERE is_active = true
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findRecentActiveUsers(@Param("limit") int limit);
}
