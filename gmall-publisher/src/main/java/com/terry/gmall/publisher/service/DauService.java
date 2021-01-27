package com.terry.gmall.publisher.service;

import java.util.Map;

public interface DauService {

    public String getDate(String name);

    public Long getTotal(String date);

    public Map getHourCount(String date);
}
