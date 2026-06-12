dependencies {
    implementation(project(":modules:inventory:inventory-domain"))
    api(project(":modules:inventory:inventory-application"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
}
