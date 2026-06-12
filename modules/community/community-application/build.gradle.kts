dependencies {
    implementation(project(":modules:community:community-domain"))
    // community → user 단방향 의존 (역방향 금지 — 순환 의존성 방지)
    // AgencyApprovedEvent 소비 목적 (ArtistProfilePort.findAllByIds 구현은 api-server 위임)
    implementation(project(":modules:user:user-application"))
    // @Service — spring-context 포함 (ADR-002 §03: JPA 구현체 제외)
    implementation("org.springframework:spring-context")
    // @Transactional — 트랜잭션 어노테이션만 (JPA 구현체 아님, user-application과 동일 패턴)
    implementation("org.springframework:spring-tx")
    implementation("org.slf4j:slf4j-api")
    testImplementation("org.mockito:mockito-junit-jupiter")
}