package com.cms0057.priorauth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${cms0057.compliance.payer-name}")
    private String payerName;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:9000}")
    private String issuerUri;

    @Bean
    public OpenAPI cms0057OpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CMS-0057-F Prior Authorization API")
                .version("1.0.0")
                .description("""
                    FHIR R4 prior authorization service implementing **CMS-0057-F** \
                    (Advancing Interoperability and Improving Prior Authorization Processes).

                    Implements three Da Vinci implementation guides:
                    - **CRD** (Coverage Requirements Discovery) — CDS Hooks 2.0
                    - **DTR** (Documentation Templates and Rules) — FHIR Questionnaire/QuestionnaireResponse
                    - **PAS** (Prior Authorization Support) — FHIR Claim $submit / $inquire

                    **CMS-0057-F response time mandates:**
                    - Urgent (expedited): ≤ 72 hours
                    - Standard: ≤ 7 calendar days
                    """)
                .contact(new Contact()
                    .name(payerName)
                    .url("https://github.com/bhaskarkollu/cms0057"))
                .license(new License()
                    .name("HL7 FHIR License")
                    .url("https://www.hl7.org/fhir/license.html")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local development"),
                new Server().url("http://cms0057-prior-auth:8080").description("Docker Compose")))
            .tags(List.of(
                new Tag().name("CRD")
                    .description("Coverage Requirements Discovery — CDS Hooks endpoints. " +
                        "Called by the EHR when a clinician places an order.")
                    .externalDocs(new ExternalDocumentation()
                        .description("Da Vinci CRD IG")
                        .url("https://hl7.org/fhir/us/davinci-crd")),
                new Tag().name("DTR")
                    .description("Documentation Templates and Rules — FHIR Questionnaire endpoints. " +
                        "Used by the SMART on FHIR DTR app to collect clinical documentation.")
                    .externalDocs(new ExternalDocumentation()
                        .description("Da Vinci DTR IG")
                        .url("https://hl7.org/fhir/us/davinci-dtr")),
                new Tag().name("PAS")
                    .description("Prior Authorization Support — FHIR Claim $submit and $inquire operations. " +
                        "Enforces 72-hour urgent and 7-day standard SLA per CMS-0057-F.")
                    .externalDocs(new ExternalDocumentation()
                        .description("Da Vinci PAS IG")
                        .url("https://hl7.org/fhir/us/davinci-pas")),
                new Tag().name("FHIR")
                    .description("FHIR server endpoints (CapabilityStatement, metadata)")))
            .components(new Components()
                .addSecuritySchemes("SMART-on-FHIR", new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .description("SMART on FHIR — OAuth2 authorization code flow with PKCE")
                    .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                            .authorizationUrl(issuerUri + "/authorize")
                            .tokenUrl(issuerUri + "/token"))))
                .addSecuritySchemes("BearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT issued by SMART on FHIR authorization server")))
            .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }
}
