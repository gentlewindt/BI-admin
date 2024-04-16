package com.gentlewind.project.utils;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 处理excel的工具类，用于将excel转成csv格式（一种以逗号为主的文件格式）
 */
@Slf4j
public class ExcelUtils {
    public static String excelToCsv(MultipartFile multipartFile) {
//        File file = null;
//
//        // 获取文件
//        try {
//            file = ResourceUtils.getFile("classpath:网站数据.xlsx");
//        } catch (FileNotFoundException e) {
//            e.printStackTrace(); // 打印异常堆栈跟踪信息
//        }

        // 读取数据
        // 使用Easyexcel库读取该文件
        List<Map<Integer, String>> list = null;
        try {
            list = EasyExcel.read(multipartFile.getInputStream()) // Map<Integer, String>使用键值对存储每行数据，键为列索引，值为单元格内容; List<>有序列表，每一个元素都是一个映射
                    .excelType(ExcelTypeEnum.XLSX) // 指定文件类型为xlsx
                    .sheet()    // 读取第一个工作表
                    .headRowNumber(0) // 指定读取第0行作为表头
                    .doReadSync();  // 同步阻塞式读取 Excel 文件。调用此方法后，程序会立即开始读取操作，并在完成整个读取过程后返回。在此期间，线程会一直等待直到所有数据读取完毕，不会进行其他任务。
        } catch (IOException e) {
            log.error("表格处理错误",e);
        }

        // 如果数据为空
        if(CollUtil.isEmpty(list)){
            return "";
        }
        StringBuilder  stringBuilder = new StringBuilder();

        // 压缩数据（转换为csv格式）
        // 读取表头（第一行）
        LinkedHashMap< Integer, String > headerMap = (LinkedHashMap) list.get(0); // 获取表头元素，因为存放的是映射并且是有序的，所以使用LinkedHashMap（LinkedHashMap会把元素按照插入顺序排序）
        List<String> headerList = headerMap.values() // 获取出映射中所有的value，存入list
                .stream()   // 转换为Stream流
                .filter(ObjectUtils::isNotEmpty) // 过滤每个元素，仅保留非空元素（注意StringUtils.isNotEmpty(String str)方法会返回null，而ObjectUtils.isNotEmpty(Object obj)方法会返回false）
                .collect(Collectors.toList());  // 收集过滤出来的流，转换为新的list
        System.out.println(StringUtils.join(headerList,",")); // 使用逗号拼接每个value
        // 读取数据(读取完表头之后，从第一行开始读取)
        for (int i = 1; i < list.size(); i++) {
            LinkedHashMap<Integer, String> dataMap = (LinkedHashMap) list.get(i);
            List<String> dataList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList()); //
            stringBuilder.append(StringUtils.join(dataList , ",")).append("/n"); // append 方法用于在字符串末尾添加一个字符串，并返回一个新的字符串。
        }
       return stringBuilder.toString();
    }

    public static void main(String[] args) {
        excelToCsv(null);
    }
}