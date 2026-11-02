package com.trademaster.ims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.trademaster.ims.config.NotificationSchemaFixer;
import com.trademaster.ims.config.PaymentSchemaFixer;
import com.trademaster.ims.config.StockMovementSchemaFixer;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.trademaster.ims.model.Role;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.RoleRepository;
import com.trademaster.ims.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TrademasterImsApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.config.location=optional:classpath:/startup-test.properties",
		"spring.datasource.url=jdbc:h2:mem:startup;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
		"spring.mail.host=localhost",
		"app.default-admin.enabled=false",
		"app.email.enabled=false",
		"app.upload.root=target/test-uploads",
		"file.upload-dir=target/test-uploads",
		"jwt.expiration=60000"
})
@MockitoBean(types = {NotificationSchemaFixer.class, PaymentSchemaFixer.class, StockMovementSchemaFixer.class})
class TrademasterImsApplicationTests {
	@Autowired UserRepository users;
	@Autowired RoleRepository roles;
	@Autowired PasswordEncoder encoder;
	@Value("${local.server.port}") int port;
	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		String secret = UUID.randomUUID().toString() + UUID.randomUUID();
		registry.add("jwt.secret", () -> secret);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void persistedAdminCanLogInWithRealRepositoriesAndProfile() throws Exception {
		Role role = new Role();
		role.setRoleName("SUPER_ADMIN");
		role = roles.saveAndFlush(role);
		String password = UUID.randomUUID().toString();
		User user = new User();
		user.setUsername("test-admin");
		user.setFullName("Test Administrator");
		user.setRoleId(role.getRoleId());
		user.setPasswordHash(encoder.encode(password));
		user = users.saveAndFlush(user);
		ObjectMapper json = new ObjectMapper();
		HttpClient client = HttpClient.newHttpClient();
		String origin = "https://trademaster-angular-frontend.vercel.app";
		var preflight = client.send(HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + "/api/categories"))
				.header("Origin", origin)
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization,content-type")
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, preflight.statusCode());
		assertEquals(origin, preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
		var response = client.send(HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + "/api/auth/login"))
				.header("Content-Type", "application/json")
				.header("Origin", origin)
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
						Map.of("username", user.getUsername(), "password", password))))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
		var body = json.readTree(response.body());
		assertEquals(user.getUserId(), body.path("userId").longValue());
		assertEquals("SUPER_ADMIN", body.path("roleName").asText());
		assertEquals("Test Administrator", body.path("fullName").asText());
		assertTrue(body.path("isActive").asBoolean());
		assertFalse(body.path("token").asText().isBlank());
		assertFalse(body.has("passwordHash"));
		assertNotNull(users.findById(user.getUserId()).orElseThrow().getLastLogin());
		var categories = client.send(HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + "/api/categories"))
				.header("Origin", origin)
				.header("Authorization", "Bearer " + body.path("token").asText())
				.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, categories.statusCode());
		assertEquals(origin, categories.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
	}

}
