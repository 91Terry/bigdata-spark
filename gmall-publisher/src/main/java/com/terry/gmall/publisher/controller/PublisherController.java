package com.terry.gmall.publisher.controller;

import com.alibaba.fastjson.JSON;
import com.terry.gmall.publisher.service.impl.DauServiceImpl;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController //@Controller发布网页 RestController发布数据
public class PublisherController {
    @Autowired
    DauServiceImpl dauService ;
    @RequestMapping("hello") //定义方法的访问路径
    public String getHelloWorld(@RequestParam("name") String name){//接受请求中的参数
        //调用后台 利用参数查询数据库 通过数据库返回的结果整理成response的结果
        String info = dauService.getDate(name);

        return name+":"+info;
    }

    //确定请求格式和返回结果的格式
    @RequestMapping("/realtime-total")
    public String getRealtimeTotal(@RequestParam("date") String date){
        date = date.replace("-", "");
        Long total = dauService.getTotal(date);
        String json ="[{\"id\":\"dau\",\"name\":\"新增日活\",\"value\":"+total+"},\n" +
                "{\"id\":\"new_mid\",\"name\":\"新增设备\",\"value\":233} ]\n";
        return json;
    }

    @RequestMapping("/realtime-hour")
    public String getRealtimeHour(@RequestParam("id") String id,@RequestParam("date") String date ){
        if ("dau".equals(id)){
            Map hourCountTdMap = dauService.getHourCount(date);
            String yd = getYd(date);
            Map hourCountYdMap = dauService.getHourCount(yd);
            Map<String,Map<String,Long>> rsMap = new HashMap<>();
            rsMap.put("today",hourCountTdMap);
            rsMap.put("yesterday",hourCountYdMap);
            return JSON.toJSONString(rsMap);
        }else {
            return "no this id";
        }
    }

    private String getYd(String td){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date date = simpleDateFormat.parse(td);
            Date ydDate = DateUtils.addDays(date, -1);
            return simpleDateFormat.format(ydDate);
        } catch (ParseException e) {
            e.printStackTrace();
            throw new RuntimeException("日期转换失败");
        }
    }
}
