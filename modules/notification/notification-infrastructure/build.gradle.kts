dependencies {
    implementation(project(":modules:notification:notification-domain"))
    implementation(project(":modules:notification:notification-application"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter")
}
