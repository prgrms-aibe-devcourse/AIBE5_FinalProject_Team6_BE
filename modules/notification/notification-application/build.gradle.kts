dependencies {
    implementation(project(":modules:notification:notification-domain"))
    implementation(project(":modules:payment:payment-application"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("org.slf4j:slf4j-api")
    compileOnly("com.fasterxml.jackson.core:jackson-annotations")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter")
}
