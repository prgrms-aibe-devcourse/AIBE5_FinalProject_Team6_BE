dependencies {
    implementation(project(":modules:order:order-domain"))
    implementation(project(":modules:order:order-application"))
    implementation(project(":modules:common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.security:spring-security-core")
}
