package com.auditlogging.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Contrato de mensagem de transação PIX consumido pelo Audit Logging Service.
 * Deve ser idêntico ao publicado pelo producer-service (versão 1.0).
 */
public record TransactionMessage(
        @JsonProperty("transactionId")   String transactionId,
        @JsonProperty("timestamp")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp,
        @JsonProperty("senderBank")      String senderBank,
        @JsonProperty("receiverBank")    String receiverBank,
        @JsonProperty("senderAccount")   String senderAccount,
        @JsonProperty("receiverAccount") String receiverAccount,
        @JsonProperty("amount")          BigDecimal amount
) {}
