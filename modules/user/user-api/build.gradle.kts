dependencies {
    implementation(project(":modules:user:user-domain"))
    implementation(project(":modules:user:user-application"))
    implementation(project(":modules:common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.security:spring-security-web")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.security:spring-security-core")
}
