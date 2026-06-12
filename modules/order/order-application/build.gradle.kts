dependencies {
    implementation(project(":modules:order:order-domain"))
    implementation(project(":modules:payment:payment-application"))
    implementation("org.springframework:spring-tx")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    testImplementation("org.mockito:mockito-junit-jupiter")
}
