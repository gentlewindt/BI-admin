package com.gentlewind.project.constant;

/**
 * 通用常量
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public interface RedisConstant {

    String CHART_LIST_CACHE_KEY = "MoonBI:chartlist";
    //缓存时间为30分钟
    Long CHART_LIST_CACHE_TIME = 30L;
    String CHART_CHCHE_ID="MoonBI:Chart:id:";
    Long CHART_CACHE_TIME = 30L;

    String CACHE_USER_PAGE = "User:Chart:id:";

    String CACHE_PAGE_ARG = "User:Chart:id:Arg:";

    Long CACHE_MINUTES_FIVE = 5L;

}
