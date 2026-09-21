package com.hrsystem.delivery.cli.views;

import com.hrsystem.delivery.cli.utils.ConsoleTableFormatter;
import com.hrsystem.delivery.cli.utils.InputValidator;
import com.hrsystem.domain.entity.ParsingLogEntity;
import com.hrsystem.domain.entity.ParsingSourceEntity;
import com.hrsystem.domain.entity.UserEntity;
import com.hrsystem.domain.entity.VacancyEntity;
import com.hrsystem.domain.enums.VacancySource;
import com.hrsystem.domain.enums.VacancyStatus;
import com.hrsystem.dto.response.DashboardStatsDto;
import com.hrsystem.dto.response.ParsingReportDto;
import com.hrsystem.repository.ParsingLogRepository;
import com.hrsystem.scraper.ScraperCoordinatorService;
import com.hrsystem.service.ModerationService;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.List;

@Component
public class AdminCliView {

    private final ModerationService moderationService;
    private final ScraperCoordinatorService scraperCoordinatorService;
    private final ParsingLogRepository parsingLogRepository;
    private final ConsoleTableFormatter tables;

    public AdminCliView(ModerationService moderationService,
                        ScraperCoordinatorService scraperCoordinatorService,
                        ParsingLogRepository parsingLogRepository,
                        ConsoleTableFormatter tables) {
        this.moderationService = moderationService;
        this.scraperCoordinatorService = scraperCoordinatorService;
        this.parsingLogRepository = parsingLogRepository;
        this.tables = tables;
    }

    public void show(InputValidator input, PrintStream out) {
        boolean inMenu = true;
        while (inMenu) {
            printDashboard(out);
            out.println("[1] Запустить принудительный парсинг HTML-сайтов");
            out.println("[2] Запустить парсинг Telegram-каналов");
            out.println("[3] Управление списком источников (Добавить / вкл-выкл)");
            out.println("[4] Журнал логов парсинга");
            out.println("[5] Модерация каталога вакансий");
            out.println("[6] Блокировка учётных записей");
            out.println("[7] Нативная SQL-аналитика (ORM demo)");
            out.println("[0] Выход в главное меню (Logout)");
            int choice = input.readIntInRange("Выберите команду > ", 0, 7);
            try {
                switch (choice) {
                    case 1 -> runSites(input, out);
                    case 2 -> runTelegram(input, out);
                    case 3 -> manageSources(input, out);
                    case 4 -> showLogs(out);
                    case 5 -> moderateVacancies(input, out);
                    case 6 -> moderateUsers(input, out);
                    case 7 -> showNativeStats(input, out);
                    case 0 -> inMenu = false;
                    default -> {
                    }
                }
            } catch (RuntimeException ex) {
                out.println("[Ошибка] " + ex.getMessage());
            }
        }
    }

    private void printDashboard(PrintStream out) {
        DashboardStatsDto stats = moderationService.getDashboardStats();
        out.println();
        out.println("=== ПАНЕЛЬ УПРАВЛЕНИЯ ПАРСЕРАМИ И СБОРОМ ДАННЫХ ===");
        out.println("Статистика базы данных:");
        out.println("- Всего активных вакансий: " + stats.getActiveVacancies());
        out.println("- Спарсено через Web Scraper: " + stats.getWebsiteVacancies());
        out.println("- Спарсено через Telegram Mirror: " + stats.getTelegramVacancies());
        out.println("- Добавлено работодателями вручную: " + stats.getManualVacancies());
        out.println("- Последний сбор: " + stats.getLastParsingStartedAt() + " (" + stats.getLastParsingStatus() + ")");
        out.println("------------------------------------------------------------");
    }

    private void runSites(InputValidator input, PrintStream out) {
        out.println();
        out.println(">>> Запуск HTML Web Scraper...");
        ParsingReportDto report = scraperCoordinatorService.runWebsiteScraping(out::println);
        printReport(report, out);
        input.readOptionalString("Enter для продолжения > ");
    }

    private void runTelegram(InputValidator input, PrintStream out) {
        out.println();
        out.println(">>> Запуск Telegram Web Mirror Extractor...");
        ParsingReportDto report = scraperCoordinatorService.runTelegramScraping(out::println);
        printReport(report, out);
        input.readOptionalString("Enter для продолжения > ");
    }

