dependencies {
    implementation(project(":modules:order:order-domain"))
    implementation(project(":modules:order:order-application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
