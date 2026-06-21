package com.auditlogging.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Responsável por formatar e escrever entradas no arquivo audit.log.
 *
 * Formato de cada linha:
 *   [yyyy-MM-dd HH:mm:ss] TRANSACTION_ID | SENDER | RECEIVER | AMOUNT
 *
 * Exemplo:
 *   [2026-06-01 10:15:30] TX-ABC123456789 | BankA | BankB | 1500.50
 *
 * O arquivo é aberto em modo append (true) para nunca sobrescrever
 * entradas anteriores.
 */
@Component
public class AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogWriter.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${audit.log.path:/app/logs/audit.log}")
    private String auditLogPath;

    /**
     * Escreve uma linha de auditoria no arquivo.
     *
     * @throws IOException se houver falha de I/O — o consumer deve tratar como falha técnica
     */
    public void write(String transactionId,
                      LocalDateTime timestamp,
                      String senderBank,
                      String receiverBank,
                      BigDecimal amount) throws IOException {

        String line = String.format("[%s] %s | %s | %s | %.2f%n",
                timestamp.format(FORMATTER),
                transactionId,
                senderBank,
                receiverBank,
                amount);

        // Garante que o diretório pai existe antes de escrever
        Path path = Path.of(auditLogPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        // Modo append=true: nunca sobrescreve entradas anteriores
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(auditLogPath, true))) {
            writer.write(line);
        }

        log.info("Written to audit.log: {}", line.trim());
    }
}
