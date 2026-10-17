-- 短信发送限流（原子执行）
-- KEYS[1] 手机号冷却 key
-- KEYS[2] 手机号当日发送计数 key
-- KEYS[3] IP 小时发送计数 key
-- ARGV[1] 发送间隔（秒）
-- ARGV[2] 单手机号每日上限
-- ARGV[3] 单 IP 每小时上限
-- ARGV[4] 每日计数 TTL（秒）
-- ARGV[5] IP 计数 TTL（秒）
-- 返回：1 允许；0 冷却中；-1 手机号当日超限；-2 IP 超限

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

local daily = tonumber(redis.call('GET', KEYS[2]) or '0')
if daily >= tonumber(ARGV[2]) then
    return -1
end

local ip = tonumber(redis.call('GET', KEYS[3]) or '0')
if ip >= tonumber(ARGV[3]) then
    return -2
end

if daily == 0 then
    redis.call('SET', KEYS[2], 1, 'EX', ARGV[4])
else
    redis.call('INCR', KEYS[2])
end

if ip == 0 then
    redis.call('SET', KEYS[3], 1, 'EX', ARGV[5])
else
    redis.call('INCR', KEYS[3])
end

redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
return 1
