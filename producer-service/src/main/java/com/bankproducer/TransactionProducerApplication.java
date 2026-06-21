package com.bankproducer;

import com.bankproducer.generator.TransactionGenerator;
import com.bankproducer.model.TransactionMessage;
import com.bankproducer.publisher.TransactionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do Producer Service.
 *
 * Implementa CommandLineRunner para iniciar o loop de publicação
 * imediatamente após o contexto Spring estar pronto.
 *
 * Comportamento:
 * 1. Conecta no RabbitMQ (gerenciado pelo Spring AMQP)
 * 2. Entra em loop: gera → publica → aguarda intervalo → repete
 * 3. Registra log no console a cada publicação
 */
@SpringBootApplication
public class TransactionProducerApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TransactionProducerApplication.class);

    private final TransactionGenerator generator;
    private final TransactionPublisher publisher;

    @Value("${bank.name}")
    private String bankName;

    @Value("${bank.publish-interval-ms}")
    private long publishIntervalMs;

    public TransactionProducerApplication(TransactionGenerator generator,
                                          TransactionPublisher publisher) {
        this.generator = generator;
        this.publisher = publisher;
    }

    public static void main(String[] args) {
        SpringApplication.run(TransactionProducerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("==> {} starting. Publishing every {}ms to RabbitMQ.", bankName, publishIntervalMs);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                TransactionMessage message = generator.generate();
                publisher.publish(message);
                Thread.sleep(publishIntervalMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("{} producer interrupted and stopped.", bankName);

            } catch (Exception e) {
                // Falha técnica (ex: conexão temporariamente indisponível).
                // Registra o erro e aguarda o intervalo antes de tentar novamente.
                log.error("{} failed to publish: {}. Retrying in {}ms...",
                        bankName, e.getMessage(), publishIntervalMs);
                try {
                    Thread.sleep(publishIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
