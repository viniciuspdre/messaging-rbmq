package com.auditlogging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do Audit Logging Service.
 *
 * O serviço sobe como uma aplicação Spring sem servidor web.
 * O @RabbitListener em AuditLogConsumer é ativado automaticamente
 * após o contexto Spring estar pronto.
 */
@SpringBootApplication
public class AuditLoggingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditLoggingApplication.class, args);
    }
}
