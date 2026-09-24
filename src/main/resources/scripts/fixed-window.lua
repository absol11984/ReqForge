-- INCR and set EXPIRE or just INCR and check for 1
local key = KEYS[1]
local windowSeconds = tonumber(ARGV[1])
local count = redis.call("INCR", key)
if count == 1 then
    redis.call("EXPIRE", key, windowSeconds)
end
return count
