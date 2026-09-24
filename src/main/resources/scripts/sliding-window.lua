local key = KEYS[1]
local now = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local requestLimit = tonumber(ARGV[3])
local memberId = ARGV[4]

-- 1. Remove expired
redis.call("ZREMRANGEBYSCORE", key, "-inf", now - windowSeconds)
-- 2. Count
local count = redis.call("ZCARD", key)

-- 3. Prepare resetTime
local oldest = redis.call("ZRANGE", key, 0, 0, "WITHSCORES")
local resetTime = windowSeconds
if oldest and oldest[1] then
    local oldestScore = tonumber(oldest[2])
    resetTime = math.max(1, oldestScore + windowSeconds - now)
end

if count < requestLimit then
    -- Add new entry
    redis.call("ZADD", key, now, memberId)
    redis.call("EXPIRE", key, windowSeconds)

    local updatedCount = count + 1
    local remaining = requestLimit - updatedCount

    return {1, remaining, resetTime}
else
    return {0, 0, resetTime}
end
