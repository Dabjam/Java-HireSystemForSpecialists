package com.hrsystem.repository;

import com.hrsystem.domain.entity.ApplicationEntity;
import com.hrsystem.domain.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    // ---------------------------------------------------------------
    // JPQL-запросы (ORM-уровень)
    // ---------------------------------------------------------------

    @EntityGraph(attributePaths = {"candidate", "vacancy"})
    List<ApplicationEntity> findByVacancyIdOrderByCreatedAtDesc(Long vacancyId);

    @EntityGraph(attributePaths = {"candidate", "vacancy"})
    List<ApplicationEntity> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    @EntityGraph(attributePaths = {"candidate", "vacancy"})
    @Query("SELECT a FROM ApplicationEntity a WHERE a.candidate.id = :candidateProfileId ORDER BY a.createdAt DESC")
    List<ApplicationEntity> findByCandidateProfileIdOrderByCreatedAtDesc(@Param("candidateProfileId") Long candidateProfileId);

    @EntityGraph(attributePaths = {"candidate", "vacancy", "vacancy.employer"})
    @Query("SELECT a FROM ApplicationEntity a WHERE a.id = :id")
    Optional<ApplicationEntity> findWithDetailsById(@Param("id") Long id);

    boolean existsByVacancyIdAndCandidateIdAndStatusIn(
            Long vacancyId,
            Long candidateId,
            Collection<ApplicationStatus> statuses
    );

    @Query("SELECT COUNT(a) > 0 FROM ApplicationEntity a " +
           "WHERE a.vacancy.id = :vacancyId " +
           "AND a.candidate.id = :candidateProfileId " +
           "AND a.status IN :statuses")
    boolean existsActiveApplication(
            @Param("vacancyId") Long vacancyId,
            @Param("candidateProfileId") Long candidateProfileId,
            @Param("statuses") Collection<ApplicationStatus> statuses
    );

    long countByVacancyId(Long vacancyId);

    // ---------------------------------------------------------------
    // Нативные SQL-запросы
    // ---------------------------------------------------------------

    /**
     * Нативный SQL: количество откликов по каждому статусу.
     * Используется для дашборда администратора.
     *
     * @return массив объектов [status, count]
     */
    @Query(value = """
            SELECT
                status,
                COUNT(*) AS cnt
            FROM applications
            GROUP BY status
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> countApplicationsByStatus();

    /**
     * Нативный SQL: самые популярные вакансии по количеству откликов.
     *
     * @param limit количество записей
     * @return массив объектов [vacancy_id, title, company_name, application_count]
     */
    @Query(value = """
            SELECT
                v.id          AS vacancy_id,
                v.title       AS title,
                v.company_name AS company_name,
                COUNT(a.id)   AS application_count
            FROM applications a
            JOIN vacancies v ON a.vacancy_id = v.id
            GROUP BY v.id, v.title, v.company_name
            ORDER BY application_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopVacanciesByApplicationCount(@Param("limit") int limit);
}

