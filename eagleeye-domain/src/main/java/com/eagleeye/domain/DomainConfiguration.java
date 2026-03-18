package com.eagleeye.domain;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;

/**
 * Central JPA configuration for the domain module.
 * Defines entity scanning and repository registration once — shared across all consuming modules.
 * Replaces @EntityScan and @EnableJpaRepositories on individual @SpringBootApplication classes.
 */
@Configuration
@EnableJpaRepositories("com.eagleeye.domain.repository")
public class DomainConfiguration {

    @Bean
    public PersistenceManagedTypes persistenceManagedTypes(ResourceLoader resourceLoader) {
        return new PersistenceManagedTypesScanner(resourceLoader)
                .scan("com.eagleeye.domain.entity");
    }
}
