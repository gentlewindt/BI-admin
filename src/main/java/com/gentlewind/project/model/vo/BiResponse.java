package com.gentlewind.project.model.vo;

import lombok.Data;

/**
 * 封装返回给前端的图表数据
 */
@Data
public class BiResponse   {
    private String genChart;

    private String genResult;

    private Long chartId;

}
