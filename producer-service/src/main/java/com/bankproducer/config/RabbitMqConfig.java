package com.bankproducer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração RabbitMQ do producer.
 *
 * O producer declara apenas o exchange. As filas e bindings são
 * responsabilidade do consumer (audit-logging-service), conforme
 * a separação de responsabilidades de topologia em sistemas event-driven.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "pix.transactions.exchange";

    /**
     * Exchange principal para eventos de transação PIX.
     * Durable=true garante que o exchange sobrevive a reinícios do broker.
     */
    @Bean
    public DirectExchange pixTransactionsExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /**
     * Conversor Jackson configurado com suporte a Java 8 Date/Time API.
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * RabbitTemplate configurado com o conversor Jackson para serialização JSON automática.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
