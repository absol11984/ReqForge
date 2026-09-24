local key = KEYS[1]
local requestLimit = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local tokensData = redis.call("HGET", key, "tokens")
local lastRefillData = redis.call("HGET", key, "lastRefillSeconds")

local available
local lastRefill

if not tokensData or not lastRefillData then
    available = requestLimit
    lastRefill = now
else
    lastRefill = tonumber(lastRefillData)
    local elapsed = math.max(0, now - lastRefill)
    local refillRate = requestLimit / windowSeconds
    available = math.min(requestLimit, (tonumber(tokensData) or 0) + elapsed * refillRate)
end

local allowed = 0
local resetTime = 0
if available >= 1 then
    allowed = 1
    available = available - 1
    resetTime = 0
else
    -- need 1 token; refillRate = limit/window
    local refillRate = requestLimit / windowSeconds
    resetTime = math.max(1, math.ceil((1 - available) / refillRate))
end

redis.call("HSET", key, "tokens", tostring(available), "lastRefillSeconds", tostring(now))
redis.call("EXPIRE", key, windowSeconds)

local remaining = math.floor(available)

return {allowed, remaining, resetTime}