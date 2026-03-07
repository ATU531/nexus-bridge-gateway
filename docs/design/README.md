# Nexus Bridge Gateway 架构设计文档

## 项目概述

Nexus Bridge Gateway 是一个基于 Spring Boot 3.4.0 和 Java 21 的 Web3 网关服务，提供以太坊区块链数据的查询和桥接功能。

## 技术栈

- **Java 版本**: 21
- **Spring Boot**: 3.4.0
- **Web 框架**: Spring WebFlux (响应式编程)
- **Web3 库**: Web3j 4.12.0
- **并发模型**: 虚拟线程 (Virtual Threads)
- **构建工具**: Maven

## 项目结构

```
nexus-bridge-gateway/
├── src/
│   ├── main/
│   │   ├── java/com/nexus/bridgegateway/
│   │   │   ├── config/          # 配置类
│   │   │   ├── controller/      # REST 控制器
│   │   │   ├── service/         # 业务服务层
│   │   │   ├── model/           # 数据模型
│   │   │   ├── exception/       # 异常处理
│   │   │   └── BridgeGatewayApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/nexus/bridgegateway/
├── docs/
│   ├── design/README.md         # 架构设计文档
│   └── api/swagger.md           # API 文档
└── pom.xml
```

## 核心模块

### 1. 核心查询引擎 (Web3 Query Engine)

#### 1.1 RPC 节点选型

**选型**: Cloudflare Ethereum RPC 节点

**RPC URL**: `https://cloudflare-eth.com`

**选型理由**:
- **高可用性**: Cloudflare 提供全球分布的节点网络，确保服务稳定性
- **低延迟**: 利用 Cloudflare 的边缘计算能力，提供快速响应
- **免费使用**: 无需付费，适合开发和测试环境
- **HTTPS 支持**: 提供安全的加密连接
- **公共节点**: 无需注册认证，简化配置

**备选节点** (生产环境建议):
- Infura: `https://mainnet.infura.io/v3/YOUR_PROJECT_ID`
- Alchemy: `https://eth-mainnet.g.alchemy.com/v2/YOUR_API_KEY`
- QuickNode: `https://YOUR_ENDPOINT.quiknode.pro/YOUR_KEY/`

#### 1.2 数据流向

```
客户端请求
    ↓
QueryController (REST API)
    ↓
Web3QueryService (业务逻辑)
    ↓
Virtual Thread (虚拟线程执行)
    ↓
Web3j (RPC 调用)
    ↓
Cloudflare Ethereum RPC 节点
    ↓
以太坊区块链网络
    ↓
返回数据 (经过虚拟线程)
    ↓
Web3QueryService (数据处理)
    ↓
QueryController (封装响应)
    ↓
客户端响应
```

#### 1.3 技术实现要点

**虚拟线程使用**:
```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<BigInteger> future = executor.submit(() -> web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance());
    return future.get();
}
```

**优势**:
- 轻量级并发: 虚拟线程占用资源极少，可创建百万级线程
- 阻塞友好: 传统阻塞代码可直接在虚拟线程中运行
- 简化编程模型: 无需使用响应式编程的复杂回调
- 提升吞吐量: 充分利用系统资源处理大量并发请求

**重试机制**:
- 实现了自动重试机制（最多 3 次）
- 检测 RPC 节点内部错误（`MessageDecodingException`）
- 指数退避策略：第 1 次重试延迟 1 秒，第 2 次 2 秒，第 3 次 3 秒
- 增强系统稳定性，应对 RPC 节点临时故障

**数据模型**:
- 使用 Java 21 `record` 关键字定义不可变数据结构
- 自动生成构造器、getter、equals、hashCode、toString
- 简洁且线程安全

**异常处理**:
- 全局异常处理器捕获 Web3j RPC 错误
- 统一错误响应格式
- 详细的错误日志记录
- 特殊处理 RPC 节点内部错误（错误码 -32603）

#### 1.4 API 接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 获取余额 | GET | /api/query/balance/{address} | 获取指定地址的 ETH 余额 |
| 获取最新区块 | GET | /api/query/block/latest | 获取最新的区块号 |

#### 1.5 性能优化

- **连接池**: Web3j 内置 HTTP 连接池管理
- **超时配置**: 合理设置 RPC 请求超时时间
- **缓存策略**: 可考虑添加 Redis 缓存热点数据
- **限流保护**: 使用 Spring Boot Actuator 和 Resilience4j 实现限流

## 配置说明

### application.yml 关键配置

```yaml
spring:
  application:
    name: nexus-bridge-gateway
  webflux:
    base-path: /api

server:
  port: 8080

web3:
  rpc-url: https://cloudflare-eth.com
  timeout: 10000
```

## 扩展性设计

### 未来功能模块

1. **多链支持**: 扩展支持 BSC、Polygon 等其他 EVM 兼容链
2. **交易查询**: 实现交易详情查询接口
3. **合约交互**: 支持智能合约方法调用
4. **事件监听**: 实时监听区块链事件
5. **数据缓存**: 集成 Redis 缓存层
6. **监控告警**: 集成 Prometheus 和 Grafana

### 架构演进方向

- **微服务化**: 将不同区块链查询拆分为独立服务
- **消息队列**: 引入 Kafka/RabbitMQ 处理异步任务
- **服务网格**: 使用 Istio 管理服务间通信
- **多区域部署**: 实现跨区域的高可用部署

## 安全考虑

- **HTTPS 强制**: 生产环境强制使用 HTTPS
- **API 认证**: 实现基于 JWT 的 API 认证
- **速率限制**: 防止 API 滥用
- **输入验证**: 严格验证所有用户输入
- **敏感数据保护**: RPC 密钥等敏感信息使用环境变量或密钥管理服务

## 监控与运维

### 健康检查

- Spring Boot Actuator 提供 `/actuator/health` 端点
- 自定义健康检查: RPC 节点连通性检查

### 日志管理

- 使用 SLF4J + Logback
- 结构化日志输出
- 日志级别动态调整

### 指标监控

- Spring Boot Actuator Metrics
- 关键指标: 请求响应时间、错误率、RPC 调用次数
