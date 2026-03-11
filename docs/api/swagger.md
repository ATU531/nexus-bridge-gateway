# Nexus Bridge Gateway API 文档

## 基础信息

- **Base URL**: `http://localhost:8080/api`
- **Content-Type**: `application/json`
- **API Version**: v1.0.0

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

### 1. 获取地址余额

获取指定以太坊地址的 ETH 余额。

#### 请求

- **方法**: `GET`
- **路径**: `/query/balance/{address}`
- **描述**: 查询指定地址的 ETH 余额

#### 路径参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| address | string | 是 | 以太坊地址（0x 开头的 42 位十六进制字符串） | 0xd8da6bf26964af9d7eed9e03e53415d37aa96045 |

#### 请求示例

```bash
curl -X GET "http://localhost:8080/api/query/balance/0xd8da6bf26964af9d7eed9e03e53415d37aa96045"
```

#### 响应示例

**成功响应 (200 OK)**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "address": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
    "balance": "1.234567890123456789",
    "unit": "ETH"
  }
}
```

**错误响应 (400 Bad Request)**

```json
{
  "code": 400,
  "message": "Invalid Ethereum address format",
  "data": null
}
```

**错误响应 (500 Internal Server Error)**

```json
{
  "code": 500,
  "message": "Failed to query balance from RPC node",
  "data": null
}
```

#### 响应字段说明

| 字段名 | 类型 | 描述 |
|--------|------|------|
| code | number | HTTP 状态码 |
| message | string | 响应消息 |
| data.address | string | 查询的以太坊地址 |
| data.balance | string | 余额（字符串格式，避免精度丢失） |
| data.unit | string | 单位（固定为 ETH） |

---

### 2. 获取最新区块号

获取以太坊主网的最新区块号。

#### 请求

- **方法**: `GET`
- **路径**: `/query/block/latest`
- **描述**: 查询最新的区块号

#### 请求示例

```bash
curl -X GET "http://localhost:8080/api/query/block/latest"
```

#### 响应示例

**成功响应 (200 OK)**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "blockNumber": 19234567,
    "timestamp": "2026-03-07T10:30:45Z"
  }
}
```

**错误响应 (500 Internal Server Error)**

```json
{
  "code": 500,
  "message": "Failed to query latest block number from RPC node",
  "data": null
}
```

#### 响应字段说明

| 字段名 | 类型 | 描述 |
|--------|------|------|
| code | number | HTTP 状态码 |
| message | string | 响应消息 |
| data.blockNumber | number | 最新区块号 |
| data.timestamp | string | 查询时间（ISO 8601 格式） |

---

## 错误码说明

| 错误码 | HTTP 状态码 | 描述 |
|--------|------------|------|
| 200 | 200 OK | 请求成功 |
| 400 | 400 Bad Request | 请求参数错误 |
| 404 | 404 Not Found | 资源不存在 |
| 500 | 500 Internal Server Error | 服务器内部错误 |

## 数据模型

### BalanceResponse

```java
public record BalanceResponse(
    String address,
    String balance,
    String unit
) {}
```

### BlockResponse

```java
public record BlockResponse(
    Long blockNumber,
    String timestamp
) {}
```

### ApiResponse<T>

```java
public record ApiResponse<T>(
    int code,
    String message,
    T data
) {}
```

## 注意事项

1. **地址格式**: 地址必须是有效的以太坊地址，以 `0x` 开头，共 42 个字符
2. **余额精度**: 余额返回字符串格式，避免 JavaScript 等语言的浮点数精度问题
3. **单位换算**: 余额已自动从 Wei 转换为 ETH（1 ETH = 10^18 Wei）
4. **超时设置**: RPC 请求默认超时时间为 10 秒
5. **速率限制**: 建议客户端实现合理的请求频率控制

## 使用示例

### Java 示例

```java
import org.springframework.web.reactive.function.client.WebClient;

public class Web3QueryClient {
    private final WebClient webClient;

    public Web3QueryClient() {
        this.webClient = WebClient.create("http://localhost:8080/api");
    }

    public BalanceResponse getBalance(String address) {
        return webClient.get()
            .uri("/query/balance/{address}", address)
            .retrieve()
            .bodyToMono(BalanceResponse.class)
            .block();
    }

    public BlockResponse getLatestBlock() {
        return webClient.get()
            .uri("/query/block/latest")
            .retrieve()
            .bodyToMono(BlockResponse.class)
            .block();
    }
}
```

### JavaScript 示例

```javascript
const axios = require('axios');

const BASE_URL = 'http://localhost:8080/api';

async function getBalance(address) {
    try {
        const response = await axios.get(`${BASE_URL}/query/balance/${address}`);
        return response.data;
    } catch (error) {
        console.error('Error fetching balance:', error.response?.data || error.message);
        throw error;
    }
}

async function getLatestBlock() {
    try {
        const response = await axios.get(`${BASE_URL}/query/block/latest`);
        return response.data;
    } catch (error) {
        console.error('Error fetching latest block:', error.response?.data || error.message);
        throw error;
    }
}

// 使用示例
(async () => {
    const address = '0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb';
    const balance = await getBalance(address);
    console.log('Balance:', balance);

    const block = await getLatestBlock();
    console.log('Latest Block:', block);
})();
```

### Python 示例

```python
import requests

BASE_URL = 'http://localhost:8080/api'

def get_balance(address):
    try:
        response = requests.get(f'{BASE_URL}/query/balance/{address}')
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f'Error fetching balance: {e}')
        raise

def get_latest_block():
    try:
        response = requests.get(f'{BASE_URL}/query/block/latest')
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f'Error fetching latest block: {e}')
        raise

# 使用示例
if __name__ == '__main__':
    address = '0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb'
    balance = get_balance(address)
    print(f'Balance: {balance}')

    block = get_latest_block()
    print(f'Latest Block: {block}')
```

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0.0 | 2026-03-07 | 初始版本，包含余额查询和最新区块查询接口 |
