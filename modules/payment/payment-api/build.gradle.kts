dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:payment:payment-domain"))
    implementation(project(":modules:payment:payment-application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}