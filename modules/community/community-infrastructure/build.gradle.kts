dependencies {
    implementation(project(":modules:community:community-domain"))
    api(project(":modules:community:community-application"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
