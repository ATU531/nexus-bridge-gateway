# Nexus Bridge Gateway API 文档

## 基础信息

- **Base URL**: `http://localhost:8080/api`
- **Content-Type**: `application/json`
- **API Version**: v1.0.0

## 认证机制

本网关采用 **JWT + 身份映射** 双重认证机制，确保 Web2 用户身份与 Web3 钱包地址的安全映射。

### 认证架构

```
客户端请求
    ↓
JwtAuthenticationFilter (优先级: -100)
    ↓
验证 Authorization: Bearer <token>
    ↓
解析 JWT Token 获取 userId
    ↓
注入 X-Trusted-User-ID 请求头（绝对可信）
    ↓
AuthGlobalFilter (优先级: -99)
    ↓
查询 user_identity 表获取钱包地址
    ↓
重写请求头 X-Nexus-Wallet-Address
    ↓
传递给下游服务
```

### 请求头说明

| 请求头 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| Authorization | string | 是 | JWT Token 格式：`Bearer <token>` |
| X-Trusted-User-ID | string | 否 | 由 JWT Filter 注入的绝对可信用户ID |
| X-User-ID | string | 否 | Web2 系统用户ID（兼容模式，不推荐） |
| X-Nexus-Wallet-Address | string | 否 | Web3 钱包地址。由网关自动注入 |

### JWT Token 生成

```java
// 使用 JwtUtils 生成 Token
String token = jwtUtils.generateToken("user123");
```

### 使用示例

```bash
# 方式一：JWT 认证 + 自动钱包映射（推荐）
curl -X GET "http://localhost:8080/api/v1/user/eth/balance" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# 方式二：直接提供钱包地址查询
curl -X GET "http://localhost:8080/api/v1/user/eth/balance/0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"

# 方式三：兼容模式（不推荐）
curl -X GET "http://localhost:8080/api/v1/user/eth/balance" \
  -H "X-User-ID: user123"
```

### 错误响应

```json
{
  "code": 401,
  "message": "Unauthorized",
  "data": null
}
```

## 通用响应格式

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 错误响应

```json
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```

## 核心查询引擎 API

### 1. 获取地址余额（通过 X-User-ID）

通过 Web2 用户ID 查询绑定的 Web3 钱包地址余额。不需要提供 URL 中的地址参数。

#### 请求

- **方法**: `GET`
- **路径**: `/v1/user/{chain}/balance`
- **描述**: 通过 X-User-ID 查询用户绑定的钱包地址余额
- **架构说明**: 此接口通过 Spring Cloud Gateway 路由转发到内部服务，确保经过 AuthGlobalFilter 身份认证

#### 路径参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| chain | string | 是 | 链标识（eth, bsc, polygon） | eth |

#### 请求头参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| X-User-ID | string | **是** | Web2 系统用户ID。网关会根据此ID查询绑定的钱包地址 |
| X-Nexus-Wallet-Address | string | 否 | Web3 钱包地址。由网关自动注入 |

#### 支持的链

| 链标识 | 链名称 | 原生代币 |
|--------|--------|----------|
| eth | Ethereum | ETH |
| bsc | BNB Smart Chain | BNB |
| polygon | Polygon | MATIC |

#### 请求示例

```bash
# 通过 X-User-ID 查询以太坊余额
curl -X GET "http://localhost:8080/api/v1/user/eth/balance" \
  -H "X-User-ID: user123"

# 通过 X-User-ID 查询 BSC 余额
curl -X GET "http://localhost:8080/api/v1/user/bsc/balance" \
  -H "X-User-ID: user123"

# 通过 X-User-ID 查询 Polygon 余额
curl -X GET "http://localhost:8080/api/v1/user/polygon/balance" \
  -H "X-User-ID: user123"
```

#### 响应示例

**成功响应 (200 OK)**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "address": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
    "chain": "eth",
    "symbol": "ETH",
    "balance": "1.234567890123456789"
  }
}
```

**错误响应 (400 Bad Request)**

```json
{
  "code": 400,
  "message": "Missing X-User-ID header or user has no bound wallet address",
  "data": null
}
```

---

### 2. 获取地址余额（通过钱包地址）

获取指定链上地址的原生代币余额。

#### 请求

- **方法**: `GET`
- **路径**: `/v1/user/{chain}/balance/{address}`
- **描述**: 查询指定链上地址的原生代币余额
- **架构说明**: 此接口通过 Spring Cloud Gateway 路由转发到内部服务，确保经过 AuthGlobalFilter 身份认证

#### 路径参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| chain | string | 是 | 链标识（eth, bsc, polygon） | eth |
| address | string | 是 | 钱包地址（0x 开头的 42 位十六进制字符串）。当提供 X-User-ID 请求头时，此参数会被忽略 | 0xd8da6bf26964af9d7eed9e03e53415d37aa96045 |

#### 请求头参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| X-User-ID | string | 否 | Web2 系统用户ID。提供后，网关会自动查询该用户绑定的钱包地址进行查询，优先级高于路径参数 |
| X-Nexus-Wallet-Address | string | 否 | Web3 钱包地址。由网关自动注入，优先级高于路径参数 |

#### 支持的链

| 链标识 | 链名称 | 原生代币 |
|--------|--------|----------|
| eth | Ethereum | ETH |
| bsc | BNB Smart Chain | BNB |
| polygon | Polygon | MATIC |

#### 请求示例

```bash
# 方式一：直接查询指定地址余额
curl -X GET "http://localhost:8080/api/v1/user/eth/balance/0xd8da6bf26964af9d7eed9e03e53415d37aa96045"

