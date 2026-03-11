package com.nexus.bridgegateway.core.auth;

import java.time.LocalDateTime;

/**
 * 用户身份实体
 */
public class UserIdentity {

    private String userId;
    private String phoneNumber;
    private String email;
    private String walletAddress;
    private String privateKeyCipher;
    private boolean selfCustody;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    public String getPrivateKeyCipher() {
        return privateKeyCipher;
    }

    public void setPrivateKeyCipher(String privateKeyCipher) {
        this.privateKeyCipher = privateKeyCipher;
    }

    public boolean isSelfCustody() {
        return selfCustody;
    }

    public void setSelfCustody(boolean selfCustody) {
        this.selfCustody = selfCustody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
