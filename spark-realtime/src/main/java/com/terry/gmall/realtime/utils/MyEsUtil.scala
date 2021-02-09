package com.terry.gmall.realtime.utils

import java.util
import java.util.Properties

import io.searchbox.client.config.HttpClientConfig
import io.searchbox.client.{JestClient, JestClientFactory}
import io.searchbox.core.{Bulk, BulkResult, Index, Search, SearchResult}
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.elasticsearch.search.sort.SortOrder

object MyEsUtil {
  private var factory: JestClientFactory = null

  //创建jest对象
  def getClient:JestClient = {
    if (factory == null)build()
    factory.getObject

  }

  def build(): Unit ={
    factory = new JestClientFactory
    val properties: Properties = PropertiesUtil.load("config.properties")
    val serverUri: String = properties.getProperty("elasticsearch.server")
    factory.setHttpClientConfig(new HttpClientConfig.Builder(serverUri)
        .multiThreaded(true)
        .maxTotalConnection(20)
        .connTimeout(10000)
        .readTimeout(1000)
        .build())
  }

  def search(): Unit ={
    val jest: JestClient = getClient
    //组织参数
    //方法一:直接输入json的查询语句
    val query = "{\n  \"query\": {\n    \"match\": {\n      \"name\": \"operation red sea\"\n    }\n  },\n  \"sort\": [\n    {\n      \"doubanScore\": {\n        \"order\": \"asc\"\n      }\n    }\n  ],\n  \"size\": 2,\n  \"from\": 1,\n  \"_source\": [\"name\",\"doubanScore\"],\n  \"highlight\": {\"fields\": {\"name\": {}}} \n}"

    //方法二：es官方推荐优雅的结构体对象
    val searchSourceBuilder = new SearchSourceBuilder()
    searchSourceBuilder.query(new MatchQueryBuilder("name","operation red sea"))
    searchSourceBuilder.sort("doubanScore",SortOrder.DESC)
    searchSourceBuilder.size(2)
    searchSourceBuilder.from(0)
    searchSourceBuilder.fetchSource(Array("name","doubanScore"),null)

    val search: Search = new Search.Builder(searchSourceBuilder.toString)
      .addIndex("movie_index")
      .addType("movie").build()
    val result: SearchResult = jest.execute(search)
    val rsList: util.List[SearchResult#Hit[util.Map[String, Any], Void]] = result.getHits(classOf[util.Map[String,Any]])
    import collection.JavaConverters._
    for ( rs <- rsList.asScala ) {
      println(rs.source)
    }
    //接受结果

    jest.close()
  }

  //测试
  def main(args: Array[String]): Unit = {
    search()
  }

  //批量写入ES操作:bulk
  val DEFAULT_TYPE = "_doc"
  def saveBulk(indexName:String, docList:List[(String,Any)]): Unit ={

    val jest: JestClient = getClient
    val bulkBuilder = new Bulk.Builder()
    //加入很多个单行操作
    for ((id,doc) <- docList ) {
      val index = new Index.Builder(doc ).id(id).build()
      bulkBuilder.addAction(index)
    }
    //加入统一保存的索引
    bulkBuilder.defaultIndex(indexName).defaultType(DEFAULT_TYPE)
    val bulk = bulkBuilder.build()
    val items: util.List[BulkResult#BulkResultItem] = jest.execute(bulk).getItems
    println("已保存："+items.size()+"条")
    jest.close()
  }

  //单条写入ES操作
  def save(): Unit ={
    val jest: JestClient = getClient
    val index = new Index.Builder(MovieTest("0102","西游记"))
      .index("movie_test0921")
      .`type`("_doc")
      .build()
    jest.execute(index)
    jest.close()
  }

  case class MovieTest(id:String,movie_name:String)


}
