package com.gentlewind.project.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gentlewind.project.constant.CommonConstant;
import com.gentlewind.project.constant.FileConstant;
import com.gentlewind.project.constant.UserConstant;
import com.gentlewind.project.manager.AiManager;
import com.gentlewind.project.manager.RedisLimiterManager;
import com.gentlewind.project.model.dto.chart.*;
import com.gentlewind.project.model.dto.file.UploadFileRequest;
import com.gentlewind.project.model.enums.FileUploadBizEnum;
import com.gentlewind.project.model.vo.BiResponse;
import com.gentlewind.project.utils.ExcelUtils;
import com.google.gson.Gson;
import com.gentlewind.project.annotation.AuthCheck;
import com.gentlewind.project.common.BaseResponse;
import com.gentlewind.project.common.DeleteRequest;
import com.gentlewind.project.common.ErrorCode;
import com.gentlewind.project.common.ResultUtils;
import com.gentlewind.project.exception.BusinessException;
import com.gentlewind.project.exception.ThrowUtils;
import com.gentlewind.project.model.entity.Chart;
import com.gentlewind.project.model.entity.User;
import com.gentlewind.project.service.ChartService;
import com.gentlewind.project.service.UserService;
import com.gentlewind.project.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * 帖子接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    // 自动注入线程池的实例
    private ThreadPoolExecutor threadPoolExecutor;

    private final static Gson GSON = new Gson();

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }


    /**
     * 智能分析（同步）
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 参数校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");// 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");// 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示

        /**
         *    校验文件（保证系统安全）
         *
         *    先拿到原始文件的大小和文件名
         */
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();

        /**
         *  1. 校验文件大小
         *
         *  定义一个常量1MB
         *  1MB = 1024*1024B
         */
        final long ONE_MB = 1024 * 1024L;
        // 文件大小大于一兆，则抛出异常
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1M");

        /**
         *  2. 校验文件后缀
         *
         *  利用FileUtil工具类中的getSuffix方法获取文件后缀名
         */
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls"); // 定义合法的后缀列表
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法"); // 如果后缀不在List的范围内，则抛出异常

        // 通过response对象拿到用户id（必须登录才能使用）
        User loginUser = userService.getLoginUser(request);

        /**
         *  限流判断
         */
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

        // 指定一个模型id
        long biModelId = 1659171950288818178L;

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        // 添加图标类型
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用：" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据").append("\n");
        // 压缩数据后传入
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");


        // 调用AI方法，并拿到返回结果
        String result = aiManager.doChat(biModelId, userInput.toString());

        // 堆返回结果进行拆分，按照5个中括号进行拆分成三个部分
        String[] splits = result.split("【【【【【");

        // 拆分后进行校验
        if (splits.length < 3) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI生成错误");
        }


        String genChart = splits[1].trim(); // .trim() 是一个字符串方法，用于移除该字符串首尾的所有空白字符（如空格、制表符、换行符等）。
        String genResult = splits[2].trim();
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());

        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(loginUser.getId());

        return ResultUtils.success(biResponse);

    }
        /**
         * 智能分析（异步）
         *
         * @param multipartFile
         * @param genChartByAiRequest
         * @param request
         * @return
         */
        @PostMapping("/gen/async")
        public BaseResponse<BiResponse> genChartByAiAsync (@RequestPart("file") MultipartFile multipartFile,
                GenChartByAiRequest genChartByAiRequest, HttpServletRequest request){
            String name = genChartByAiRequest.getName();
            String goal = genChartByAiRequest.getGoal();
            String chartType = genChartByAiRequest.getChartType();

            // 校验
            ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
            ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

            // 校验文件
            long size = multipartFile.getSize();
            String originalFilename = multipartFile.getOriginalFilename();

            // 校验文件大小
            final long ONE_MB = 1024 * 1024L;
            ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");

            // 校验文件大小缀 aaa.png
            String suffix = FileUtil.getSuffix(originalFilename);
            final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
            ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

            User loginUser = userService.getLoginUser(request);

            // 限流判断，每个用户一个限流器
            redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

            // 指定一个模型id(把id写死，也可以定义成一个常量)
            long biModelId = 1659171950288818178L;
            // 分析需求：
            // 分析网站用户的增长情况
            // 原始数据：
            // 日期,用户数
            // 1号,10
            // 2号,20
            // 3号,30

            // 构造用户输入
            StringBuilder userInput = new StringBuilder();
            userInput.append("分析需求：").append("\n");

            // 拼接分析目标
            String userGoal = goal;
            if (StringUtils.isNotBlank(chartType)) {
                userGoal += "，请使用" + chartType;
            }
            userInput.append(userGoal).append("\n");
            userInput.append("原始数据：").append("\n");

            // 压缩后的数据
            String csvData = ExcelUtils.excelToCsv(multipartFile);
            userInput.append(csvData).append("\n");

            // 先把图表保存到数据库中
            Chart chart = new Chart();
            chart.setName(name);
            chart.setGoal(goal);
             chart.setChartData(csvData);
            chart.setChartType(chartType);
            // 插入数据库时,还没生成结束,把生成结果都去掉
            //        chart.setGenChart(genChart);
            //        chart.setGenResult(genResult);
            // 设置任务状态为排队中
            chart.setStatus("wait");
            chart.setUserId(loginUser.getId());
            boolean saveResult = chartService.save(chart);
            ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

            // 在最终的返回结果前提交一个任务
            // todo 建议处理任务队列满了后,抛异常的情况(因为提交任务报错了,前端会返回异常)
            CompletableFuture.runAsync(() -> {
                // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。(为了防止同一个任务被多次执行)
                Chart updateChart = new Chart();
                updateChart.setId(chart.getId());
                // 把任务状态改为执行中
                updateChart.setStatus("running");
                boolean b = chartService.updateById(updateChart);
                // 如果提交失败(一般情况下,更新失败可能意味着你的数据库出问题了)
                if (!b) {
                    handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
                    return;
                }

                // 调用 AI
                String result = aiManager.doChat(biModelId, userInput.toString());
                String[] splits = result.split("【【【【【");
                if (splits.length < 3) {
                    handleChartUpdateError(chart.getId(), "AI 生成错误");
                    return;
                }
                String genChart = splits[1].trim();
                String genResult = splits[2].trim();
                // 调用AI得到结果之后,再更新一次
                Chart updateChartResult = new Chart();
                updateChartResult.setId(chart.getId());
                updateChartResult.setGenChart(genChart);
                updateChartResult.setGenResult(genResult);
                updateChartResult.setStatus("succeed"); // 执行成功
                boolean updateResult = chartService.updateById(updateChartResult);
                if (!updateResult) {
                    handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
                }
            }, threadPoolExecutor);

            BiResponse biResponse = new BiResponse();
            //        biResponse.setGenChart(genChart);
            //        biResponse.setGenResult(genResult);
            biResponse.setChartId(chart.getId());
            return ResultUtils.success(biResponse);
        }
        // 上面的接口很多用到异常,直接定义一个工具类
        private void handleChartUpdateError( long chartId, String execMessage){
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chartId);
            updateChartResult.setStatus("failed");
            updateChartResult.setExecMessage(execMessage);
            boolean updateResult = chartService.updateById(updateChartResult);
            if (!updateResult) {
                log.error("更新图表失败状态失败" + chartId + "," + execMessage);
            }
        }

