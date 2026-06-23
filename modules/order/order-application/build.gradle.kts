dependencies {
    implementation(project(":modules:order:order-domain"))
    implementation(project(":modules:payment:payment-application"))
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    implementation("net.javacrumbs.shedlock:shedlock-spring:6.3.0")
    testImplementation("org.mockito:mockito-junit-jupiter")
}
