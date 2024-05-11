package com.gentlewind.project.utils;

import com.gentlewind.project.model.entity.Chart;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;

public class BloomUtils implements Funnel<Chart> {
        // 定义一个Funnel，用于将对象特征转化为字节流，供布隆过滤器使用
        public void funnel(Chart chart, PrimitiveSink into) {
            // 假设Chart有getId()方法，且我们仅基于ID构建布隆过滤器
            into.putLong(chart.getId());
        }
}
