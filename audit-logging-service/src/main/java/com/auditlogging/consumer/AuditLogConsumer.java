package com.auditlogging.consumer;

import com.auditlogging.model.TransactionMessage;
import com.auditlogging.writer.AuditLogWriter;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumer responsável por processar mensagens da fila audit.logging.queue.
 *
 * Estratégia de ACK/NACK (at-least-once delivery):
 *
 *   ✅ Escrita bem-sucedida         → basicAck   (mensagem confirmada)
 *   ⚠️ Falha de I/O temporária      → basicNack com requeue=true  (tenta novamente)
 *   ❌ Dados inválidos               → basicNack com requeue=false (vai para DLQ)
 *
 * JSON malformado é tratado antes de chegar aqui pelo ConditionalRejectingErrorHandler
 * configurado em RabbitMqConfig, que também envia para DLQ sem requeue.
 *
 * Limitação conhecida (at-least-once):
 * Se o consumer escrever no arquivo e cair antes de enviar ACK,
 * a mensagem será reentregue e uma entrada duplicada pode aparecer no audit.log.
 * Documentado no README como limitação da versão atual.
 */
@Component
public class AuditLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditLogConsumer.class);

    private final AuditLogWriter writer;

    public AuditLogConsumer(AuditLogWriter writer) {
        this.writer = writer;
    }

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void consume(TransactionMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received transaction {} from {} to {} amount={}",
                message.transactionId(),
                message.senderBank(),
                message.receiverBank(),
                message.amount());

        try {
            // 1. Validação de negócio básica
            validateMessage(message);

            // 2. Efeito colateral: escrita no arquivo
            writer.write(
                    message.transactionId(),
                    message.timestamp(),
                    message.senderBank(),
                    message.receiverBank(),
                    message.amount()
            );

            // 3. ACK apenas após escrita bem-sucedida
            channel.basicAck(deliveryTag, false);
            log.info("ACK sent for transaction {}", message.transactionId());

        } catch (IllegalArgumentException e) {
            // Falha de negócio: dados inválidos → DLQ, sem requeue
            log.error("Invalid message sent to DLQ. transactionId={} reason={}",
                    message.transactionId(), e.getMessage());
            channel.basicNack(deliveryTag, false, false);

        } catch (IOException e) {
            // Falha técnica temporária → requeue para nova tentativa
            log.error("I/O failure writing audit log. transactionId={} error={} — requeueing.",
                    message.transactionId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * Valida campos obrigatórios e regras de negócio mínimas.
     * Falha aqui → mensagem vai para DLQ.
     */
    private void validateMessage(TransactionMessage message) {
        if (message.transactionId() == null || message.transactionId().isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
        if (message.senderBank() == null || message.senderBank().isBlank()) {
            throw new IllegalArgumentException("senderBank is required");
        }
        if (message.receiverBank() == null || message.receiverBank().isBlank()) {
            throw new IllegalArgumentException("receiverBank is required");
        }
        if (message.timestamp() == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        if (message.amount() == null || message.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be a positive value");
        }
    }
}
