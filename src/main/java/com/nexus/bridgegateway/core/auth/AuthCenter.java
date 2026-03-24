package com.nexus.bridgegateway.core.auth;

import com.nexus.bridgegateway.exception.UnauthorizedException;
import com.nexus.bridgegateway.mapper.UserIdentityMapper;
import com.nexus.bridgegateway.model.entity.UserIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一认证中心
 * 
 * 【架构定位】：
 * JWT 与钱包地址的映射及 SIWE (Sign-In with Ethereum) 校验。
 * 
 * 【核心功能】：
 * 1. Web2 登录（手机号/邮箱）
 * 2. Web3 登录（SIWE - EIP-4361）
 * 3. 托管钱包创建
 * 4. JWT Token 验证
 * 
 * 【线程模型】：
 * 所有阻塞操作（数据库、验签、密钥生成）必须通过虚拟线程执行，
 * 绝不能阻塞 Netty EventLoop 线程。
 * 
 * 【托管钱包说明】：
 * Web2 用户向 Web3 过渡的核心托管方案。
 * 系统为用户生成并加密存储私钥，用户无需管理私钥即可使用 Web3 功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCenter {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserIdentityMapper userIdentityMapper;
    private final JwtUtils jwtUtils;

    /**
     * SIWE Nonce Redis Key 前缀
     */
    private static final String NONCE_KEY_PREFIX = "auth:siwe:nonce:";

    /**
     * Web2 验证码 Redis Key 前缀
     */
    private static final String CODE_KEY_PREFIX = "auth:code:";

    /**
     * Nonce 过期时间（5分钟）
     */
    private static final Duration NONCE_EXPIRATION = Duration.ofMinutes(5);

    /**
     * 验证码过期时间（5分钟）
     */
    private static final Duration CODE_EXPIRATION = Duration.ofMinutes(5);

    /**
     * 存储Nonce到Redis
     * 
     * @param nonce 随机Nonce
     * @return 是否存储成功
     */
    public Mono<Boolean> storeNonce(String nonce) {
        String key = NONCE_KEY_PREFIX + nonce;
        return redisTemplate.opsForValue()
                .set(key, String.valueOf(System.currentTimeMillis()), NONCE_EXPIRATION)
                .doOnSuccess(result -> log.debug("[AuthCenter] Nonce 已存储, key={}, result={}", key, result))
                .onErrorResume(e -> {
                    log.error("[AuthCenter] 存储 Nonce 失败: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * 生成并发送验证码
     * 
     * 【功能说明】：
     * 生成 6 位数字验证码并存入 Redis，用于 Web2 登录验证。
     * 
     * 【生产环境】：
     * 实际应调用短信/邮件服务发送验证码，此处仅模拟。
     * 
     * @param phoneOrEmail 手机号或邮箱
     * @return 生成的验证码
     */
    public Mono<String> generateAndStoreVerificationCode(String phoneOrEmail) {
        String code = generate6DigitCode();
        String key = CODE_KEY_PREFIX + phoneOrEmail;
        
        log.info("[AuthCenter] 生成验证码, phoneOrEmail={}, code={}", phoneOrEmail, code);
        
        return redisTemplate.opsForValue()
                .set(key, code, CODE_EXPIRATION)
                .map(stored -> {
                    if (stored) {
                        log.info("[AuthCenter] 验证码已存储, key={}, code={}", key, code);
                        return code;
                    } else {
                        throw new RuntimeException("存储验证码失败");
                    }
                });
    }

    /**
     * Web2 登录（手机号/邮箱）
     * 
     * 【验证流程】：
     * 1. 验证码校验：从 Redis 查询验证码并比对
     * 2. 查询或注册：查找用户，不存在则创建托管钱包并注册
     * 3. 颁发 Token：生成 JWT 返回
     * 
     * 【线程模型】：
     * - Redis 操作：响应式，不阻塞
     * - 密钥生成：虚拟线程执行（CPU 密集型）
     * - 数据库操作：虚拟线程执行（阻塞 I/O）
     * 
     * @param phoneOrEmail    手机号或邮箱
     * @param verificationCode 验证码
     * @return JWT Token
     */
    public Mono<String> web2Login(String phoneOrEmail, String verificationCode) {
        log.info("[AuthCenter] 开始 Web2 登录验证, phoneOrEmail={}", phoneOrEmail);

        String codeKey = CODE_KEY_PREFIX + phoneOrEmail;

        // 【步骤 1】验证码校验
        return redisTemplate.opsForValue().get(codeKey)
                .switchIfEmpty(Mono.error(new UnauthorizedException("验证码已过期或不存在")))
                .flatMap(storedCode -> {
                    if (!storedCode.equals(verificationCode)) {
                        return Mono.error(new UnauthorizedException("验证码错误"));
                    }

                    // 【关键】验证通过后立即删除验证码，防止重放
                    return redisTemplate.delete(codeKey)
                            .then(Mono.defer(() -> {
                                log.info("[AuthCenter] 验证码校验通过, phoneOrEmail={}", phoneOrEmail);

                                // 【步骤 2】查询或注册用户（虚拟线程执行）
                                return findOrCreateWeb2User(phoneOrEmail);
                            }));
                });
    }

    /**
     * 查询或创建 Web2 用户
     * 
     * 【逻辑说明】：
     * 1. 根据手机号/邮箱查询用户
     * 2. 若存在，直接返回 userId
     * 3. 若不存在，创建托管钱包并注册新用户
     * 
     * 【线程模型】：
     * 密钥生成和数据库操作都是阻塞型操作，必须在虚拟线程中执行。
     * 
     * @param phoneOrEmail 手机号或邮箱
     * @return JWT Token
     */
    private Mono<String> findOrCreateWeb2User(String phoneOrEmail) {
        return Mono.fromCallable(() -> {
            // 查询现有用户
            UserIdentity existingUser = userIdentityMapper.selectByPhoneOrEmail(phoneOrEmail);

            if (existingUser != null) {
                // 用户已存在，返回 userId
                log.info("[AuthCenter] 用户已存在, userId={}, phoneOrEmail={}",
                        existingUser.getUserId(), phoneOrEmail);
                return existingUser.getUserId();
            }

            // 【静默注册】创建新用户
            // Web2 登录用户为托管模式（系统生成并存储私钥）
            log.info("[AuthCenter] 新用户注册，开始生成托管钱包, phoneOrEmail={}", phoneOrEmail);

            // 【关键】生成托管钱包
            CustodyWallet wallet = generateCustodyWallet();

            // 判断是手机号还是邮箱
            boolean isEmail = phoneOrEmail.contains("@");

            String newUserId = UUID.randomUUID().toString();
            UserIdentity newUser = new UserIdentity();
            newUser.setUserId(newUserId);
            newUser.setPhoneNumber(isEmail ? null : phoneOrEmail);
            newUser.setEmail(isEmail ? phoneOrEmail : null);
            newUser.setWalletAddress(wallet.address());
            newUser.setPrivateKeyCipher(wallet.privateKeyCipher());
            newUser.setSelfCustody(false);
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());

            userIdentityMapper.insert(newUser);

            log.info("[AuthCenter] 新用户注册成功, userId={}, walletAddress={}, selfCustody=false",
                    newUserId, wallet.address());

            return newUserId;

        }).subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()))
          .map(jwtUtils::generateToken);
    }

    /**
     * 生成托管钱包
     * 
     * 【功能说明】：
     * 为 Web2 用户生成以太坊托管钱包。
     * 
     * 【核心逻辑】：
     * 1. 使用 Web3j 的 Keys.createEcKeyPair() 随机生成密钥对
     * 2. 从公钥计算以太坊地址
     * 3. 加密私钥并存储
     * 
     * 【线程模型】：
     * 密钥生成是 CPU 密集型操作，建议在虚拟线程中执行。
     * 
     * 【安全说明】：
     * 当前使用 Base64 模拟加密，生产环境应使用 AES-256-GCM 加密。
     * 
     * @return 托管钱包（包含地址和加密后的私钥）
     */
    private CustodyWallet generateCustodyWallet() {
        try {
            // 【关键】生成随机 EC 密钥对
            // 这是 CPU 密集型操作，必须在虚拟线程中执行
            ECKeyPair keyPair = Keys.createEcKeyPair();

            // 从公钥计算以太坊地址
            String address = "0x" + Keys.getAddress(keyPair.getPublicKey());

            // 提取私钥（十六进制）
            String privateKeyHex = keyPair.getPrivateKey().toString(16);

            // 【模拟加密】使用 Base64 编码
            // 生产环境应使用 AES-256-GCM 加密，密钥由 KMS 提供
            String privateKeyCipher = Base64.getEncoder().encodeToString(
                    privateKeyHex.getBytes(StandardCharsets.UTF_8));

            log.info("[AuthCenter] 托管钱包生成成功, address={}", address);

            return new CustodyWallet(address, privateKeyCipher);

        } catch (Exception e) {
            log.error("[AuthCenter] 托管钱包生成失败: {}", e.getMessage(), e);
            throw new RuntimeException("托管钱包生成失败", e);
        }
    }

    /**
     * 托管钱包信息
     * 
     * @param address         钱包地址
     * @param privateKeyCipher 加密后的私钥
     */
    private record CustodyWallet(String address, String privateKeyCipher) {}

    /**
     * 生成 6 位数字验证码
     */
    private String generate6DigitCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    /**
     * Web3 登录（SIWE - EIP-4361）
     * 
     * 【验证流程】：
     * 1. 防重放校验：从 message 中解析 Nonce，校验 Redis 中是否存在
     * 2. 验签校验：使用 Web3j 恢复公钥，比对地址是否一致
     * 3. 身份映射：查询或创建用户身份记录
     * 4. 签发 Token：生成 JWT 返回
     * 
     * 【线程模型】：
     * - Redis 操作：响应式，不阻塞
     * - 验签操作：虚拟线程执行（CPU 密集型）
     * - 数据库操作：虚拟线程执行（阻塞 I/O）
     * 
     * @param walletAddress 钱包地址
     * @param message       SIWE 消息原文
     * @param signature     签名结果
     * @return JWT Token
     */
    public Mono<String> web3Login(String walletAddress, String message, String signature) {
        log.info("[AuthCenter] 开始 Web3 登录验证, walletAddress={}", walletAddress);

        // 【步骤 1】防重放校验：解析 Nonce 并验证
        String nonce = extractNonceFromMessage(message);
        if (nonce == null) {
            return Mono.error(new UnauthorizedException("无法从消息中解析 Nonce"));
        }

        String nonceKey = NONCE_KEY_PREFIX + nonce;

        return redisTemplate.hasKey(nonceKey)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new UnauthorizedException("Nonce 无效或已过期"));
                    }

                    // 【关键】Nonce 验证通过后立即删除，防止重放
                    return redisTemplate.delete(nonceKey)
                            .then(Mono.defer(() -> {
                                log.info("[AuthCenter] Nonce 验证通过，已删除, nonce={}", nonce);

                                // 【步骤 2】验签校验（虚拟线程执行）
                                return verifySignature(walletAddress, message, signature)
                                        .flatMap(valid -> {
                                            if (!valid) {
                                                return Mono.error(new UnauthorizedException("签名验证失败"));
                                            }

                                            log.info("[AuthCenter] 签名验证通过, walletAddress={}", walletAddress);

                                            // 【步骤 3】身份映射落库（虚拟线程执行）
                                            return findOrCreateUserIdentity(walletAddress);
                                        });
                            }));
                });
    }

    /**
     * 从 SIWE 消息中提取 Nonce
     * 
     * 【SIWE 消息格式】：
     * ...
     * nonce: 1234567890abcdef
     * ...
     * 
     * @param message SIWE 消息原文
     * @return Nonce 字符串，解析失败返回 null
     */
    private String extractNonceFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        // 使用正则表达式匹配 nonce 字段
        Pattern pattern = Pattern.compile("nonce:\\s*([a-fA-F0-9]+)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        log.warn("[AuthCenter] 无法从消息中解析 Nonce");
        return null;
    }

    /**
     * 验证以太坊签名
     * 
     * 【EIP-4361 简化验证机制】：
     * 1. 将消息转换为字节数组
     * 2. 使用 Web3j 的 Sign.signedPrefixedMessageToKey 恢复公钥
     * 3. 从公钥计算地址并比对
     * 
     * 【线程模型】：
     * 验签是 CPU 密集型操作，必须在虚拟线程中执行。
     * 
     * @param walletAddress 预期钱包地址
     * @param message       消息原文
     * @param signature     签名结果（十六进制）
     * @return 签名是否有效
     */
    private Mono<Boolean> verifySignature(String walletAddress, String message, String signature) {
        return Mono.fromCallable(() -> {
            try {
                // 解析签名（去掉 0x 前缀）
                byte[] signatureBytes = Numeric.hexStringToByteArray(signature);

                // 消息字节数组
                byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

                // 【关键】解析签名为 r, s, v 三部分
                // 以太坊签名格式: r (32字节) + s (32字节) + v (1字节)
                byte[] r = new byte[32];
                byte[] s = new byte[32];
                byte v = signatureBytes[64];
                
                System.arraycopy(signatureBytes, 0, r, 0, 32);
                System.arraycopy(signatureBytes, 32, s, 0, 32);
                
                Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);

                // 【关键】使用 Web3j 恢复公钥
                // signedPrefixedMessageToKey 会自动处理 Ethereum 的 "\x19Ethereum Signed Message:\n" 前缀
                BigInteger publicKey = Sign.signedPrefixedMessageToKey(messageBytes, signatureData);

                // 从公钥计算地址
                String recoveredAddress = "0x" + Keys.getAddress(publicKey);

                // 比对地址（忽略大小写）
                boolean valid = recoveredAddress.equalsIgnoreCase(walletAddress);

                log.debug("[AuthCenter] 签名验证结果: recovered={}, expected={}, valid={}",
                        recoveredAddress, walletAddress, valid);

                return valid;

            } catch (Exception e) {
                log.error("[AuthCenter] 签名验证异常: {}", e.getMessage(), e);
                return false;
            }
        }).subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()));
    }

    /**
     * 查询或创建用户身份（Web3 登录）
     * 
     * 【逻辑说明】：
     * 1. 根据钱包地址查询用户身份
     * 2. 若存在，直接返回 userId
     * 3. 若不存在，创建新用户身份并插入数据库（静默注册）
     * 
     * 【线程模型】：
     * 数据库操作是阻塞 I/O，必须在虚拟线程中执行。
     * 
     * @param walletAddress 钱包地址
     * @return JWT Token
     */
    private Mono<String> findOrCreateUserIdentity(String walletAddress) {
        return Mono.fromCallable(() -> {
            // 查询现有用户
            UserIdentity existingUser = userIdentityMapper.selectByWalletAddress(walletAddress);

            if (existingUser != null) {
                // 用户已存在，返回 userId
                log.info("[AuthCenter] 用户已存在, userId={}, walletAddress={}",
                        existingUser.getUserId(), walletAddress);
                return existingUser.getUserId();
            }

            // 【静默注册】创建新用户
            // Web3 登录用户为自托管模式（用户自己管理私钥）
            String newUserId = UUID.randomUUID().toString();
            UserIdentity newUser = new UserIdentity();
            newUser.setUserId(newUserId);
            newUser.setWalletAddress(walletAddress);
            newUser.setSelfCustody(true);
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());

            userIdentityMapper.insert(newUser);

            log.info("[AuthCenter] 新用户注册成功, userId={}, walletAddress={}, selfCustody=true",
                    newUserId, walletAddress);

            return newUserId;

        }).subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()))
          .map(jwtUtils::generateToken);
    }

    /**
     * 验证 JWT Token
     */
    public Mono<Boolean> validateToken(String token) {
        return Mono.fromCallable(() -> jwtUtils.validateToken(token))
                .subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()));
    }

    /**
     * 从 Token 中获取钱包地址
     */
    public Mono<String> getWalletAddressFromToken(String token) {
        return Mono.fromCallable(() -> {
            String userId = jwtUtils.getUserIdFromToken(token);
            if (userId == null) {
                return null;
            }
            return userIdentityMapper.selectWalletAddressByUserId(userId);
        }).subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()));
    }
}
