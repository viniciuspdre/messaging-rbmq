# Central Bank Messaging System

Sistema distribuído local inspirado no ecossistema PIX, implementado como trabalho acadêmico para demonstrar conceitos de mensageria assíncrona, producers, consumers, broker de mensagens e auditoria de eventos.

---

## Objetivo Acadêmico

Demonstrar na prática os seguintes conceitos:

- **Producers independentes**: quatro bancos publicam eventos de transação PIX de forma autônoma
- **Message broker**: RabbitMQ roteia mensagens entre producers e consumer
- **Consumer assíncrono**: Audit Logging Service consome e persiste cada transação
- **Comunicação por eventos**: nenhum serviço se comunica diretamente — tudo passa pelo broker
- **ACK manual**: o consumer confirma apenas após gravar com sucesso no arquivo
- **Dead-letter queue**: mensagens inválidas ou não processáveis são isoladas na DLQ
- **Execução local com Docker Compose e Makefile**

> Este projeto **não implementa** validação de saldo, débito, crédito ou liquidação financeira.  
> As transações são eventos simulados para fins de demonstração de mensageria.

---

## Tecnologias

| Tecnologia | Versão | Papel |
|---|---|---|
| Java | 21 | Linguagem |
| Spring Boot | 3.3 | Framework |
| Spring AMQP | 3.3 | Integração RabbitMQ |
| RabbitMQ | 3-management | Message broker |
| Docker / Docker Compose | — | Containerização |
| Maven | 3.9 | Build |
| Makefile | — | Orquestração de comandos |

---

## Arquitetura

### Visão de Contexto (C4 — Nível 1)

```
[Bank A] ──┐
[Bank B] ──┤──▶ [Central Bank Messaging System] ──▶ [Audit Logging Service] ──▶ [audit.log]
[Bank C] ──┤
[Bank D] ──┘
```

### Visão de Containers (C4 — Nível 2)

```
[bank-a Java App] ──┐
[bank-b Java App] ──┤──▶ [RabbitMQ Broker]
[bank-c Java App] ──┤         │
[bank-d Java App] ──┘         │
                              ▼
                    [audit.logging.queue]
                              │
                              ▼
                 [audit-logging-service Java App]
                              │
                              ▼
                         [audit.log]
```

### Topologia de Mensageria

| Recurso | Nome | Finalidade |
|---|---|---|
| Exchange principal | `pix.transactions.exchange` | Recebe eventos de transação PIX |
| Queue principal | `audit.logging.queue` | Entrega mensagens ao Audit Logging Service |
| Routing key principal | `pix.transaction.created` | Roteamento de eventos válidos |
| Dead-letter exchange | `pix.transactions.dlx` | Recebe mensagens rejeitadas |
| Dead-letter queue | `audit.logging.dlq` | Armazena mensagens que falharam |
| Dead-letter routing key | `audit.logging.failed` | Roteamento das mensagens rejeitadas |

**Fluxo normal:**

1. Banco gera uma transação PIX simulada
2. Banco publica a mensagem JSON no `pix.transactions.exchange`
3. RabbitMQ roteia para `audit.logging.queue` via routing key `pix.transaction.created`
4. Audit Logging Service consome a mensagem
5. Audit Logging Service escreve uma linha no `audit.log`
6. Consumer envia ACK confirmando o processamento

**Fluxo de falha:**

- JSON malformado → rejeitado pelo deserializador → NACK sem requeue → DLQ
- Dados inválidos (valor negativo, campos ausentes) → validação falha → NACK sem requeue → DLQ
- Falha de I/O temporária → NACK com requeue → mensagem volta para a fila

---

## Contrato da Mensagem

Cada transação segue o formato JSON abaixo:

