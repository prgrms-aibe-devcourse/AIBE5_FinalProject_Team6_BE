dependencies {
    implementation(project(":modules:order:order-domain"))
    implementation(project(":modules:order:order-application"))
    implementation(project(":modules:inventory:inventory-domain"))
    implementation(project(":modules:inventory:inventory-application"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
