package com.terry.gmall.publisher.service;

import java.util.Map;


public interface OrderService {
    public Map getOrderStats(String date,String keyword,int startPageNo,int pageSize);
}
