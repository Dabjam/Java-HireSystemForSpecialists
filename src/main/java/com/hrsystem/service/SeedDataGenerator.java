package com.hrsystem.service;

import com.hrsystem.domain.entity.*;
import com.hrsystem.domain.enums.*;
import com.hrsystem.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Генератор начальных тестовых данных (Seed Data).
 * Запускается автоматически при старте приложения, если база данных пустая.
 *
 * <p>Демонстрирует работу с базой данных через:
 * <ul>
 *   <li><b>ORM (Hibernate/JPA)</b> — маппинг объектов на таблицы через аннотации</li>
 *   <li><b>Каскадное сохранение</b> — связи OneToOne, ManyToOne с @JoinColumn</li>
 *   <li><b>Ленивая загрузка</b> — FetchType.LAZY для ManyToOne отношений</li>
 *   <li><b>@PrePersist / @PreUpdate</b> — автоматическое проставление временных меток</li>
 *   <li><b>@Transactional</b> — атомарность всей операции генерации данных</li>
 *   <li><b>ENUM-столбцы</b> — хранение статусов через Java enum -> VARCHAR в PostgreSQL</li>
 * </ul>
 */
@Component
@Profile("!test")
public class SeedDataGenerator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataGenerator.class);

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final ParsingSourceRepository parsingSourceRepository;
    private final VacancyRepository vacancyRepository;
    private final ApplicationRepository applicationRepository;
    private final ParsingLogRepository parsingLogRepository;

    public SeedDataGenerator(UserRepository userRepository,
                              CandidateProfileRepository candidateProfileRepository,
                              EmployerProfileRepository employerProfileRepository,
                              ParsingSourceRepository parsingSourceRepository,
                              VacancyRepository vacancyRepository,
                              ApplicationRepository applicationRepository,
                              ParsingLogRepository parsingLogRepository) {
        this.userRepository = userRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.parsingSourceRepository = parsingSourceRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.parsingLogRepository = parsingLogRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("База данных уже содержит данные. Пропуск генерации Seed Data.");
            return;
        }

        log.info("=== Инициализация начальных тестовых данных (Seed Data) ===");

        // ============================================================
        // 1. Пользователи (таблица: users)
        //    Пароль для всех: "password123" (BCrypt-хэш, 10 раундов)
        //    ORM: @Enumerated(EnumType.STRING) для поля role
        // ============================================================
        final String bcryptHash = "$2a$10$e8p2Nq1uO7eY7Y9Z6qQ5uO.Z4f6g7h8i9j0k1l2m3n4o5p6q7r8s9";

        userRepository.save(new UserEntity("admin@hrsystem.com", bcryptHash, UserRole.ADMIN));

        UserEntity empUser1 = userRepository.save(new UserEntity("hr@techcorp.com",   bcryptHash, UserRole.EMPLOYER));
        UserEntity empUser2 = userRepository.save(new UserEntity("recruiter@fintech.io", bcryptHash, UserRole.EMPLOYER));

        UserEntity candUser1 = userRepository.save(new UserEntity("ivan.dev@gmail.com",    bcryptHash, UserRole.CANDIDATE));
        UserEntity candUser2 = userRepository.save(new UserEntity("elena.qa@yandex.ru",    bcryptHash, UserRole.CANDIDATE));
        UserEntity candUser3 = userRepository.save(new UserEntity("aleksei.ml@mail.ru",    bcryptHash, UserRole.CANDIDATE));

        // ============================================================
        // 2. Профили работодателей (таблица: employer_profiles)
        //    ORM: @OneToOne (users -> employer_profiles), EAGER fetch
        // ============================================================
        EmployerProfileEntity empProfile1 = createEmployerProfile(empUser1,
                "TechCorp Solutions",
                "Ведущий разработчик высоконагруженных IT-систем и облачных платформ.",
                "Анна Смирнова",
                "https://techcorp.com");

        EmployerProfileEntity empProfile2 = createEmployerProfile(empUser2,
                "FinTech Innovations",
                "Инновационная финтех платформа нового поколения для B2B рынка.",
                "Игорь Ковалев",
                "https://fintech.io");

        // ============================================================
        // 3. Профили соискателей (таблица: candidate_profiles)
        //    ORM: fullName (единое поле), targetTitle, skills TEXT,
        //         telegram (контакт), @PrePersist -> updated_at
        // ============================================================
        CandidateProfileEntity candProfile1 = candidateProfileRepository.save(
                new CandidateProfileEntity(candUser1,
                        "Иван Петров",
                        "Senior Java Developer",
                        "Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, Kafka, Docker, Redis",
                        "+79991234567",
                        "@ivan_dev"));

        CandidateProfileEntity candProfile2 = candidateProfileRepository.save(
                new CandidateProfileEntity(candUser2,
                        "Елена Сидорова",
                        "QA Automation Engineer",
                        "Java, Selenium, JUnit 5, REST Assured, Allure, TestNG, Postman",
                        "+79997654321",
                        "@elena_qa"));

        candidateProfileRepository.save(
                new CandidateProfileEntity(candUser3,
                        "Алексей Новиков",
                        "ML Engineer",
                        "Python, TensorFlow, PyTorch, scikit-learn, SQL, Docker, MLflow",
                        "+79995551234",
                        "@alex_ml"));

        // ============================================================
        // 4. Источники парсинга (таблица: parsing_sources)
        //    ORM: VacancySource enum -> VARCHAR(50), base_url UNIQUE
        // ============================================================
        ParsingSourceEntity sourceHH = createParsingSource(
                "HeadHunter (hh.ru)", VacancySource.WEBSITE, "https://hh.ru");

        ParsingSourceEntity sourceHabr = createParsingSource(
                "Хабр Карьера", VacancySource.WEBSITE, "https://career.habr.com");

        ParsingSourceEntity sourceTg = createParsingSource(
                "Telegram: IT Jobs", VacancySource.TELEGRAM, "https://t.me/itjobs_ru");

        // ============================================================
        // 5. Вакансии (таблица: vacancies)
        //    ORM: @ManyToOne(LAZY) -> employer_profiles, parsing_sources
        //    INTEGER поля: salary_min, salary_max
        //    Нативный запрос в репозитории: findWithFilters(), searchActive()
        // ============================================================
        VacancyEntity v1 = createVacancy(empProfile1, sourceHH,
                "Senior Java Developer", "TechCorp Solutions",
                280_000, 350_000,
                "Разработка микросервисной архитектуры на Spring Boot 3. " +
                "Оптимизация PostgreSQL-запросов, интеграция с Kafka и Redis.",
                "Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, Kafka, Docker",
                "hash_java_senior_001", "https://hh.ru/vacancy/100001",
                EmploymentType.REMOTE, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        VacancyEntity v2 = createVacancy(empProfile1, sourceHabr,
                "Middle Java Engineer", "TechCorp Solutions",
                180_000, 240_000,
                "Поддержка и развитие core-сервисов банковской платформы. " +
                "Участие в code-review и написание unit/integration тестов.",
                "Java 11/17, Spring Boot, REST API, JUnit 5, Mockito",
                "hash_java_middle_002", "https://career.habr.com/vacancies/200001",
                EmploymentType.OFFICE, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        createVacancy(empProfile2, sourceHH,
                "Lead Backend Developer (Java)", "FinTech Innovations",
                400_000, 500_000,
                "Проектирование архитектуры высоконагруженных финансовых шлюзов и платёжных систем.",
                "Java 21, Spring Cloud, Kafka, Redis, PostgreSQL, Kubernetes",
                "hash_java_lead_003", "https://hh.ru/vacancy/100002",
                EmploymentType.HYBRID, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        createVacancy(empProfile2, sourceHabr,
                "DevOps Engineer", "FinTech Innovations",
                250_000, 320_000,
                "Настройка CI/CD пайплайнов и управление Kubernetes кластерами в production.",
                "Docker, Kubernetes, GitLab CI, Terraform, Ansible, Helm",
                "hash_devops_004", "https://career.habr.com/vacancies/200002",
                EmploymentType.REMOTE, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        createVacancy(empProfile1, sourceHH,
                "QA Automation Engineer (Java)", "TechCorp Solutions",
                160_000, 210_000,
                "Автоматизация тестирования REST API и UI интерфейсов с нуля.",
                "Java, Selenium, TestNG, REST Assured, Allure, Postman",
                "hash_qa_005", "https://hh.ru/vacancy/100003",
                EmploymentType.HYBRID, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        createVacancy(empProfile1, sourceHH,
                "Junior Java Developer", "TechCorp Solutions",
                90_000, 130_000,
                "Разработка внутренних утилит и интеграционных сервисов под кураторством тимлида.",
                "Java Core, Collections, SQL, Spring Boot, Git",
                "hash_java_junior_006", "https://hh.ru/vacancy/100004",
                EmploymentType.OFFICE, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        createVacancy(empProfile2, sourceHabr,
                "Database Administrator (PostgreSQL)", "FinTech Innovations",
                260_000, 330_000,
                "Тюнинг производительности PostgreSQL: индексы, партиционирование, репликация, бэкапы.",
                "PostgreSQL, Patroni, pgBouncer, WAL, VACUUM, Bash",
                "hash_dba_007", "https://career.habr.com/vacancies/200003",
                EmploymentType.REMOTE, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        createVacancy(empProfile1, sourceHH,
                "Fullstack Developer (Java + React)", "TechCorp Solutions",
                220_000, 280_000,
                "Создание личных кабинетов и внутренних дашбордов для HR-платформы.",
                "Java 17, Spring Boot, React 18, TypeScript, Tailwind CSS",
                "hash_fullstack_008", "https://hh.ru/vacancy/100005",
                EmploymentType.HYBRID, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        createVacancy(empProfile2, sourceHabr,
                "Security Engineer (AppSec)", "FinTech Innovations",
                270_000, 350_000,
                "Анализ безопасности архитектуры, SAST/DAST сканирование приложений.",
                "OWASP Top 10, OAuth2, JWT, SAST, DAST, Burp Suite, Linux",
                "hash_appsec_009", "https://career.habr.com/vacancies/200004",
                EmploymentType.REMOTE, VacancyStatus.ACTIVE, VacancySource.WEBSITE);

        createVacancy(empProfile1, sourceTg,
                "Data Engineer (ETL/Pipelines)", "TechCorp Solutions",
                240_000, 310_000,
                "Построение ETL-пайплайнов для сбора и аналитики данных о рынке труда.",
                "Python, SQL, Apache Airflow, PostgreSQL, Apache Spark",
                "hash_dataeng_010", "https://t.me/itjobs_ru/100010",
                EmploymentType.REMOTE, VacancyStatus.ACTIVE, VacancySource.TELEGRAM);

        // ============================================================
        // 6. Отклики на вакансии (таблица: applications)
        //    ORM: @ManyToOne -> vacancies, candidate_profiles
        //    ApplicationStatus: APPLIED, REVIEWING, OFFER, REJECTED, WITHDRAWN
        // ============================================================
        applicationRepository.save(new ApplicationEntity(v1, candProfile1,
                "Здравствуйте! Меня очень заинтересовала вакансия Senior Java Developer. " +
                "Имею более 5 лет опыта работы с Java и экосистемой Spring. " +
                "Готов к техническому интервью в любое удобное время."));

        ApplicationEntity app2 = new ApplicationEntity(v2, candProfile2,
                "Добрый день! Хочу присоединиться к команде в роли Middle Java Engineer. " +
                "Опыт в QA помог глубоко понять требования к качеству кода.");
        app2.setStatus(ApplicationStatus.REVIEWING);
        applicationRepository.save(app2);

        applicationRepository.save(new ApplicationEntity(v1, candProfile2,
                "Имею опыт в тестировании Java-приложений и хочу перейти в разработку."));

        // ============================================================
        // 7. Логи парсинга (таблица: parsing_logs)
        //    ORM: @ManyToOne -> parsing_sources, @PrePersist -> started_at
        //    Показываем статистику работы парсера
        // ============================================================
        createParsingLog(sourceHH,    85, 62, 23);
        createParsingLog(sourceHabr,  40, 38,  2);
        createParsingLog(sourceTg,    15, 10,  5);

        log.info("=== Seed Data успешно загружен! ===");
        log.info("  Пользователей : {}", userRepository.count());
        log.info("  Вакансий      : {}", vacancyRepository.count());
        log.info("  Откликов      : {}", applicationRepository.count());
        log.info("  Логов парсинга: {}", parsingLogRepository.count());
    }

    // ================================================================
    // Вспомогательные приватные методы
    // ================================================================

    private EmployerProfileEntity createEmployerProfile(UserEntity user, String companyName,
                                                         String description, String contact,
                                                         String website) {
        EmployerProfileEntity profile = new EmployerProfileEntity();
        profile.setUser(user);
        profile.setCompanyName(companyName);
        profile.setDescription(description);
        profile.setContactPerson(contact);
        profile.setWebsiteUrl(website);
        return employerProfileRepository.save(profile);
    }

    private ParsingSourceEntity createParsingSource(String name, VacancySource type, String baseUrl) {
        ParsingSourceEntity source = new ParsingSourceEntity();
        source.setName(name);
        source.setSourceType(type);
        source.setBaseUrl(baseUrl);
        source.setActive(true);
        return parsingSourceRepository.save(source);
    }

    private VacancyEntity createVacancy(EmployerProfileEntity employer,
                                         ParsingSourceEntity source,
                                         String title, String companyName,
                                         Integer salaryMin, Integer salaryMax,
                                         String description, String requirements,
                                         String contentHash, String sourceUrl,
                                         EmploymentType employmentType,
                                         VacancyStatus status,
                                         VacancySource sourceType) {
        VacancyEntity v = new VacancyEntity(
                title, companyName, salaryMin, salaryMax,
                Currency.RUB, description, requirements,
                "Россия", employmentType, sourceType);
        v.setEmployer(employer);
        v.setSource(source);
        v.setContentHash(contentHash);
        v.setSourceUrl(sourceUrl);
        v.setStatus(status);
        v.setIsParsed(VacancySource.WEBSITE.equals(sourceType) || VacancySource.TELEGRAM.equals(sourceType));
        return vacancyRepository.save(v);
    }

    private void createParsingLog(ParsingSourceEntity source,
                                   int found, int saved, int duplicates) {
        ParsingLogEntity logEntry = new ParsingLogEntity();
        logEntry.setSource(source);
        logEntry.setItemsFound(found);
        logEntry.setItemsSaved(saved);
        logEntry.setDuplicatesSkipped(duplicates);
        logEntry.setStatus("SUCCESS");
        parsingLogRepository.save(logEntry);
    }
}
