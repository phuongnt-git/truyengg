plugins {
    java
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.truyengg"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.graphql-java:graphql-java-extended-scalars:24.0")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-json")

    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5")
    implementation("org.jobrunr:jobrunr-spring-boot-3-starter:8.3.1")

    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")

    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.0")

    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core:11.19.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.19.0")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("io.jsonwebtoken:jjwt-impl:0.13.0")
    implementation("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("com.yubico:webauthn-server-core:2.8.0")
    implementation("com.yubico:yubico-util:2.8.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    implementation("org.apache.commons:commons-lang3")
    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.brotli:dec:0.1.2")

    implementation("commons-io:commons-io")

    implementation("org.jsoup:jsoup:1.21.2")
    implementation("io.minio:minio:8.6.0")

    implementation("com.twelvemonkeys.imageio:imageio-webp:3.13.0")
    implementation("org.webjars.npm:blurhash:2.0.5")

    implementation("org.webjars:webjars-locator-core:0.59")
    implementation("org.webjars.npm:sockjs-client:1.6.1")
    implementation("org.webjars:stomp-websocket:2.3.4")
    implementation("org.webjars:bootstrap:5.3.3")
    implementation("org.webjars.npm:bootstrap-icons:1.11.3")
    implementation("org.webjars.npm:chart.js:4.4.7")
    implementation("org.webjars:jquery:3.7.1")
    implementation("org.webjars.npm:sortablejs:1.15.6")
    implementation("org.webjars.npm:feather-icons:4.29.2")

    runtimeOnly("io.netty:netty-resolver-dns-native-macos") {
        artifact {
            classifier = "osx-aarch_64"
        }
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs = listOf(
        "-XX:+TieredCompilation",
        "-XX:TieredStopAtLevel=1",
        "-Dspring.jmx.enabled=false"
    )

    args("--spring.profiles.active=dev")
}
