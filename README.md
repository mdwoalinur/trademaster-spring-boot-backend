# TradeMaster Spring Boot Backend

Spring Boot REST backend for TradeMaster Inventory and Stock Management.

## Requirements

- Java 17
- MySQL with database `trademaster` (or a configured database URL)
- Maven 3.9+

## Required environment variables

```text
TRADEMASTER_DB_URL=jdbc:mysql://localhost:3306/trademaster
TRADEMASTER_DB_USERNAME=root
TRADEMASTER_DB_PASSWORD=<database password>
TRADEMASTER_JWT_SECRET=<random secret containing at least 64 characters for HS512>
TRADEMASTER_JWT_SECRET=<long random HS512 secret>
```

Optional mail variables:

```text
TRADEMASTER_EMAIL_ENABLED=false
TRADEMASTER_MAIL_USERNAME=
TRADEMASTER_MAIL_PASSWORD=
TRADEMASTER_EMAIL_FROM=
TRADEMASTER_MAIL_DEBUG=false
```

Use a Gmail App Password when Gmail SMTP is enabled. Never commit credentials.

## Build and run

```bash
./mvnw clean package
java -jar target/trademaster_ims-0.0.1-SNAPSHOT.jar
```

The API listens on port 8080 by default. Angular development CORS origin is `http://localhost:4200`.

## Architecture

Controllers expose `/api` endpoints, services own transactional business rules, Spring Data repositories persist MySQL entities, Spring Security validates stateless JWTs, AOP records audit events, and Flyway-style SQL files document schema migrations. Approval and posting are separate payment states; only posted payments affect ledgers and balances.

## Upload storage

Files use the project-relative `uploads/` root:

```text
uploads/products
uploads/customers
uploads/suppliers
uploads/product-variations
uploads/profiles
uploads/receipts
uploads/payment-attachments
```

Directories are created automatically. Public display images have explicit static mappings. Receipts and payment attachments require authenticated download endpoints.

## Security

- Login and forgot-password endpoints are public; business APIs require JWT authentication.
- User and role administration require ADMIN or SUPER_ADMIN.
- Disabled users cannot authenticate.
- Password hashes are write-only in JSON and never returned.
- SMTP, database, and JWT secrets are environment-driven.
- Audit sanitization excludes passwords, tokens, OTPs, and secret fields.

## Known limitations

- The automated test suite currently contains only a Spring context test.
- Maven Central availability is required the first time Surefire provider artifacts are resolved.
- Email delivery depends on external SMTP configuration.
- Full financial workflow validation requires isolated test data and should not be run against production data.

See `THIRD_PARTY_NOTICES.md` for direct dependency licensing.
