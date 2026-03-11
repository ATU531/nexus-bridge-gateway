package com.nexus.bridgegateway.core.risk;

/**
 * 风控检查结果
 */
public class RiskCheckResult {

    private final boolean passed;
    private final String message;

    private RiskCheckResult(boolean passed, String message) {
        this.passed = passed;
        this.message = message;
    }

    public static RiskCheckResult passed() {
        return new RiskCheckResult(true, "风控检查通过");
    }

    public static RiskCheckResult failed(String message) {
        return new RiskCheckResult(false, message);
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }
}
