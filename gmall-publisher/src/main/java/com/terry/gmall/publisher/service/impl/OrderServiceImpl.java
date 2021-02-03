package com.terry.gmall.publisher.service.impl;

import com.sun.deploy.panel.ITreeNode;
import com.terry.gmall.publisher.service.OrderService;
import io.searchbox.client.JestClient;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.TermsAggregation;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    JestClient jestClient;

    String ORDER_INDEX_PERFIX = "gmall2020_order_wide_";
    String ORDER_INDEX_SUFFIX =  "-query";

    @Override
    public Map getOrderStats(String date, String keyword, int startPageNo, int pageSize) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //查询部分
        searchSourceBuilder.query(new MatchQueryBuilder("sku_name",keyword).operator(Operator.AND));
        //分页
        searchSourceBuilder.size(pageSize);
        searchSourceBuilder.from((startPageNo-1)*pageSize );// 行号=  (页码-1）*每页行数
        //聚合部分
        SumAggregationBuilder sumOrderAmountAgg = AggregationBuilders.sum("order_amount").field("split_total_amount");
        TermsAggregationBuilder ageAggs = AggregationBuilders.terms("groupby_age").field("user_age").subAggregation(sumOrderAmountAgg);
        TermsAggregationBuilder genderAggs = AggregationBuilders.terms("groupby_gender").field("user_gender").size(2).subAggregation(sumOrderAmountAgg);

        searchSourceBuilder.aggregation(ageAggs);
        searchSourceBuilder.aggregation(genderAggs);

        String indexName = ORDER_INDEX_PERFIX+date+ORDER_INDEX_SUFFIX;
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(indexName).addType("_doc").build();

        Map resultMap = new HashMap();
        try {
            SearchResult searchResult = jestClient.execute(search);
            //总数结果
            resultMap.put("total",searchResult.getTotal());
            //明细结果
            List<Map> detailList = new ArrayList<>();
            List<SearchResult.Hit<Map, Void>> hits = searchResult.getHits(Map.class);
            for (SearchResult.Hit<Map, Void> hit : hits) {
                detailList.add(hit.source);
            }
            resultMap.put("detail",detailList);

            //聚合结果 年龄
            Map ageAggMap = new HashMap<>();
            List<TermsAggregation.Entry> ageBuckets = searchResult.getAggregations().getTermsAggregation("groupby_age").getBuckets();
            for (TermsAggregation.Entry ageBucket : ageBuckets) {
                String ageKey = ageBucket.getKey();
                Double orderAmount = ageBucket.getSumAggregation("order_amount").getSum();
                ageAggMap.put(ageKey,orderAmount);
            }
            resultMap.put("ageAgg",ageAggMap);

            //聚合结果 性别
            Map genderAggMap = new HashMap();
            List<TermsAggregation.Entry> genderBuckets = searchResult.getAggregations().getTermsAggregation("groupby_gender").getBuckets();
            for (TermsAggregation.Entry genderBucket : genderBuckets) {
                String genderKey = genderBucket.getKey();
                Double orderAmount = genderBucket.getSumAggregation("order_amount").getSum();
                genderAggMap.put(genderKey,orderAmount);
            }
            resultMap.put("genderAgg",genderAggMap);

            return resultMap;
        }catch (IOException e){
            e.printStackTrace();
            throw new RuntimeException("es查询错误");
        }

    }

}
