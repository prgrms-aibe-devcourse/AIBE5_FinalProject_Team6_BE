dependencies {
    implementation(project(":modules:user:user-domain"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.security:spring-security-crypto")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
}
