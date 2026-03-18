# FinGuard — Real-Time Fraud Detection System

A production-grade microservices architecture built with **Java 17**, **Spring Boot 3**, **Apache Kafka**, **Docker**, and a **RAG (Retrieval-Augmented Generation) pipeline** using LangChain4j + ChromaDB + OpenAI.

---

## Architecture

```
Transaction Service  ──Kafka──►  Fraud Service  ──REST──►  Risk Service
      (8081)          topic:         (8082)                   (8083)
                   transactions        │
                                       ├──REST──►  RAG Service  (8084)
                                       │
                                       └──Kafka──►  Notification Service
                                         topic:           (8085)
                                       fraud-alerts
```

| Flow | Method | Why |
|---|---|---|
| Transaction → Fraud | Kafka | High throughput, async |
| Fraud → Risk | REST (WebClient) | Needs immediate response |
| Fraud → RAG | REST (WebClient) | On-demand explanation |
| Fraud → Notification | Kafka | Async, scalable alerts |

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| `transaction-service` | 8081 | Accepts incoming transactions, publishes to Kafka |
| `fraud-service` | 8082 | Consumes transactions, runs rules, calls Risk + RAG, publishes alerts |
| `risk-service` | 8083 | Maintains account risk profiles, Redis-cached scoring |
| `rag-service` | 8084 | LangChain4j + ChromaDB + OpenAI for fraud explanations |
| `notification-service` | 8085 | Consumes fraud alerts, dispatches email + log notifications |

---

## Prerequisites

- **Docker** 24+ and **Docker Compose** v2
- **Java 17** (for local dev)
- **Maven 3.9+** (for local dev)
- **OpenAI API key** (for RAG service)

---

## Quick Start

### 1. Clone and configure

```bash
git clone <your-repo>
cd fingaurd

# Set up environment variables
cp .env.example .env
# Edit .env — add your OPENAI_API_KEY and mail credentials
```

### 2. Start infrastructure first

```bash
docker-compose up -d zookeeper kafka postgres redis chromadb

# Wait for Kafka to be ready (~15-20 seconds)
docker-compose logs -f kafka
# Look for: "started (kafka.server.KafkaServer)"
```

### 3. Build and start all services

```bash
docker-compose up -d --build
```

### 4. Verify everything is running

```bash
docker-compose ps
# All 10 containers should show "Up"

# Check service health endpoints
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
```

### 5. Seed the RAG knowledge base

```bash
curl -X POST http://localhost:8084/api/rag/seed
```

---

## Testing the Full Pipeline

### Submit a normal transaction (low risk — should pass)

```bash
curl -X POST http://localhost:8081/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-001",
    "amount": 42.50,
    "currency": "USD",
    "merchant": "Starbucks",
    "merchantCategory": "FOOD_BEVERAGE",
    "location": "New York, NY",
    "ipAddress": "192.168.1.1",
    "deviceId": "device-abc-123"
  }'
```

### Submit a suspicious transaction (high amount + crypto + missing context)

```bash
curl -X POST http://localhost:8081/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-002",
    "amount": 15000.00,
    "currency": "USD",
    "merchant": "CryptoExchange Pro",
    "merchantCategory": "CRYPTO",
    "location": "Unknown",
    "ipAddress": null,
    "deviceId": null
  }'
```

### Check generated fraud alerts

```bash
curl http://localhost:8082/api/fraud/alerts | python3 -m json.tool
```

### Check alerts for a specific transaction

```bash
curl http://localhost:8082/api/fraud/alerts/transaction/{transactionId}
```

### Check account risk profile

```bash
curl http://localhost:8083/api/risk/ACC-002
```

### Manually trigger RAG explanation

```bash
curl -X POST http://localhost:8084/api/rag/explain \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-002",
    "amount": 15000.00,
    "currency": "USD",
    "merchant": "CryptoExchange Pro",
    "merchantCategory": "CRYPTO",
    "location": "Unknown"
  }'
```

### Ingest a custom fraud case into the knowledge base

```bash
curl -X POST http://localhost:8084/api/rag/ingest \
  -H "Content-Type: application/json" \
  -d '"Account made 8 rapid crypto purchases in 1 hour totaling $32,000 from a new IP. Confirmed compromised by account holder."'
```

---

## Kafka Topics

| Topic | Producer | Consumer |
|---|---|---|
| `transactions` | transaction-service | fraud-service |
| `fraud-alerts` | fraud-service | notification-service |

### View topics in Kafka UI

```
http://localhost:8090
```

---

## Fraud Detection Rules

The `FraudRuleEngine` scores each transaction based on:

| Rule | Trigger | Score Contribution |
|---|---|---|
| `HIGH_AMOUNT` | Amount > $10,000 | Up to +0.40 |
| `VELOCITY_EXCEEDED` | >10 transactions/hour | Up to +0.50 |
| `SUSPICIOUS_CATEGORY` | CRYPTO / GAMBLING / MONEY_TRANSFER | +0.30 |
| `MISSING_CONTEXT` | Null IP or Device ID | +0.15 |

Scores are combined with the Risk Service score (60/40 blend):

| Final Score | Action |
|---|---|
| < 0.70 | Pass — no action |
| 0.70 – 0.89 | `FLAGGED` — alert sent |
| ≥ 0.90 | `BLOCKED` — alert sent |

---

## Development Commands

### Build all services

```bash
for svc in transaction-service fraud-service risk-service rag-service notification-service; do
  echo "Building $svc..."
  cd $svc && mvn clean package -DskipTests && cd ..
done
```

### Run a single service locally (with Docker infra running)

```bash
# Example: run fraud-service locally
cd fraud-service
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fingaurd \
SPRING_DATASOURCE_USERNAME=fingaurd \
SPRING_DATASOURCE_PASSWORD=fingaurd123 \
SPRING_REDIS_HOST=localhost \
mvn spring-boot:run
```

### Rebuild and restart a single Docker service

```bash
docker-compose up -d --build fraud-service
```

### View logs

```bash
# All services
docker-compose logs -f

# Single service
docker-compose logs -f fraud-service

# Last 100 lines
docker-compose logs --tail=100 fraud-service
```

### Connect to PostgreSQL

```bash
docker exec -it fingaurd-postgres-1 psql -U fingaurd -d fingaurd

# Useful queries:
# SELECT * FROM transactions ORDER BY created_at DESC LIMIT 10;
# SELECT * FROM fraud_alerts ORDER BY created_at DESC LIMIT 10;
# SELECT * FROM risk_profiles;
```

### Connect to Redis

```bash
docker exec -it fingaurd-redis-1 redis-cli
# KEYS *                  -- list all keys
# GET velocity:ACC-002    -- check velocity counter
```

---

## Stopping and Cleanup

```bash
# Stop all containers (keep volumes/data)
docker-compose down

# Stop and remove all data
docker-compose down -v

# Remove built images too
docker-compose down -v --rmi local
```

---

## Extending the System

- **Add Slack notifications**: Implement a `SlackNotifier` bean in `notification-service` and call it from `NotificationService.handleFraudAlert()`
- **Add more rules**: Add methods to `FraudRuleEngine` in `fraud-service`
- **ML model scoring**: Replace/supplement `FraudRuleEngine` with a call to a scikit-learn or TensorFlow Serving endpoint
- **Add a dashboard**: Spring Boot + Thymeleaf or a React frontend consuming the REST APIs
- **Schema registry**: Add Confluent Schema Registry to docker-compose for Avro serialisation
# fraud-detect-backend
