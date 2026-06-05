dependencies {
    implementation(project(":modules:community:community-application"))
    implementation(project(":modules:community:community-domain"))
    implementation(project(":modules:common"))               // ApiResponse 공통 envelope
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation") // @Valid
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.mockito:mockito-junit-jupiter")
}