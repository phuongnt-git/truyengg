plugins {
    java
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.graalvm.buildtools.native") version "0.9.28"
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
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-devtools")

    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5")
    implementation("org.jobrunr:jobrunr-spring-boot-3-starter:8.3.1")

    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core:11.19.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.19.0")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("io.jsonwebtoken:jjwt-impl:0.13.0")
    implementation("io.jsonwebtoken:jjwt-jackson:0.13.0")
    
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

    runtimeOnly("io.netty:netty-resolver-dns-native-macos") {
        artifact {
            classifier = "osx-aarch_64"
        }
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
