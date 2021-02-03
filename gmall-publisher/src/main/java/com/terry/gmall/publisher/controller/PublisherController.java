package com.terry.gmall.publisher.controller;

import com.alibaba.fastjson.JSON;
import com.terry.gmall.publisher.bean.Option;
import com.terry.gmall.publisher.bean.Stat;
import com.terry.gmall.publisher.service.OrderService;
import com.terry.gmall.publisher.service.impl.DauServiceImpl;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController //@Controller发布网页 RestController发布数据
public class PublisherController {

    @Autowired
    DauServiceImpl dauService ;

    @Autowired
    OrderService orderService;

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

    @RequestMapping("sale_detail")
    public String getSaleDetail(@RequestParam("date")String date,
                                @RequestParam("startpage")int startPageNo,
                                @RequestParam("size")int pageSize,
                                @RequestParam("keyword")String keyword){

        Map orderStatsMap = orderService.getOrderStats(date, keyword, startPageNo, pageSize);
        Long total =(Long)orderStatsMap.get("total");
        List<Map> detailList =(List<Map>)orderStatsMap.get("detail");
        Map ageAgg =(Map)orderStatsMap.get("ageAgg");
        Map genderAgg =(Map)orderStatsMap.get("genderAgg");

        Double orderAmountLT20=0D;
        Double orderAmountGTE20LT30=0D;
        Double orderAmountGTE30=0D;
        Double orderAmountTotal=0D;

        for (Object o : ageAgg.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            String ageKey = (String)entry.getKey();
            Double orderAmount = (Double)entry.getValue();
            Integer age = Integer.valueOf(ageKey);
            if(age<20){
                orderAmountLT20+=orderAmount;
            }else if(age>=20&&age<30){
                orderAmountGTE20LT30+=orderAmount;
            }else{
                orderAmountGTE30+=orderAmount;
            }
            orderAmountTotal+=orderAmount;
        }
        //得到不同年龄段的百分比
        Double orderAmountLT20Ratio=Math.round (orderAmountLT20/orderAmountTotal*1000)/10D;
        Double orderAmountGTE20LT30Ratio=Math.round (orderAmountGTE20LT30/orderAmountTotal*1000)/10D;
        Double orderAmountGTE30Ratio=Math.round (orderAmountGTE30/orderAmountTotal*1000)/10D;

        ArrayList ageOptions = new ArrayList();
        ageOptions.add(new Option("20岁以下",orderAmountLT20Ratio));
        ageOptions.add(new Option("20岁到30岁",orderAmountGTE20LT30Ratio));
        ageOptions.add(new Option("30岁以上",orderAmountGTE30Ratio));
        Stat ageStat = new Stat(ageOptions, "用户年龄占比");


        Double orderAmountMale=0D;
        Double orderAmountFemale=0D;
        for (Object o : genderAgg.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            String genderKey = (String)entry.getKey();
            Double orderAmount = (Double)entry.getValue();
            if(genderKey.equals("M")){
                orderAmountMale+=orderAmount;
            }else{
                orderAmountFemale+=orderAmount;
            }
        }
        //得到不同性别的百分比
        Double orderAmountMaleRatio=Math.round (orderAmountMale/orderAmountTotal*1000)/10D;
        Double orderAmountFemaleRatio=Math.round (orderAmountFemale/orderAmountTotal*1000)/10D;

        ArrayList genderOptions = new ArrayList();
        genderOptions.add(new Option("男",orderAmountMaleRatio));
        genderOptions.add(new Option("女",orderAmountFemaleRatio));

        Stat genderStat = new Stat(genderOptions, "用户性别占比");

        List statList=new ArrayList();
        statList.add(ageStat);
        statList.add(genderStat);


        Map  resultMap = new HashMap<>();
        resultMap.put("total",total);//明细总数
        resultMap.put("detail",detailList);//明细
        resultMap.put("stat",statList);//饼图集

        return JSON.toJSONString(resultMap);
    }
}
