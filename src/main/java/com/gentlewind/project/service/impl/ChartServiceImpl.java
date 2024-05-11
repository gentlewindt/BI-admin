package com.gentlewind.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gentlewind.project.common.ErrorCode;
import com.gentlewind.project.constant.CommonConstant;
import com.gentlewind.project.constant.RedisConstant;
import com.gentlewind.project.exception.BusinessException;
import com.gentlewind.project.model.dto.chart.ChartQueryRequest;
import com.gentlewind.project.model.entity.Chart;
import com.gentlewind.project.service.ChartService;
import com.gentlewind.project.utils.BloomUtils;
import com.gentlewind.project.utils.RedisUtils;
import com.gentlewind.project.utils.mapper.ChartMapper;
import com.google.common.hash.BloomFilter;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
        implements ChartService {
    @Resource
    @Lazy
    private ChartService chartService;

    @Resource
    private RedisTemplate redisTemplate;



    @Override
    public Page<Chart> getChartPageByRedis(ChartQueryRequest chartQueryRequest) {
        long pageNum = chartQueryRequest.getPageNum();
        long pageSize = chartQueryRequest.getCurrent();
        // 第一个参数：刚创建的Page对象，它告诉服务我们想要查询哪一页以及每页有多少条记录。
        // 第二个参数：QueryWrapper包含了根据chartQueryRequest构建的查询条件、排序规则
        // 第三个参数：：chartService.page(...) 方法执行分页查询，返回一个Page<Chart>对象，
        // 其中包含查询结果的列表（List<Chart>）以及关于分页的元数据（如总记录数、总页数等）
        Page<Chart> result = chartService.page(new Page<>(pageNum, pageSize), getQueryWrapper(chartQueryRequest));
        // 获取当前登录用户的Id
        Long userId = chartQueryRequest.getUserId();
        // 每个用户的图表都是不一样的，所以拼接userId，就是唯一的
        String pageUser = RedisConstant.CACHE_USER_PAGE + userId;
        // 根据需要查询的当前页码和每页大小拼接
        String userPageArg = RedisConstant.CACHE_PAGE_ARG + pageNum + ":" + pageSize;
        // 拿到缓存中的数据
        Object userPageInfoObj = redisTemplate.opsForHash().get(pageUser, userPageArg);

        /**
         * 使用缓存空对象解决缓存穿透
         *
         * 1 . 如果获取到的缓存数据为空字符串，抛出自定义异常ErrorCode.NOT_FOUND_ERROR。
         * 2 . 如果获取的缓存数据为空，则执行数据库查询操作。
         *      2.1  如果查询结果为空，设置缓存数据为空字符串，并设置过期时间为5分钟，并抛出自定义异常ErrorCode.NOT_FOUND_ERROR。
         *      2.2 如果查询结构不为空，将查询结果存储到缓存中，并设置过期时间为5分钟。
         * 3 . 再次从Redis中获取用户缓存数据。
         * 4 . 将获取到的缓存数据转换成Page对象并返回。
         */
        if ("".equals(userPageInfoObj)) {// userPageInfoObj的内容是空字符串
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        if (userPageInfoObj == null) {
            // 执行查询数据库操作
            Page<Chart> chartPage = chartService.page(new Page<>(pageNum, pageSize), getQueryWrapper(chartQueryRequest));

            if (chartPage.getRecords().isEmpty()) {
                // 如果查询结果为空，则设置一个空字符串，避免频繁查询数据库
                redisTemplate.opsForHash().put(pageUser, userPageArg, "");
                // 设置过期时间，防止缓存在长时间内一直为空集合而无法更新
                redisTemplate.expire(pageUser, RedisConstant.CACHE_MINUTES_FIVE, TimeUnit.MINUTES);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
            }

            // 存储结果到缓存中
            redisTemplate.opsForHash().put(pageUser, userPageArg, result);
            redisTemplate.expire(pageUser, RedisConstant.CACHE_MINUTES_FIVE, TimeUnit.MINUTES);
            userPageInfoObj = redisTemplate.opsForHash().get(pageUser, userPageArg);
        }
        // 将对象转换成Page对象
        Page<Chart> cacheResult = RedisUtils.convertToPage(userPageInfoObj, Chart.class);
        return cacheResult;
    }

    /**
     * redis缓存分页查询，使用布隆过滤器解决缓存穿透问题
     * @param chartQueryRequest
     * @return
     */
    @Override
    public Page<Chart> getChartPageByRedisBl(ChartQueryRequest chartQueryRequest) {
        long pageNum = chartQueryRequest.getPageNum();
        long pageSize = chartQueryRequest.getCurrent();
        Page<Chart> result = chartService.page(new Page<>(pageNum, pageSize), getQueryWrapper(chartQueryRequest));
        // 获取当前登录用户的Id
        Long userId = chartQueryRequest.getUserId();
        // 每个用户的图表都是不一样的，所以拼接userId，就是唯一的
        String pageUser = RedisConstant.CACHE_USER_PAGE + userId;
        // 根据需要查询的当前页码和每页大小拼接
        String userPageArg = RedisConstant.CACHE_PAGE_ARG + pageNum + ":" + pageSize;

        // 预估元素数量和期望的误报率
        long expectedInsertions = 1_000_000L; // 预计要插入的元素数量
        double fpp = 0.01; // 期望的误报率，这里是1%

        // 初始化布隆过滤器
        BloomFilter<Chart> chartBloomFilter = BloomFilter.create(new BloomUtils(), expectedInsertions, fpp);

        // 数据预热：假设从数据库获取所有Chart对象
        List<Chart> allCharts = chartService.list(getQueryWrapper(chartQueryRequest)); // 假设此方法存在并能获取所有Chart

        // 预加载数据到布隆过滤器
        for (Chart chart : allCharts) {
            chartBloomFilter.put(chart);
        }

        // 构造（获取）布隆过滤器对象
        RBloomFilter<Object> myChartRBloom = Redisson.create().getBloomFilter("MyChart");
        // 先通过布隆过滤器判断该查询是否存在于缓存中
        boolean existInCache = myChartRBloom.contains(userPageArg);
        if (!existInCache) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 先从缓存中获取数据
        Object userPageInfoObj = redisTemplate.opsForHash().get(pageUser, userPageArg);
        // 将对象转换成Page对象
        Page<Chart> cacheResult = RedisUtils.convertToPage(userPageInfoObj, Chart.class);
        return cacheResult;


    }




    @Override
    public QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        // 创建一个QueryWrapper对象
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        // 空值检查：如果chartQueryRequest为null，则直接返回一个空的QueryWrapper，因为没有条件可以应用
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String chartName = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        // 获取当前用户id
        Long curUserId = chartQueryRequest.getUserId();
        // 声明排序字段和排序方式
        String sortField = "create_time";
        String sortOrder = CommonConstant.SORT_ORDER_ASC;
        // 添加过滤条件
        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(chartName), "chartName", chartName);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(userId != null, "userId", userId);
        // 添加当前用户id的限制
        queryWrapper.eq(curUserId != null, "userId", curUserId);
        // 添加排序条件
        queryWrapper.orderBy(true, sortOrder == CommonConstant.SORT_ORDER_DESC, sortField);
        return queryWrapper;
    }

}





