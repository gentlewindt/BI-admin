package com.gentlewind.project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gentlewind.project.model.dto.chart.ChartQueryRequest;
import com.gentlewind.project.model.entity.Chart;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *
 */
public interface ChartService extends IService<Chart> {

    /**
     * 根据用户从redis缓存查询分页信息
     * @param chartQueryRequest
     * @return
     */
    Page<Chart> getChartPageByRedis(ChartQueryRequest chartQueryRequest);


    Page<Chart> getChartPageByRedisBl(ChartQueryRequest chartQueryRequest);

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest);



}
