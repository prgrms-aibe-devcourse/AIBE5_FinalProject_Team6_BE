dependencies {
    implementation(project(":modules:payment:payment-domain"))
    implementation(project(":modules:payment:payment-application"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.assertj:assertj-core")
}
