--[[
    Nonce 原子分配脚本 (nonce_allocate.lua)
    
    【原子性原理】：
    Redis Lua 脚本在 Redis 服务器端以原子方式执行，整个脚本执行期间不会被其他命令打断。
    这保证了 Nonce 分配的原子性，避免了并发场景下的竞态条件。
    
    【参数说明】：
    KEYS[1]: Redis Key，格式为 "nonce:{chain}:{address}"
    ARGV[1]: 链上查询的 fallback nonce 基准值（可选，空字符串表示不使用）
    
    【返回值】：
    >= 0: 分配的 Nonce 值（可直接用于交易）
    -1:   Redis 中无缓存，且未提供基准值，需要调用方去链上查询
    
    【执行逻辑】：
    1. 如果 Redis 中存在该 key，则将其递增 (INCR)，返回递增前的值
    2. 如果不存在，但 ARGV[1] 有值，则将 key 初始化为 ARGV[1] + 1，并设置过期时间，然后返回 ARGV[1]
    3. 如果都不存在，返回 -1
--]]

local key = KEYS[1]
local fallback_nonce = ARGV[1]

-- 检查 key 是否存在
if redis.call("EXISTS", key) == 1 then
    -- key 存在，原子递增并返回递增前的值
    -- 例如：当前值为 5，INCR 后变为 6，返回 5（本次分配的 Nonce）
    return redis.call("INCR", key) - 1
end

-- key 不存在，检查是否提供了 fallback 基准值
if fallback_nonce and fallback_nonce ~= "" then
    -- 有基准值，初始化 key 为基准值 + 1（下次分配时使用）
    -- 并设置过期时间为 1 小时（3600 秒）
    local base_nonce = tonumber(fallback_nonce)
    redis.call("SET", key, base_nonce + 1, "EX", 3600)
    -- 返回基准值作为本次分配的 Nonce
    return base_nonce
end

-- 既没有缓存，也没有基准值，返回 -1 让调用方去链上查询
return -1
