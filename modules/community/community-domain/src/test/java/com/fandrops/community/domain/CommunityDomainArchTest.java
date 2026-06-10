package com.fandrops.community.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class CommunityDomainArchTest {

    private static JavaClasses domainClasses;

    @BeforeAll
    static void loadClasses() {
        domainClasses = new ClassFileImporter()
                .importPackages("com.fandrops.community.domain");
    }

    @Test
    void domain_must_not_import_spring() {
        ArchRule rule = noClasses()
                .should().accessClassesThat().resideInAPackage("org.springframework..")
                .because("community-domain 은 Spring 에 의존하면 안 됩니다");
        rule.check(domainClasses);
    }

    @Test
    void domain_must_not_import_jpa() {
        ArchRule rule = noClasses()
                .should().accessClassesThat().resideInAPackage("jakarta.persistence..")
                .because("community-domain 은 JPA 에 의존하면 안 됩니다");
        rule.check(domainClasses);
    }

    @Test
    void domain_must_not_import_querydsl() {
        ArchRule rule = noClasses()
                .should().accessClassesThat().resideInAPackage("com.querydsl..")
                .because("community-domain 은 QueryDSL 에 의존하면 안 됩니다");
        rule.check(domainClasses);
    }

    @Test
    void domain_must_not_import_redis() {
        ArchRule rule = noClasses()
                .should().accessClassesThat().resideInAPackage("org.springframework.data.redis..")
                .because("community-domain 은 Redis 에 의존하면 안 됩니다");
        rule.check(domainClasses);
    }
}