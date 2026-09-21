package com.hrsystem.service;

import com.hrsystem.domain.entity.ParsingSourceEntity;
import com.hrsystem.domain.entity.UserEntity;
import com.hrsystem.domain.entity.VacancyEntity;
import com.hrsystem.domain.enums.VacancySource;
import com.hrsystem.domain.enums.VacancyStatus;
import com.hrsystem.dto.response.DashboardStatsDto;

import java.util.List;

public interface ModerationService {

    DashboardStatsDto getDashboardStats();

    List<VacancyEntity> listRecentVacancies();

    VacancyEntity changeVacancyStatus(Long vacancyId, VacancyStatus status);

    List<UserEntity> listUsers();

    UserEntity setUserActive(Long userId, boolean active);

    List<ParsingSourceEntity> listSources();

    ParsingSourceEntity addSource(String name, VacancySource sourceType, String baseUrl);

    ParsingSourceEntity toggleSource(Long sourceId);

    /**
     * Нативная аналитика: вызывает нативные SQL-запросы из репозиториев.
     * Возвращает строки для отображения в дашборде администратора.
     */
    List<String> getNativeStats();
}
