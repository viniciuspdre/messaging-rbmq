.PHONY: up down broker audit bank-a bank-b bank-c bank-d banks logs queues clean

## Sobe todo o ambiente (RabbitMQ + todos os bancos + audit service)
up:
	docker compose up --build

## Para todos os containers
down:
	docker compose down

## Sobe apenas o RabbitMQ
broker:
	docker compose up rabbitmq

## Sobe apenas o Audit Logging Service
audit:
	docker compose up --build audit-logging-service

## Sobe apenas o Bank A
bank-a:
	docker compose up --build bank-a

## Sobe apenas o Bank B
bank-b:
	docker compose up --build bank-b

## Sobe apenas o Bank C
bank-c:
	docker compose up --build bank-c

## Sobe apenas o Bank D
bank-d:
	docker compose up --build bank-d

## Sobe todos os bancos produtores
banks:
	docker compose up --build bank-a bank-b bank-c bank-d

## Acompanha o audit.log em tempo real
logs:
	tail -f logs/audit.log

## Lista as filas do RabbitMQ e seus contadores (inclui DLQ)
queues:
	docker compose exec rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged

## Para containers e remove volumes
clean:
	docker compose down -v