//        // 处理上传的excel文件
//        User loginUser = userService.getLoginUser(request);// 获取登录用户信息
//        String uuid = RandomStringUtils.randomAlphanumeric(8);   // 生成uuid（唯一标识符）：随机生成八位包含大小写字母和数字的字符串
//        String filename = uuid + "-" + multipartFile.getOriginalFilename(); // 生成文件名：将uuid和原始文件名拼接起来，得到最终的文件名
//        File file = null;
//        try {
//
//            // 返回可访问地址
//            return ResultUtils.success("");
//        } catch (Exception e) {
//            //            log.error("file upload error, filepath = " + filepath, e);
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
//        } finally {
//            if (file != null) {
//                // 删除临时文件
//                boolean delete = file.delete();
//                if (!delete) {
//                    //                    log.error("file delete error, filepath = {}", filepath);
//                }
//            }
//        }


        /**
         * 获取查询包装类
         *
         * @param chartQueryRequest
         * @return
         */
        private QueryWrapper<Chart> getQueryWrapper (ChartQueryRequest chartQueryRequest){
            QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
            if (chartQueryRequest == null) {
                return queryWrapper;
            }
            Long id = chartQueryRequest.getId();
            String name = chartQueryRequest.getName();
            String goal = chartQueryRequest.getGoal();
            String chartType = chartQueryRequest.getChartType();
            Long userId = chartQueryRequest.getUserId();
            String sortField = chartQueryRequest.getSortField();
            String sortOrder = chartQueryRequest.getSortOrder();

            queryWrapper.eq(id != null && id > 0, "id", id);
            queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
            queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
            queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
            queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
            queryWrapper.eq("isDelete", false);
            queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                    sortField);
            return queryWrapper;
        }


    }
