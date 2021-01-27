package com.terry.gmall.publisher.service.impl;

import com.terry.gmall.publisher.service.DauService;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.TermsAggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DauServiceImpl implements DauService {


    @Autowired
    JestClient jestClient;

    public static final String DAU_INDEX_PREFIX = "dau_info";
    public static final String DAU_INDEX_SUFFIX = "-query";
    public static final String DEFAULT_TYPE = "_doc";

    @Override
    public String getDate(String name) {
        return "30,程序员";
    }

    @Override
    public Long getTotal(String date) {
        //查询es
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(0);
        String indexName = DAU_INDEX_PREFIX+date+DAU_INDEX_SUFFIX;
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(indexName).addType(DEFAULT_TYPE).build();
        try {
            SearchResult result = jestClient.execute(search);
            return result.getTotal();

        } catch (IOException e) {
            e.printStackTrace();
            throw  new RuntimeException("查询ES异常");
        }

    }

    @Override
    public Map getHourCount(String date){
        //将2021-01-27转换为20210127，跟es索引相同
        date = date.replace("-","");

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(0);

        //生成es分组统计语法
        TermsAggregationBuilder aggregationBuilder  = AggregationBuilders.terms("groupby_hr").field("hr").size(24);
        searchSourceBuilder.aggregation(aggregationBuilder);
        String indexName = DAU_INDEX_PREFIX + date + DAU_INDEX_SUFFIX;

        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(indexName).addType(DEFAULT_TYPE).build();
        try {
            SearchResult result = jestClient.execute(search);
            //将分组数据转化为Map，K：分时，V：数量
            Map hourMap = new HashMap();
            TermsAggregation termsAggergation = result.getAggregations().getTermsAggregation("groupby_hr");
            //避免空指针
            if (termsAggergation!=null){
                List<TermsAggregation.Entry> buckets = termsAggergation.getBuckets();
                for (TermsAggregation.Entry bucket : buckets) {
                    hourMap.put(bucket.getKey(),bucket.getCount());
                }
            }
            return hourMap;

        } catch (IOException e) {
            e.printStackTrace();
            throw  new RuntimeException("查询ES异常");
        }

    }




}
