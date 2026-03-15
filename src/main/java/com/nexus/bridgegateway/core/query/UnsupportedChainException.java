package com.nexus.bridgegateway.core.query;

/**
 * 不支持的链异常
 * 当请求了未配置的区块链网络时抛出
 */
public class UnsupportedChainException extends RuntimeException {

    public UnsupportedChainException(String message) {
        super(message);
    }

    public UnsupportedChainException(String message, Throwable cause) {
        super(message, cause);
    }
}
