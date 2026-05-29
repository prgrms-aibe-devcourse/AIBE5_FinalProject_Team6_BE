dependencies {
    implementation(project(":modules:payment:payment-domain"))
    implementation(project(":modules:payment:payment-application"))
    implementation(project(":modules:order:order-domain"))  // AccessTicketValidator 구현용
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter")
}
