package com.hrsystem.repository;

import com.hrsystem.domain.entity.VacancyEntity;
import com.hrsystem.domain.enums.VacancySource;
import com.hrsystem.domain.enums.VacancyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VacancyRepository extends JpaRepository<VacancyEntity, Long> {

    // ---------------------------------------------------------------
    // JPQL-запросы (ORM-уровень, работают через Hibernate)
    // ---------------------------------------------------------------

    Page<VacancyEntity> findByStatus(VacancyStatus status, Pageable pageable);

    @Query("SELECT v FROM VacancyEntity v WHERE v.status = :status " +
           "AND (:source IS NULL OR v.sourceType = :source) " +
           "AND (:minSalary IS NULL OR (v.salaryMax >= :minSalary OR (v.salaryMin IS NOT NULL AND v.salaryMin >= :minSalary))) " +
           "AND (CAST(:keyword AS string) IS NULL OR (LOWER(v.title) LIKE :keyword " +
           "     OR LOWER(v.companyName) LIKE :keyword " +
           "     OR LOWER(COALESCE(v.requirementsStack, '')) LIKE :keyword " +
           "     OR LOWER(v.description) LIKE :keyword))")
    Page<VacancyEntity> findWithFilters(
            @Param("status") VacancyStatus status,
            @Param("source") VacancySource source,
            @Param("minSalary") Integer minSalary,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    boolean existsByContentHash(String contentHash);

    boolean existsBySourceUrl(String sourceUrl);

    List<VacancyEntity> findByEmployerIdOrderByPublishedAtDesc(Long employerId);

    @EntityGraph(attributePaths = "employer")
    @Query("SELECT v FROM VacancyEntity v WHERE v.id = :id")
    Optional<VacancyEntity> findWithEmployerById(@Param("id") Long id);

    long countByStatus(VacancyStatus status);

    long countByStatusAndSourceType(VacancyStatus status, VacancySource sourceType);

    List<VacancyEntity> findTop50ByOrderByPublishedAtDesc();

    @Query("""
            SELECT v FROM VacancyEntity v
            WHERE v.status = com.hrsystem.domain.enums.VacancyStatus.ACTIVE
              AND (:keyword IS NULL OR LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(v.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(v.requirementsStack, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(v.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:salaryMin IS NULL OR COALESCE(v.salaryMax, v.salaryMin) >= :salaryMin)
              AND (:sourceType IS NULL OR v.sourceType = :sourceType)
            """)
    Page<VacancyEntity> searchActive(
            @Param("keyword") String keyword,
            @Param("salaryMin") Integer salaryMin,
            @Param("sourceType") VacancySource sourceType,
            Pageable pageable
    );

    // ---------------------------------------------------------------
    // Нативные SQL-запросы (работают напрямую с PostgreSQL)
    // Используются для сложной аналитики и отчётности
    // ---------------------------------------------------------------

    /**
     * Нативный SQL: статистика по вакансиям — средняя зарплата по статусу.
     * Демонстрирует использование нативного SQL через @Query(nativeQuery = true).
     *
     * @return массив объектов [status, avg_salary_min, avg_salary_max, count]
     */
    @Query(value = """
            SELECT
                status,
                ROUND(AVG(salary_min), 0)  AS avg_salary_min,
                ROUND(AVG(salary_max), 0)  AS avg_salary_max,
                COUNT(*)                   AS total
            FROM vacancies
            WHERE salary_min IS NOT NULL
            GROUP BY status
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> getSalaryStatsByStatus();

    /**
     * Нативный SQL: топ работодателей по количеству активных вакансий.
     *
     * @param limit максимальное количество строк в результате
     * @return массив объектов [company_name, vacancy_count]
     */
    @Query(value = """
            SELECT
                v.company_name,
                COUNT(v.id) AS vacancy_count
            FROM vacancies v
            WHERE v.status = 'ACTIVE'
            GROUP BY v.company_name
            ORDER BY vacancy_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopEmployersByVacancyCount(@Param("limit") int limit);

    /**
     * Нативный SQL: поиск вакансий по зарплатному диапазону.
     * Пример использования нативного SQL с параметрами и BETWEEN.
     *
     * @param minSalary минимальная зарплата (нижняя граница)
     * @param maxSalary максимальная зарплата (верхняя граница)
     * @return список вакансий в указанном диапазоне зарплат
     */
    @Query(value = """
            SELECT v.*
            FROM vacancies v
            WHERE v.status = 'ACTIVE'
              AND v.salary_min IS NOT NULL
              AND v.salary_min BETWEEN :minSalary AND :maxSalary
            ORDER BY v.salary_min DESC
            """, nativeQuery = true)
    List<VacancyEntity> findBySalaryRange(
            @Param("minSalary") Integer minSalary,
            @Param("maxSalary") Integer maxSalary
    );
}

