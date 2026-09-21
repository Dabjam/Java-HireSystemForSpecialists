package com.hrsystem.service.impl;

import com.hrsystem.domain.entity.ParsingSourceEntity;
import com.hrsystem.domain.entity.UserEntity;
import com.hrsystem.domain.entity.VacancyEntity;
import com.hrsystem.domain.enums.UserRole;
import com.hrsystem.domain.enums.VacancySource;
import com.hrsystem.domain.enums.VacancyStatus;
import com.hrsystem.repository.ApplicationRepository;
import com.hrsystem.repository.ParsingLogRepository;
import com.hrsystem.repository.ParsingSourceRepository;
import com.hrsystem.repository.UserRepository;
import com.hrsystem.repository.VacancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceImplTest {

    @Mock
    private VacancyRepository vacancyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private ParsingSourceRepository parsingSourceRepository;
    @Mock
    private ParsingLogRepository parsingLogRepository;

    private ModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModerationServiceImpl(
                vacancyRepository, userRepository, applicationRepository, parsingSourceRepository, parsingLogRepository);
    }


    @Test
    void hidesVacancyFromCatalogByArchiving() {
        VacancyEntity vacancy = new VacancyEntity();
        vacancy.setId(3L);
        vacancy.setStatus(VacancyStatus.ACTIVE);
        when(vacancyRepository.findById(3L)).thenReturn(Optional.of(vacancy));
        when(vacancyRepository.save(any(VacancyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        VacancyEntity updated = service.changeVacancyStatus(3L, VacancyStatus.ARCHIVED);
        assertEquals(VacancyStatus.ARCHIVED, updated.getStatus());
    }

    @Test
    void cannotBlockAdmin() {
        UserEntity admin = new UserEntity();
        admin.setId(1L);
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.setUserActive(1L, false));
    }

    @Test
    void toggleSourceFlipsActiveFlag() {
        ParsingSourceEntity source = new ParsingSourceEntity();
        source.setId(8L);
        source.setActive(true);
        when(parsingSourceRepository.findById(8L)).thenReturn(Optional.of(source));
        when(parsingSourceRepository.save(any(ParsingSourceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ParsingSourceEntity updated = service.toggleSource(8L);
        assertFalse(updated.isActive());
    }

    @Test
    void addSourceRejectsManualType() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addSource("X", VacancySource.MANUAL, "https://example.com"));
    }

    @Test
    void dashboardCountsActiveBySource() {
        when(vacancyRepository.countByStatus(VacancyStatus.ACTIVE)).thenReturn(12L);
        when(vacancyRepository.countByStatusAndSourceType(VacancyStatus.ACTIVE, VacancySource.WEBSITE)).thenReturn(7L);
        when(vacancyRepository.countByStatusAndSourceType(VacancyStatus.ACTIVE, VacancySource.TELEGRAM)).thenReturn(3L);
        when(vacancyRepository.countByStatusAndSourceType(VacancyStatus.ACTIVE, VacancySource.MANUAL)).thenReturn(2L);
        when(parsingLogRepository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.empty());

        var stats = service.getDashboardStats();
        assertEquals(12L, stats.getActiveVacancies());
        assertEquals(2L, stats.getManualVacancies());
        assertTrue(stats.getLastParsingStartedAt().contains("не запускался"));
    }
}
