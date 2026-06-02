dependencies {
    implementation(project(":modules:community:community-domain"))
    // @Service — spring-context 포함 (ADR-002 §03: JPA 구현체 제외)
    implementation("org.springframework:spring-context")
    // @Transactional — 트랜잭션 어노테이션만 (JPA 구현체 아님, user-application과 동일 패턴)
    implementation("org.springframework:spring-tx")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
}