package com.fandrops.user.infrastructure.k6;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Date;

/**
 * k6 부하 테스트용 JWT 사전 생성 유틸리티.
 * JWT_SECRET 환경변수 주입 후 1회 실행 → infra/k6/seed/tokens.csv 생성.
 * tokens.csv는 .gitignore 등록 완료 — 실행 후 S3 업로드, 로컬 파일 삭제 권장.
 *
 * 실행 방법 (IntelliJ):
 *   Run Configuration → Environment variables → JWT_SECRET=<base64_secret>
 * 실행 방법 (CLI):
 *   JWT_SECRET=<base64_secret> ./gradlew :modules:user:user-infrastructure:test \
 *     --tests "com.fandrops.user.infrastructure.k6.JwtGeneratorTest"
 */
class JwtGeneratorTest {

    private static final int FAN_ID_COUNT = 2100;
    private static final long EXPIRE_MILLIS = 86400L * 1000 * 7; // 7일

    @Test
    void generateTokensForK6() throws Exception {
        String base64Secret = System.getenv("JWT_SECRET");
        Assumptions.assumeTrue(base64Secret != null && !base64Secret.isBlank(),
                "JWT_SECRET 환경변수 미설정 — 수동 실행 전용, CI skip");

        SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));

        // Gradle 실행(모듈 디렉터리 기준)과 IntelliJ 실행(프로젝트 루트 기준) 양쪽 대응
        Path workDir = Path.of(System.getProperty("user.dir"));
        Path outputPath = workDir.resolve("infra/k6/seed").toFile().exists()
                ? workDir.resolve("infra/k6/seed/tokens.csv")
                : workDir.resolve("../../../infra/k6/seed/tokens.csv").normalize();

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath.toFile()))) {
            writer.println("fanId,token");
            for (int i = 1; i <= FAN_ID_COUNT; i++) {
                String token = Jwts.builder()
                        .subject(String.valueOf(i))
                        .claim("role", "FAN")
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + EXPIRE_MILLIS))
                        .signWith(secretKey)
                        .compact();
                writer.println(i + "," + token);
            }
        }

        System.out.println("tokens.csv 생성 완료: " + outputPath.toAbsolutePath());
        System.out.println("총 " + FAN_ID_COUNT + "개 토큰 생성됨");
    }
}