package com.auditlogging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ErrorHandler;

/**
 * Configuração de mensageria do Audit Logging Service.
 *
 * Topologia declarada aqui:
 *
 *   [pix.transactions.exchange]  --routing-key: pix.transaction.created-->  [audit.logging.queue]
 *   [audit.logging.queue]        --x-dead-letter-exchange-->                 [pix.transactions.dlx]
 *   [pix.transactions.dlx]       --routing-key: audit.logging.failed-->      [audit.logging.dlq]
 *
 * O consumer declara toda a topologia para garantir que ela exista
 * antes de começar a consumir mensagens.
 */
@Configuration
public class RabbitMqConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue}")
    private String queue;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.dlx}")
    private String dlx;

    @Value("${rabbitmq.dlq}")
    private String dlq;

    @Value("${rabbitmq.dl-routing-key}")
    private String dlRoutingKey;

    // ── Exchanges ────────────────────────────────────────────────────────────

    @Bean
    public DirectExchange pixTransactionsExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlx, true, false);
    }

    // ── Queues ───────────────────────────────────────────────────────────────

    /**
     * Fila principal configurada com dead-letter:
     * mensagens rejeitadas (NACK sem requeue) vão para pix.transactions.dlx
     * com a routing key audit.logging.failed.
     */
    @Bean
    public Queue auditLoggingQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", dlRoutingKey)
                .build();
    }

    /** Fila de dead-letter: armazena mensagens que não puderam ser processadas. */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlq).build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding auditQueueBinding() {
        return BindingBuilder.bind(auditLoggingQueue())
                .to(pixTransactionsExchange())
                .with(routingKey);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(dlRoutingKey);
    }

    // ── Conversor e Container Factory ────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * Configura o container de listeners com:
     * - ACK manual (acknowledge-mode também setado no application.yml)
     * - ConditionalRejectingErrorHandler: erros de desserialização JSON
     *   são rejeitados sem requeue, enviando automaticamente para a DLQ.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {

        ErrorHandler errorHandler = new ConditionalRejectingErrorHandler(
                new ConditionalRejectingErrorHandler.DefaultExceptionStrategy());

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setErrorHandler(errorHandler);
        return factory;
    }
}
