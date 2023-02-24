-- 比较两个value是否相同

if(ARGV[1] == redis.call('get', KEYS[1]))
then
    return redis.call('del',KEYS[1])
end
return 0