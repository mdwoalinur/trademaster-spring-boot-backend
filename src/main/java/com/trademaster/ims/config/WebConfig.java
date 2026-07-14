package com.trademaster.ims.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path productUploadDir = Paths.get("uploads", "products").toAbsolutePath().normalize();
    private final Path customerUploadDir = Paths.get("uploads", "customers").toAbsolutePath().normalize();
    private final Path supplierUploadDir = Paths.get("uploads", "suppliers").toAbsolutePath().normalize();
    private final Path productVariationUploadDir = Paths.get("uploads", "product-variations").toAbsolutePath().normalize();

    // CORS Configuration (existing)
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    // Static Resource Handler for uploaded files
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations(productUploadDir.toUri().toString())
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/customers/**")
                .addResourceLocations(customerUploadDir.toUri().toString())
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/suppliers/**")
                .addResourceLocations(supplierUploadDir.toUri().toString())
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/product-variations/**")
                .addResourceLocations(productVariationUploadDir.toUri().toString())
                .setCachePeriod(3600);

    }
}
