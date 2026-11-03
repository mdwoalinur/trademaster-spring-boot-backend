package com.trademaster.ims.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tradeMasterOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TradeMaster Inventory Management API")
                        .version("0.0.1-SNAPSHOT")
                        .description("REST APIs for inventory, sales, purchases, payments, reporting and administration. "
                                + "Log in with POST /api/auth/login, copy the token from the response, then paste "
                                + "the token into Authorize without the Bearer prefix. "
                                + "Protected operations require a valid JWT and may also require a permitted user role."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT returned by POST /api/auth/login. Enter the token without the Bearer prefix.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
