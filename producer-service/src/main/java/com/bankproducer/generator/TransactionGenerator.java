package com.bankproducer.generator;

import com.bankproducer.model.TransactionMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Responsável por gerar dados simulados de transações PIX.
 *
 * Cada transação possui:
 * - transactionId único baseado em UUID
 * - timestamp exato do momento de criação
 * - senderBank configurado via variável de ambiente BANK_NAME
 * - receiverBank escolhido aleatoriamente entre os demais bancos
 * - contas numéricas simuladas
 * - valor positivo aleatório
 */
@Component
public class TransactionGenerator {

    private static final List<String> ALL_BANKS = List.of("BankA", "BankB", "BankC", "BankD");
    private static final Random RANDOM = new Random();

    @Value("${bank.name}")
    private String bankName;

    public TransactionMessage generate() {
        String transactionId = "TX-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();

        // Escolhe receiver diferente do sender
        List<String> otherBanks = ALL_BANKS.stream()
                .filter(b -> !b.equalsIgnoreCase(bankName))
                .toList();
        String receiverBank = otherBanks.get(RANDOM.nextInt(otherBanks.size()));

        String senderAccount = String.valueOf(10000 + RANDOM.nextInt(90000));
        String receiverAccount = String.valueOf(10000 + RANDOM.nextInt(90000));

        BigDecimal amount = BigDecimal.valueOf(10 + RANDOM.nextDouble() * 9990)
                .setScale(2, RoundingMode.HALF_UP);

        return new TransactionMessage(
                transactionId,
                LocalDateTime.now(),
                bankName,
                receiverBank,
                senderAccount,
                receiverAccount,
                amount
        );
    }
}
