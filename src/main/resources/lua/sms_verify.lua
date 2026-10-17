-- 短信验证码校验并一次性消费（原子执行，避免并发重复使用）
-- KEYS[1] 验证码哈希 key
-- KEYS[2] 错误次数 key
-- ARGV[1] 本次输入验证码的哈希
-- ARGV[2] 最大错误次数
-- ARGV[3] 错误次数 key 的 TTL（秒）
-- 返回：1 校验通过；0 验证码错误；-1 错误次数过多；-2 验证码不存在或已过期

local stored = redis.call('GET', KEYS[1])
if not stored then
    return -2
end

local attempts = tonumber(redis.call('GET', KEYS[2]) or '0')
if attempts >= tonumber(ARGV[2]) then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[2])
    return -1
end

if stored == ARGV[1] then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[2])
    return 1
end

local current = redis.call('INCR', KEYS[2])
if current == 1 then
    redis.call('EXPIRE', KEYS[2], ARGV[3])
end

if current >= tonumber(ARGV[2]) then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[2])
    return -1
end

return 0
