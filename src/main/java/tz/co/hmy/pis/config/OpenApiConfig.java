package tz.co.hmy.pis.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI procurementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Procurement Information System API")
                .version("v1")
                .description("Suppliers, requisitions and purchase orders.")
                .contact(new Contact().name("HM&Y Technologies Ltd")))
                // Adds the Authorize button to Swagger UI and sends Basic credentials on every call.
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
