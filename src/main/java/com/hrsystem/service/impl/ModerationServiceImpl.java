package com.hrsystem.service.impl;

import com.hrsystem.domain.entity.ParsingLogEntity;
import com.hrsystem.domain.entity.ParsingSourceEntity;
import com.hrsystem.domain.entity.UserEntity;
import com.hrsystem.domain.entity.VacancyEntity;
import com.hrsystem.domain.enums.UserRole;
import com.hrsystem.domain.enums.VacancySource;
import com.hrsystem.domain.enums.VacancyStatus;
import com.hrsystem.dto.response.DashboardStatsDto;
import com.hrsystem.exception.EntityNotFoundException;
import com.hrsystem.repository.ApplicationRepository;
import com.hrsystem.repository.ParsingLogRepository;
import com.hrsystem.repository.ParsingSourceRepository;
import com.hrsystem.repository.UserRepository;
import com.hrsystem.repository.VacancyRepository;
import com.hrsystem.service.ModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ModerationServiceImpl implements ModerationService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final VacancyRepository vacancyRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ParsingSourceRepository parsingSourceRepository;
    private final ParsingLogRepository parsingLogRepository;

    public ModerationServiceImpl(VacancyRepository vacancyRepository,
                                 UserRepository userRepository,
                                 ApplicationRepository applicationRepository,
                                 ParsingSourceRepository parsingSourceRepository,
                                 ParsingLogRepository parsingLogRepository) {
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.parsingSourceRepository = parsingSourceRepository;
        this.parsingLogRepository = parsingLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        DashboardStatsDto stats = new DashboardStatsDto();
        stats.setActiveVacancies(vacancyRepository.countByStatus(VacancyStatus.ACTIVE));
        stats.setWebsiteVacancies(vacancyRepository.countByStatusAndSourceType(VacancyStatus.ACTIVE, VacancySource.WEBSITE));
        stats.setTelegramVacancies(vacancyRepository.countByStatusAndSourceType(VacancyStatus.ACTIVE, VacancySource.TELEGRAM));
        stats.setManualVacancies(vacancyRepository.countByStatusAndSourceType(VacancyStatus.ACTIVE, VacancySource.MANUAL));
        parsingLogRepository.findTopByOrderByStartedAtDesc().ifPresent(log -> {
            stats.setLastParsingStartedAt(log.getStartedAt() == null ? "—" : TS.format(log.getStartedAt()));
            stats.setLastParsingStatus(log.getStatus());
        });
        if (stats.getLastParsingStatus() == null) {
            stats.setLastParsingStartedAt("ещё не запускался");
            stats.setLastParsingStatus("—");
        }
        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VacancyEntity> listRecentVacancies() {
        return vacancyRepository.findTop50ByOrderByPublishedAtDesc();
    }

    @Override
    public VacancyEntity changeVacancyStatus(Long vacancyId, VacancyStatus status) {
        VacancyEntity vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new EntityNotFoundException("Вакансия #" + vacancyId + " не найдена"));
        vacancy.setStatus(status);
        return vacancyRepository.save(vacancy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserEntity> listUsers() {
        return userRepository.findAllByOrderByIdAsc();
    }

    @Override
    public UserEntity setUserActive(Long userId, boolean active) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь #" + userId + " не найден"));
        if (user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("Нельзя блокировать учётную запись администратора");
        }
        user.setActive(active);
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParsingSourceEntity> listSources() {
        return parsingSourceRepository.findAllByOrderByIdAsc();
    }

    @Override
    public ParsingSourceEntity addSource(String name, VacancySource sourceType, String baseUrl) {
        if (sourceType == VacancySource.MANUAL) {
            throw new IllegalArgumentException("Источник сбора может быть только WEBSITE или TELEGRAM");
        }
        if (parsingSourceRepository.existsByBaseUrlIgnoreCase(baseUrl)) {
            throw new IllegalArgumentException("Источник с таким URL уже существует");
        }
        ParsingSourceEntity source = new ParsingSourceEntity();
        source.setName(name.trim());
        source.setSourceType(sourceType);
        source.setBaseUrl(baseUrl.trim());
        source.setActive(true);
        return parsingSourceRepository.save(source);
    }

    @Override
    public ParsingSourceEntity toggleSource(Long sourceId) {
        ParsingSourceEntity source = parsingSourceRepository.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("Источник #" + sourceId + " не найден"));
        source.setActive(!source.isActive());
        return parsingSourceRepository.save(source);
    }

    /**
     * Нативная SQL-аналитика: агрегирует данные через нативные запросы из трёх репозиториев.
     * Демонстрирует использование nativeQuery = true в Spring Data JPA.
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> getNativeStats() {
        List<String> lines = new ArrayList<>();

        // --- Native SQL #1: UserRepository — статистика пользователей по ролям ---
        lines.add("  [Native SQL] Пользователи по ролям (users GROUP BY role):");
        List<Object[]> userRoles = userRepository.countUsersByRole();
        for (Object[] row : userRoles) {
            lines.add("    • " + row[0] + " → " + row[1] + " чел.");
        }

        // --- Native SQL #2: VacancyRepository — средняя зарплата по статусу вакансий ---
        lines.add("  [Native SQL] Средняя зарплата по статусу вакансий:");
        List<Object[]> salaryStats = vacancyRepository.getSalaryStatsByStatus();
        for (Object[] row : salaryStats) {
            lines.add("    • " + row[0] + ": avg_min=" + row[1] + ", avg_max=" + row[2] + ", кол-во=" + row[3]);
        }

        // --- Native SQL #3: VacancyRepository — топ-3 работодателя по вакансиям ---
        lines.add("  [Native SQL] Топ-3 работодателя по активным вакансиям:");
        List<Object[]> topEmployers = vacancyRepository.getTopEmployersByVacancyCount(3);
        for (Object[] row : topEmployers) {
            lines.add("    • " + row[0] + " → " + row[1] + " вак.");
        }

        // --- Native SQL #4: ApplicationRepository — количество откликов по статусам ---
        lines.add("  [Native SQL] Отклики по статусам (applications GROUP BY status):");
        List<Object[]> appStats = applicationRepository.countApplicationsByStatus();
        for (Object[] row : appStats) {
            lines.add("    • " + row[0] + " → " + row[1] + " шт.");
        }

        return lines;
    }
}

