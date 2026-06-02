dependencies {
    implementation(project(":modules:order:order-domain"))
    implementation(project(":modules:order:order-application"))
    implementation(project(":modules:inventory:inventory-domain"))
    implementation(project(":modules:inventory:inventory-application"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.mockito:mockito-junit-jupiter")
}
