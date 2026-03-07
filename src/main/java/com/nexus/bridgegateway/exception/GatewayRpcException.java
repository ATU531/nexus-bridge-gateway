package com.nexus.bridgegateway.exception;

/**
 * 自定义网关 RPC 异常
 * 用于处理与 Web3 节点交互时发生的上游错误（如节点内部错误、限流等）
 */
public class GatewayRpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    // 可选：你可以增加一个字段来保存 RPC 节点返回的具体错误码
    private final Integer rpcErrorCode;

    public GatewayRpcException(String message) {
        super(message);
        this.rpcErrorCode = null;
    }

    public GatewayRpcException(String message, Throwable cause) {
        super(message, cause);
        this.rpcErrorCode = null;
    }

    public GatewayRpcException(String message, Integer rpcErrorCode) {
        super(message);
        this.rpcErrorCode = rpcErrorCode;
    }

    public GatewayRpcException(String message, Integer rpcErrorCode, Throwable cause) {
        super(message, cause);
        this.rpcErrorCode = rpcErrorCode;
    }

    public Integer getRpcErrorCode() {
        return rpcErrorCode;
    }
}