package com.bankproducer.publisher;

import com.bankproducer.model.TransactionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Responsável por publicar transações PIX no RabbitMQ.
 *
 * Publica no exchange principal usando a routing key configurada.
 * O RabbitMQ roteia a mensagem para audit.logging.queue.
 */
@Component
public class TransactionPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    public TransactionPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publica a mensagem no exchange e registra o evento no console.
     *
     * @param message transação PIX gerada
     */
    public void publish(TransactionMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.info("{} published transaction {} to {} amount={}",
                message.senderBank(),
                message.transactionId(),
                message.receiverBank(),
                message.amount());
    }
}