    private void printReport(ParsingReportDto report, PrintStream out) {
        out.println();
        out.println("=== ОТЧЁТ ПАРСИНГА ===");
        out.println("Найдено:            " + report.getItemsFound());
        out.println("Добавлено новых:    " + report.getItemsSaved());
        out.println("Отсеяно дубликатов: " + report.getDuplicatesSkipped());
        out.println("Ошибок источников:  " + report.getFailedSources());
        if (report.getSummary() != null) {
            out.println(report.getSummary());
        }
    }

    private void manageSources(InputValidator input, PrintStream out) {
        boolean inSources = true;
        while (inSources) {
            List<ParsingSourceEntity> sources = moderationService.listSources();
            out.println();
            out.println("=== ИСТОЧНИКИ СБОРА ===");
            out.println(tables.formatSources(sources));
            out.println("[1] Добавить URL  [2] Включить/выключить  [0] Назад");
            int action = input.readIntInRange("Действие > ", 0, 2);
            if (action == 0) {
                inSources = false;
            } else if (action == 1) {
                String name = input.readRequiredString("Название > ");
                int type = input.readIntInRange("Тип [1] WEBSITE [2] TELEGRAM > ", 1, 2);
                String url = input.readUrl("URL > ");
                ParsingSourceEntity created = moderationService.addSource(
                        name,
                        type == 2 ? VacancySource.TELEGRAM : VacancySource.WEBSITE,
                        url
                );
                out.println("[OK] Источник #" + created.getId() + " добавлен");
            } else {
                long id = input.readLong("ID источника > ");
                ParsingSourceEntity updated = moderationService.toggleSource(id);
                out.println("[OK] Источник #" + updated.getId() + " активен=" + updated.isActive());
            }
        }
    }

    private void showLogs(PrintStream out) {
        List<ParsingLogEntity> logs = parsingLogRepository.findTop20ByOrderByStartedAtDesc();
        out.println();
        out.println("=== ЖУРНАЛ ПАРСИНГА ===");
        out.println(tables.formatLogs(logs));
        for (ParsingLogEntity log : logs) {
            if (log.getErrorMessage() != null && !log.getErrorMessage().isBlank()) {
                out.println("Лог #" + log.getId() + " ошибка: " + log.getErrorMessage());
            }
        }
    }

    private void moderateVacancies(InputValidator input, PrintStream out) {
        List<VacancyEntity> vacancies = moderationService.listRecentVacancies();
        out.println();
        out.println("=== МОДЕРАЦИЯ ВАКАНСИЙ ===");
        out.println(tables.formatVacancies(vacancies));
        out.println("[1] В архив (скрыть)  [2] Отклонить  [3] Вернуть ACTIVE  [0] Назад");
        int action = input.readIntInRange("Действие > ", 0, 3);
        if (action == 0) {
            return;
        }
        long id = input.readLong("ID вакансии > ");
        VacancyStatus status = switch (action) {
            case 2 -> VacancyStatus.REJECTED;
            case 3 -> VacancyStatus.ACTIVE;
            default -> VacancyStatus.ARCHIVED;
        };
        VacancyEntity updated = moderationService.changeVacancyStatus(id, status);
        out.println("[OK] Вакансия #" + updated.getId() + " → " + updated.getStatus());
    }

    private void moderateUsers(InputValidator input, PrintStream out) {
        List<UserEntity> users = moderationService.listUsers();
        out.println();
        out.println("=== ПОЛЬЗОВАТЕЛИ ===");
        out.println(tables.formatUsers(users));
        out.println("[1] Заблокировать  [2] Разблокировать  [0] Назад");
        int action = input.readIntInRange("Действие > ", 0, 2);
        if (action == 0) {
            return;
        }
        long id = input.readLong("ID пользователя > ");
        UserEntity updated = moderationService.setUserActive(id, action == 2);
        out.println("[OK] Пользователь " + updated.getEmail() + " активен=" + updated.isActive());
    }

    private void showNativeStats(InputValidator input, PrintStream out) {
        out.println();
        out.println("=== НАТИВНАЯ SQL-АНАЛИТИКА ===");
        out.println("(Запросы выполняются напрямую через nativeQuery = true)");
        out.println("------------------------------------------------------------");
        List<String> lines = moderationService.getNativeStats();
        for (String line : lines) {
            out.println(line);
        }
        out.println("------------------------------------------------------------");
        input.readOptionalString("Enter для продолжения > ");
    }
}
