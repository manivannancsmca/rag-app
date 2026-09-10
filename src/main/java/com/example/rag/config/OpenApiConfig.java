package com.example.rag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RAG Document Assistant API")
                        .description("""
                                Retrieval-Augmented Generation API.

                                **Upload** PDF documents, then **ask questions**
                                and receive answers grounded in the uploaded content.

                                ### Workflow
                                1. `POST /api/documents/upload` — upload a PDF
                                2. `GET /api/documents/{id}/status` — poll until COMPLETED
                                3. `POST /api/chat` — ask a question
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RAG Team")
                                .email("team@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development")));
    }
}