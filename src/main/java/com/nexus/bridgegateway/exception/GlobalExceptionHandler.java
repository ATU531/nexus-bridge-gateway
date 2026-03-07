package com.nexus.bridgegateway.exception;

import com.nexus.bridgegateway.model.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.exceptions.TransactionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ClientConnectionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleClientConnectionException(ClientConnectionException e) {
        logger.error("RPC connection error: {}", e.getMessage(), e);
        return ApiResponse.error(503, "Unable to connect to RPC node. Please try again later.");
    }

    @ExceptionHandler(TransactionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleTransactionException(TransactionException e) {
        logger.error("Transaction error: {}", e.getMessage(), e);
        return ApiResponse.error(500, "Failed to process transaction request: " + e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.error("Invalid argument: {}", e.getMessage(), e);
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        logger.error("Runtime error: {}", e.getMessage(), e);
        return ApiResponse.error(500, "Internal server error: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGenericException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return ApiResponse.error(500, "An unexpected error occurred");
    }
}
