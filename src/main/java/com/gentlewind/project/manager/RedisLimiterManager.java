package com.gentlewind.project.manager;

import com.gentlewind.project.common.ErrorCode;
import com.gentlewind.project.exception.BusinessException;
import org.apache.poi.ss.formula.functions.Rate;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 专门提供 RedisLimiter 限流基础服务的（提供了通用的能力,放其他项目都能用）
 */
@Service
public class RedisLimiterManager {
    @Resource
    private RedissonClient redissonClient;


    /**
     *  限流操作
     *
     * @param key 区分不同的限流器，比如不同的用户id应该分别统计请求次数
     */
    public void doRateLimit(String key) {
        // 创建一个名称为key的限流器，每秒最多访问2次
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // 限流器的统计规则（每秒2个请求,连续的请求最多只有1个请求被允许通过）
        // RateType.OVERALL表示速率限制作用于整个令牌桶，即限制所有请求的速率
        rateLimiter.trySetRate(RateType.OVERALL,2,1, RateIntervalUnit.SECONDS);

        // 每当一个操作来了后，请求一个令牌
        boolean canOp = rateLimiter.tryAcquire(1);

        // 如果没有令牌，还想执行操作，就抛出异常
        if(!canOp){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST);
        }


    }

}
