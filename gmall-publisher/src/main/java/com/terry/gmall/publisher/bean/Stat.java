package com.terry.gmall.publisher.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;


//定义一个饼图类
@Data  //增加getter setter方法
@AllArgsConstructor //增加全参构造函数
public class Stat {

    List options;
    String title;
}