```json
{
  "transactionId": "TX-ABC123456789",
  "timestamp": "2026-06-01T10:15:30",
  "senderBank": "BankA",
  "receiverBank": "BankB",
  "senderAccount": "12345",
  "receiverAccount": "98765",
  "amount": 1500.50
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `transactionId` | string | ✅ | Identificador único (UUID-based) |
| `timestamp` | string ISO-8601 | ✅ | Momento de criação da transação |
| `senderBank` | string | ✅ | Banco de origem |
| `receiverBank` | string | ✅ | Banco de destino |
| `senderAccount` | string | ✅ | Conta de origem |
| `receiverAccount` | string | ✅ | Conta de destino |
| `amount` | number | ✅ | Valor positivo da transação |

---

## Como Rodar

### Pré-requisitos

- Docker Desktop instalado e em execução
- `make` disponível (Git Bash, WSL ou Make for Windows)

### Execução Completa (recomendado)

```bash
make up
```

Resultado esperado:
- RabbitMQ sobe e fica disponível em `localhost:15672`
- Os quatro bancos começam a publicar mensagens a cada 2,5 segundos
- Audit Logging Service consome as mensagens
- `logs/audit.log` começa a receber entradas

### Execução por Partes

**Terminal 1 — Sobe apenas o broker:**
```bash
make broker
```

**Terminal 2 — Sobe o consumer:**
```bash
make audit
```

**Terminais 3–6 — Sobe cada banco individualmente:**
```bash
make bank-a
make bank-b
make bank-c
make bank-d
```

**Ou todos os bancos de uma vez:**
```bash
make banks
```

---

## Como Acessar o RabbitMQ Management UI

Abra no navegador:

```
http://localhost:15672
```

Credenciais:
- **Usuário:** `guest`
- **Senha:** `guest`

No painel, acesse **Queues** para visualizar:
- `audit.logging.queue` — fila principal com mensagens em processamento
- `audit.logging.dlq` — dead-letter queue com mensagens rejeitadas

---

## Como Visualizar o audit.log

```bash
make logs
```

Ou diretamente:

```bash
tail -f logs/audit.log
```

No Windows sem terminal Unix, use:
```powershell
Get-Content -Wait logs\audit.log
```

Formato de cada linha:

```
[2026-06-01 10:15:30] TX-ABC123456789 | BankA | BankB | 1500.50
```

---

## Como Inspecionar as Filas

```bash
make queues
```

Exibe nome da fila, total de mensagens, mensagens prontas e mensagens sem ACK.

---

## Como Parar

```bash
make down
```

## Como Limpar o Ambiente

Remove containers, redes e volumes:

```bash
make clean
```

---

## ACK Manual e Reprocessamento

O Audit Logging Service usa **ACK manual**. Isso significa:

1. Quando o RabbitMQ entrega uma mensagem, ela fica no estado **unacknowledged**
2. O consumer processa a mensagem (escreve no arquivo)
3. **Somente após a escrita bem-sucedida** o consumer envia ACK
4. Se o consumer cair antes do ACK, o RabbitMQ reentrega a mensagem quando o consumer reconectar

Isso garante **at-least-once delivery**: toda mensagem será processada pelo menos uma vez.

---

## Dead-Letter Queue (DLQ)

Mensagens que não podem ser processadas são enviadas para a `audit.logging.dlq`.

Casos que disparam envio para DLQ:
- JSON malformado (não deserializável)
- Campos obrigatórios ausentes (`transactionId`, `senderBank`, `receiverBank`, `timestamp`, `amount`)
- Valor da transação inválido (negativo ou zero)

**Como visualizar a DLQ:**

1. Acesse `http://localhost:15672`
2. Clique em **Queues**
3. Selecione `audit.logging.dlq`
4. Use **Get messages** para inspecionar o conteúdo

Ou via terminal:
```bash
make queues
```

---

## Limitações Conhecidas

| Limitação | Motivo |
|---|---|
| Duplicidade possível no `audit.log` | Semântica at-least-once: se o consumer gravar e cair antes do ACK, a mensagem é reentregue. Tolerável neste contexto acadêmico. |
| Sem ordenação global garantida | Quatro producers publicam concorrentemente. O RabbitMQ não garante ordem entre mensagens de producers diferentes. |
| Sem validação de saldo | Fora do escopo desta versão. |
| `make logs` requer terminal Unix | No Windows, use `Get-Content -Wait logs\audit.log` no PowerShell. |

---

## Evoluções Futuras

- **Transaction Validation Service**: validar saldo antes de aprovar a transação
- **Fraud Monitoring Service**: detectar padrões suspeitos em tempo real
- **Tax Detection Service**: calcular impostos por transação
- **Retry policy avançada**: fila de retry com TTL antes da DLQ
- **Idempotência com banco de dados**: evitar duplicidade com unique constraint em `transactionId`
- **Persistência em PostgreSQL**: substituir o arquivo de log por banco relacional
- **Observabilidade**: Prometheus + Grafana para métricas de mensagens e throughput
- **Kubernetes local**: migrar o Docker Compose para kind ou minikube
- **GitOps**: Argo CD para deploy automatizado

---

## Estrutura do Repositório

```
messaging-rbmq/
  README.md                          # Este arquivo
  Makefile                           # Comandos de orquestração
  docker-compose.yml                 # Definição dos containers
  .gitignore
  producer-service/                  # Código dos bancos produtores
    Dockerfile
    pom.xml
    src/main/java/com/bankproducer/
      TransactionProducerApplication.java
      config/RabbitMqConfig.java
      model/TransactionMessage.java
      generator/TransactionGenerator.java
      publisher/TransactionPublisher.java
    src/main/resources/application.yml
  audit-logging-service/             # Código do consumer
    Dockerfile
    pom.xml
    src/main/java/com/auditlogging/
      AuditLoggingApplication.java
      config/RabbitMqConfig.java
      model/TransactionMessage.java
      consumer/AuditLogConsumer.java
      writer/AuditLogWriter.java
    src/main/resources/application.yml
  logs/
    audit.log                        # Gerado em runtime (volume Docker)
```
