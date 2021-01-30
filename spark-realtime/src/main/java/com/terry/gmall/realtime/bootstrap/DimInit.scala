package com.terry.gmall.realtime.bootstrap

import java.util
import java.util.Properties

import com.alibaba.fastjson.{JSONArray, JSONObject}
import com.terry.gmall.realtime.utils.{MySqlUtil, MykafkaSink, PropertiesUtil}

object DimInit {
  //引导程序  把mysql上的维表数据引入kafka
  def main(args: Array[String]): Unit = {
    val properties: Properties = PropertiesUtil.load("diminit.properties")
    val tableNames: String = properties.getProperty("bootstrap.tablenames")
    val topic: String = properties.getProperty("bootstrap.topic")
    val tableNamesArr: Array[String] = tableNames.split(",")
    for (tableName <- tableNamesArr ) {
      //读取mysql
      val dataObjList: util.List[JSONObject] = MySqlUtil.queryList("select * from "+tableName)
      //模仿canal数据格式构造message
      val messageJSONobj = new JSONObject()
      messageJSONobj.put("data",dataObjList)
      messageJSONobj.put("database","gmall")

      val pkNames = new JSONArray()
      pkNames.add("id")

      messageJSONobj.put("pkNames",pkNames)
      messageJSONobj.put("table",tableName)
      messageJSONobj.put("type","INSERT")
      println(messageJSONobj)
      MykafkaSink.send(topic,messageJSONobj.toString())
    }

    // 为什么一定要close 因为：close 执行flush 把在子线程batch中的数据 强制写入kafka
    //否则 在主线程关闭时会结束掉所有守护线程，而kafka的producer就是守护线程 ，会被结束掉有可能会丢失数据。
    MykafkaSink.close()
  }

}