# 方式二：使用 Web2 用户ID 查询（网关自动映射钱包地址，忽略 URL 中的地址）
curl -X GET "http://localhost:8080/api/v1/user/eth/balance/0x0000000000000000000000000000000000000000" \
  -H "X-User-ID: user123"

# 查询 BSC 地址余额
curl -X GET "http://localhost:8080/api/v1/user/bsc/balance/0xd8da6bf26964af9d7eed9e03e53415d37aa96045"

# 查询 Polygon 地址余额
curl -X GET "http://localhost:8080/api/v1/user/polygon/balance/0xd8da6bf26964af9d7eed9e03e53415d37aa96045"
```

#### 响应示例

**成功响应 (200 OK)**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "address": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
    "chain": "eth",
    "symbol": "ETH",
    "balance": "1.234567890123456789"
  }
}
```

**错误响应 (400 Bad Request)**

```json
{
  "code": 400,
  "message": "Invalid address format",
  "data": null
}
```

**错误响应 (404 Not Found)**

```json
{
  "code": 404,
  "message": "Unsupported chain: xxx. Supported chains: [eth, bsc, polygon]",
  "data": null
}
```

**错误响应 (500 Internal Server Error)**

```json
{
  "code": 500,
  "message": "Failed to query eth chain balance",
  "data": null
}
```

#### 响应字段说明

| 字段名 | 类型 | 描述 |
|--------|------|------|
| code | number | HTTP 状态码 |
| message | string | 响应消息 |
| data.address | string | 查询的钱包地址 |
| data.chain | string | 链标识 |
| data.symbol | string | 原生代币符号（ETH, BNB, MATIC） |
| data.balance | string | 余额（字符串格式，避免精度丢失） |

---

### 3. 获取最新区块号

获取指定链的最新区块号。

#### 请求

- **方法**: `GET`
- **路径**: `/v1/user/{chain}/block/latest`
- **描述**: 查询指定链的最新区块号
- **架构说明**: 此接口通过 Spring Cloud Gateway 路由转发到内部服务，确保经过 AuthGlobalFilter 身份认证

#### 路径参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| chain | string | 是 | 链标识（eth, bsc, polygon） | eth |

#### 请求示例

```bash
# 查询以太坊最新区块
curl -X GET "http://localhost:8080/api/v1/user/eth/block/latest"

# 查询 BSC 最新区块
curl -X GET "http://localhost:8080/api/v1/user/bsc/block/latest"

# 查询 Polygon 最新区块
curl -X GET "http://localhost:8080/api/v1/user/polygon/block/latest"
```

#### 响应示例

**成功响应 (200 OK)**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "blockNumber": 19234567
  }
}
```

**错误响应 (404 Not Found)**

```json
{
  "code": 404,
  "message": "Unsupported chain: xxx. Supported chains: [eth, bsc, polygon]",
  "data": null
}
```

**错误响应 (500 Internal Server Error)**

```json
{
  "code": 500,
  "message": "Failed to query eth chain block number",
  "data": null
}
```

#### 响应字段说明

| 字段名 | 类型 | 描述 |
|--------|------|------|
| code | number | HTTP 状态码 |
| message | string | 响应消息 |
| data.blockNumber | number | 最新区块号 |

---

## 错误码说明

| 错误码 | HTTP 状态码 | 描述 |
|--------|------------|------|
| 200 | 200 OK | 请求成功 |
| 400 | 400 Bad Request | 请求参数错误 |
| 401 | 401 Unauthorized | 认证失败（用户ID未绑定钱包地址） |
| 404 | 404 Not Found | 资源不存在（包括不支持的链） |
| 500 | 500 Internal Server Error | 服务器内部错误 |

## 数据模型

### BalanceResponse

```java
public record BalanceResponse(
    String address,   // 钱包地址
    String chain,     // 链标识
    String symbol,    // 原生代币符号
    String balance    // 余额
) {}
```

### BlockResponse

```java
public record BlockResponse(
    Long blockNumber  // 区块号
) {}
```

### ApiResponse<T>

```java
public record ApiResponse<T>(
    int code,         // 状态码
    String message,   // 消息
    T data            // 数据
) {}
```

## 注意事项

1. **地址格式**: 地址必须是有效的 EVM 地址，以 `0x` 开头，共 42 个字符
2. **余额精度**: 余额返回字符串格式，避免 JavaScript 等语言的浮点数精度问题
3. **单位换算**: 余额已自动从 Wei 转换为对应链的原生代币单位
4. **链标识**: 请使用小写的链标识（eth, bsc, polygon）
5. **多链支持**: 系统支持 Ethereum、BSC、Polygon 三条链，后续可扩展更多链
6. **身份映射**: 使用 `X-User-ID` 请求头时，系统会自动查询 `user_identity` 表获取绑定的钱包地址。如果用户未绑定钱包地址，将返回路径参数中的地址
7. **请求头优先级**: `X-Nexus-Wallet-Address` 请求头的优先级高于路径参数中的地址
