# Redis 활용 게임 서비스 성능 최적화 프로젝트

이 프로젝트는 실시간 게임 서비스에서 데이터 일관성과 성능 간의 트레이드오프를 분석하기 위해 세 가지 다른 데이터 관리 전략을 구현하고 비교합니다. Spring Boot와 WebSocket을 기반으로 하며, Redis Pub/Sub을 활용한 다양한 캐싱 전략을 테스트합니다.

## 🎯 프로젝트 목적

대규모 트래픽이 발생하는 실시간 게임 환경에서 상태 동기화와 응답 속도 최적화를 위한 최적의 아키텍처를 탐구합니다.
다음 세 가지 전략의 성능(처리량, 지연 시간)과 데이터 일관성을 비교 분석합니다.

1. **RDBMS 중심 전략**: 전통적인 DB 기반 접근
2. **Global Cache 전략**: Redis를 메인 저장소로 활용
3. **Local Cache 전략**: 애플리케이션 인메모리 캐시 활용


### 기술 스택
- **Backend**: Java 17, Spring Boot 3.x
- **Database**: MySQL 8.0
- **Cache/Message Broker**: Redis 7.0
- **Communication**: WebSocket (STOMP/Native)
- **Testing**: k6 (Load Testing)
- **Monitoring**: Prometheus, Grafana, Redis Exporter
- **Infrastructure**: Docker, Docker Compose

## 💡 구현 전략 상세

### 1. RDBMS + Redis Pub/Sub (Invalidation)
- **동작 방식**: 모든 상태 변경을 MySQL에 직접 기록합니다. 변경 발생 시 Redis Pub/Sub으로 `INVALIDATE` 이벤트를 발행하여 다른 서버들이 로컬 상태를 무효화하도록 알립니다.
- **장점**: 데이터의 영속성과 강력한 일관성(ACID) 보장.
- **단점**: 디스크 I/O로 인한 높은 지연 시간, DB 부하 집중.

### 2. Global Cache (Redis) + Redis Pub/Sub
- **동작 방식**: 게임 룸의 상태를 Redis에 저장하고 조회합니다. DB는 비동기 백업 용도로만 사용하거나 배제합니다. 변경 시 `INVALIDATE` 이벤트를 발행합니다.
- **장점**: 인메모리 저장소의 빠른 응답 속도, 서버 간 데이터 공유 용이.
- **단점**: Redis 네트워크 비용 발생, 데이터 직렬화/역직렬화 오버헤드.

### 3. Local Cache + Redis Pub/Sub (Payload)
- **동작 방식**: 각 서버의 메모리(ConcurrentHashMap)에 게임 상태를 저장합니다. 상태 변경 시 변경된 데이터(Payload) 자체를 Redis Pub/Sub으로 전파하여 다른 서버들이 자신의 로컬 캐시를 동기화합니다.
- **장점**: 네트워크 I/O 없는 극강의 조회 성능 (Zero Latency).
- **단점**: 서버 간 데이터 불일치(Race Condition) 가능성 높음, 메모리 사용량 증가.

## 📊 성능 테스트 (Load Test)

`k6`를 사용하여 WebSocket 연결 및 메시지 처리 성능을 측정합니다.

### 테스트 시나리오
- **VUs (가상 사용자)**: 1000명
- **Duration**: 각 전략당 120초
- **Flow**:
  1. WebSocket 연결
  2. 게임 룸 생성 (`CREATE_ROOM`)
  3. 카드 선택 액션 10회 반복 (`SELECT_CARD`)
  4. 연결 종료

### 실행 방법

1. **인프라 실행**
   ```bash
   docker-compose up -d
   ```

2. **애플리케이션 실행**
   ```bash
   ./gradlew bootRun
   ```

3. **부하 테스트 실행**
   ```bash
   # k6가 설치되어 있어야 합니다
   k6 run load-test/script.js
   ```

4. **모니터링**
   - **Grafana**: http://localhost:3001 (admin/admin)
   - **Prometheus**: http://localhost:9090

## 📈 결과 분석 (예상)

| 전략 | 처리량 (Throughput) | 지연 시간 (Latency) | 데이터 일관성 | 비고 |
|------|-------------------|-------------------|-------------|------|
| **RDBMS** | 낮음 | 높음 | 높음 | 트랜잭션 보장, 안정적 |
| **Global Cache** | 중간 ~ 높음 | 중간 | 중간 | Redis 네트워크 병목 가능성 |
| **Local Cache** | 매우 높음 | 매우 낮음 | 낮음 | 동기화 복잡성, 최고의 조회 성능 |

> **Note**: 실제 테스트 결과는 하드웨어 사양 및 네트워크 환경에 따라 달라질 수 있습니다. Local Cache 전략은 동시성 제어가 제대로 되지 않을 경우 데이터 덮어쓰기 문제가 발생할 수 있음을 유의해야 합니다.
