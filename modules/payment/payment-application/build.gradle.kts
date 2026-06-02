dependencies {
    implementation(project(":modules:payment:payment-domain"))
    implementation("org.springframework:spring-tx")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter")
}
