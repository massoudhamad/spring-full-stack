package tz.co.hmy.pis.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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
                .contact(new Contact().name("HM&Y Technologies Ltd")));
    }
}
