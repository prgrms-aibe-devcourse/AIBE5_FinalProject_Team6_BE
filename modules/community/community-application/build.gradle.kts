dependencies {
    implementation(project(":modules:community:community-domain"))
    // @Service, @Transactional — JPA 구현체는 포함하지 않음 (ADR-002 §03)
    implementation("org.springframework.boot:spring-boot-starter")
}