package com.terry.gmall.publisher.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
//定义一个选项类
@Data  //增加getter setter方法
@AllArgsConstructor //增加全参构造函数
public class Option {
    String name ;
    Double value;
}
