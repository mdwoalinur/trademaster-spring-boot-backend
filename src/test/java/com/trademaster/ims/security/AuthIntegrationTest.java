package com.trademaster.ims.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trademaster.ims.config.SecurityConfig;
import com.trademaster.ims.config.WebConfig;
import com.trademaster.ims.controller.AuthController;
import com.trademaster.ims.model.Role;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.AuditLogRepository;
import com.trademaster.ims.repository.RoleRepository;
import com.trademaster.ims.repository.UserRepository;
import com.trademaster.ims.service.AuditLogService;
import com.trademaster.ims.service.ForgotPasswordService;
import com.trademaster.ims.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Date;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Real HTTP, servlet error dispatches, provider, BCrypt and JWT; no database connection.
@SpringBootTest(classes = AuthIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.location=optional:classpath:/auth-test.properties",
                "jwt.expiration=60000"})
@ActiveProfiles("auth-integration")
public class AuthIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @Profile("auth-integration")
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
    @Import({SecurityConfig.class, WebConfig.class, AuthController.class, UserDetailsServiceImpl.class,
            JwtUtils.class, AuditLogService.class, RoleProbe.class})
    public static class TestApplication {
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("jwt.secret", () -> secret);
    }

    @RestController
    @Profile("auth-integration")
    @CrossOrigin(origins = "http://localhost:4200") // Existing controller annotations must merge with the global policy.
    static class RoleProbe {
        @GetMapping("/test/super-admin")
        @PreAuthorize("hasRole('SUPER_ADMIN')")
        public Map<String, Boolean> superAdminOnly() {
            return Map.of("allowed", true);
        }

        @RequestMapping("/test/cors-methods")
        public Map<String, Boolean> corsMethods() {
            return Map.of("allowed", true);
        }
    }

    @MockitoBean UserRepository users;
    @MockitoBean RoleRepository roles;
    @MockitoBean AuditLogRepository auditLogs;
    @MockitoBean ProfileService profiles;
    @MockitoBean ForgotPasswordService forgotPasswords;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired JwtUtils jwtUtils;
    @Autowired org.springframework.core.env.Environment environment;
    @Value("${local.server.port}") int port;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private User user;
    private String password;

    @BeforeEach
    void setUp() {
        password = UUID.randomUUID().toString();
        user = new User();
        user.setUserId(1L);
        user.setRoleId(1L);
        user.setUsername("admin");
        user.setIsActive(true);
        user.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(password));
        Role role = new Role();
        role.setRoleId(1L);
        role.setRoleName("SUPER_ADMIN");
        when(users.findByUsername("admin")).thenAnswer(invocation -> Optional.of(user));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(roles.findById(1L)).thenReturn(Optional.of(role));
        when(profiles.buildSafeProfile(user)).thenReturn(Map.of(
                "userId", 1L, "username", "admin", "roleName", "SUPER_ADMIN"));
    }

    @Test
    void validCredentialsReturnJwtAndProfileWithoutBearerToken() throws Exception {
        var response = login("admin", password);
        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        String token = body.path("token").asText();
        assertTrue(jwtUtils.validateJwtToken(token));
        assertEquals("admin", jwtUtils.getUserNameFromJwtToken(token));
        assertEquals("SUPER_ADMIN", body.path("roleName").asText());
        assertFalse(body.has("passwordHash"));
        assertNotNull(user.getLastLogin());
        verify(users).save(user);
        assertEquals(200, get("/api/auth/me", token).statusCode());
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("admin", password));
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN")));
    }

    @Test
    void incorrectPasswordReturnsSafeUnauthorizedJson() throws Exception {
        assertLoginFailure(login("admin", UUID.randomUUID().toString()));
        verify(users, never()).save(any());
    }

    @Test
    void unknownUsernameReturnsSameSafeUnauthorizedJson() throws Exception {
        assertLoginFailure(login("missing-user", password));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        user.setIsActive(false);
        assertLoginFailure(login("admin", password));
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        var response = get("/api/auth/me", null);
        assertEquals(401, response.statusCode());
        assertEquals("Authentication is required", json.readTree(response.body()).path("message").asText());
    }

    @Test
    void invalidJwtCannotAuthenticate() throws Exception {
        assertEquals(401, get("/api/auth/me", "invalid-token").statusCode());
    }

    @Test
    void serverFailureAfterAuthenticationIsNotMaskedByErrorDispatch() throws Exception {
        when(users.save(user)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Test database unavailable"));
        var response = login("admin", password);
        assertEquals(500, response.statusCode());
        assertFalse(response.body().contains("Test database unavailable"));
    }

    @Test
    void auditDatabaseFailureDoesNotHideLoginSuccessOrFailure() throws Exception {
        when(auditLogs.saveAndFlush(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("Test audit constraint failure"));
        assertLoginFailure(login("admin", UUID.randomUUID().toString()));
        assertEquals(200, login("admin", password).statusCode());
    }

    @Test
    void userLookupOutageReturnsSafeServiceUnavailable() throws Exception {
        when(users.findByUsername("admin")).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("Test database unavailable"));
        var response = login("admin", password);
        assertEquals(503, response.statusCode());
        assertEquals("Authentication service is unavailable. Please try again later.",
                json.readTree(response.body()).path("message").asText());
        assertFalse(response.body().contains("Test database unavailable"));
        verify(users, never()).save(any());
    }

    @Test
    void expiredJwtCannotAuthenticate() throws Exception {
        String token = Jwts.builder().setSubject("admin")
                .setExpiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor(environment.getRequiredProperty("jwt.secret").getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS512).compact();
        assertEquals(401, get("/api/auth/me", token).statusCode());
    }

    @Test
    void jwtWithWrongSignatureCannotAuthenticate() throws Exception {
        String token = Jwts.builder().setSubject("admin")
                .signWith(Keys.hmacShaKeyFor((UUID.randomUUID().toString() + UUID.randomUUID())
                        .getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512).compact();
        assertEquals(401, get("/api/auth/me", token).statusCode());
    }

    @Test
    void directErrorRequestRemainsProtected() throws Exception {
        assertEquals(401, get("/error", null).statusCode());
    }

    @Test
    void roleAuthorizationStillAllowsSuperAdminAndRejectsEmployee() throws Exception {
        String token = json.readTree(login("admin", password).body()).path("token").asText();
        assertEquals(200, get("/test/super-admin", token).statusCode());
        Role employee = new Role();
        employee.setRoleName("EMPLOYEE");
        when(roles.findById(1L)).thenReturn(Optional.of(employee));
        assertEquals(403, get("/test/super-admin", token).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://trademaster-angular-frontend.vercel.app", "http://localhost:4200"})
    void allowedOriginPreflightDoesNotRequireJwt(String origin) throws Exception {
        var loginPreflight = preflight("/api/auth/login", origin, "POST", "content-type");
        assertEquals(200, loginPreflight.statusCode());
        assertCorsAllowed(loginPreflight, origin);
        assertTrue(loginPreflight.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));

        var protectedPreflight = preflight("/api/auth/me", origin, "GET", "authorization,content-type");
        assertEquals(200, protectedPreflight.statusCode());
        assertCorsAllowed(protectedPreflight, origin);
        String allowedHeaders = protectedPreflight.headers().firstValue("Access-Control-Allow-Headers")
                .orElse("").toLowerCase(java.util.Locale.ROOT);
        assertTrue(allowedHeaders.contains("authorization"));
        assertTrue(allowedHeaders.contains("content-type"));
        verify(users, never()).findByUsername(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://trademaster-angular-frontend.vercel.app", "http://localhost:4200"})
    void allowedOriginCanLogInAndMakeJwtAuthenticatedRequests(String origin) throws Exception {
        var loginResponse = login("admin", password, origin);
        assertEquals(200, loginResponse.statusCode());
        assertCorsAllowed(loginResponse, origin);
        String token = json.readTree(loginResponse.body()).path("token").asText();
        assertTrue(jwtUtils.validateJwtToken(token));

        var profileResponse = get("/api/auth/me", token, origin);
        assertEquals(200, profileResponse.statusCode());
        assertCorsAllowed(profileResponse, origin);
        assertEquals("admin", json.readTree(profileResponse.body()).path("username").asText());

        var annotatedControllerResponse = get("/test/super-admin", token, origin);
        assertEquals(200, annotatedControllerResponse.statusCode());
        assertCorsAllowed(annotatedControllerResponse, origin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://trademaster-angular-frontend.vercel.app", "http://localhost:4200"})
    void allowedOriginStillRequiresValidCredentialsAndJwt(String origin) throws Exception {
        var badLogin = login("admin", UUID.randomUUID().toString(), origin);
        assertLoginFailure(badLogin);
        assertCorsAllowed(badLogin, origin);
        for (String token : new String[]{null, "invalid-token"}) {
            var response = get("/api/auth/me", token, origin);
            assertEquals(401, response.statusCode());
            assertCorsAllowed(response, origin);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "https://trademaster-angular-frontend.vercel.app.evil.example",
            "https://preview-trademaster-angular-frontend.vercel.app", "null"})
    void unlistedOriginsAreRejectedForPreflightAndAuthenticatedRequests(String origin) throws Exception {
        var options = preflight("/api/auth/me", origin, "GET", "authorization");
        assertEquals(403, options.statusCode());
        assertTrue(options.headers().firstValue("Access-Control-Allow-Origin").isEmpty());

        String token = json.readTree(login("admin", password).body()).path("token").asText();
        var actual = get("/api/auth/me", token, origin);
        assertEquals(403, actual.statusCode());
        assertTrue(actual.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"})
    void productionPreflightPreservesSupportedHttpMethods(String method) throws Exception {
        String origin = "https://trademaster-angular-frontend.vercel.app";
        var response = preflight("/test/cors-methods", origin, method, "authorization,content-type,x-requested-with");
        assertEquals(200, response.statusCode());
        assertCorsAllowed(response, origin);
        assertTrue(java.util.Arrays.asList(response.headers().firstValue("Access-Control-Allow-Methods")
                .orElse("").split(",\\s*")).contains(method));
    }

    private void assertCorsAllowed(HttpResponse<String> response, String origin) {
        assertEquals(java.util.List.of(origin), response.headers().allValues("Access-Control-Allow-Origin"));
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElse(""));
    }

    private HttpResponse<String> preflight(String path, String origin, String method, String headers) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", headers)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertLoginFailure(HttpResponse<String> response) throws Exception {
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));
        assertEquals("Invalid username or password", json.readTree(response.body()).path("message").asText());
    }

    private HttpResponse<String> login(String username, String submittedPassword) throws Exception {
        return login(username, submittedPassword, null);
    }

    private HttpResponse<String> login(String username, String submittedPassword, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(uri("/api/auth/login")).header("Content-Type", "application/json");
        if (origin != null) builder.header("Origin", origin);
        return http.send(builder
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("username", username, "password", submittedPassword))))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return get(path, token, null);
    }

    private HttpResponse<String> get(String path, String token, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (origin != null) builder.header("Origin", origin);
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
