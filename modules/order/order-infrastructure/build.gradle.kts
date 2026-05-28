dependencies {
    implementation(project(":modules:order:order-domain"))
    implementation(project(":modules:order:order-application"))
    implementation(project(":modules:payment:payment-domain"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
