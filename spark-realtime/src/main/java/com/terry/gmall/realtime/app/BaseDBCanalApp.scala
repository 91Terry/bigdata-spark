package com.terry.gmall.realtime.app

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import com.terry.gmall.realtime.utils.{HbaseUtil, MykafkaSink, MykafkaUtil, OffsetManagerUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkConf
import org.apache.commons.lang3.StringUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}

object BaseDBCanalApp {
  def main(args: Array[String]): Unit = {

    val sparkConf: SparkConf = new SparkConf().setMaster("local[4]").setAppName("base_db_canal_app")
    val ssc = new StreamingContext(sparkConf,Seconds(5))
    val topic = "ODS_BASE_DB_C"
    val groupid = "base_db_canal_app_group"

    //读取偏移量 的初始值
    val offsetMap: Map[TopicPartition, Long] = OffsetManagerUtil.getOffset(topic,groupid)
    var inputDStream:InputDStream[ConsumerRecord[String, String]] = null
    //如果offsetMap有值，则从指定位置获取数据流，否则从最新位置获取数据流
    if (offsetMap == null){
      inputDStream =  MykafkaUtil.getKafkaStream(topic,ssc,groupid)
    } else {
      inputDStream = MykafkaUtil.getKafkaStream(topic,ssc,offsetMap,groupid)
    }
    //获取偏移量结束点 OffsetRange[]
    var offsetRanges:Array[OffsetRange] = null
    //从数据流中顺带把本批次的偏移量结束点存入全局变量中
    val inputDstreamWithOffsetDstream: DStream[ConsumerRecord[String, String]] = inputDStream.transform { rdd =>
      val hasOffsetRanges: HasOffsetRanges = rdd.asInstanceOf[HasOffsetRanges]
      offsetRanges = hasOffsetRanges.offsetRanges
      rdd
    }

    //把格式转化为jsonObject便于中间的业务处理
    val jsonObjDstream: DStream[JSONObject] = inputDstreamWithOffsetDstream.map(record=>JSON.parseObject(record.value()))

    jsonObjDstream.foreachRDD{rdd=>

      rdd.foreachPartition{jsonItr=>
        val dimTable = Array("user_info","base_province")
        val factTable = Array("order_info","order_detail")

        for (jsonObj <- jsonItr ) {
          val table: String = jsonObj.getString("table")
          val optType: String = jsonObj.getString("type")
          val pkNames: JSONArray = jsonObj.getJSONArray("pkNames")
          val dataArr: JSONArray = jsonObj.getJSONArray("data")

          /*
          将维表写入Hbase
          命令行：put '命名空间:表名','RowKey','列族:列名','列值'
          namespace:GMALL
          teble:DIM+teble
          faimly:INFO
          rowkey：大表（user）需要预分区，需要将rowkey打散，小表（province）不需要
          rowkey:将数据的主键先补全0，填充位数预计是数据上限，然后再反转
           */
          if(dimTable.contains(table)){
            //获得主键
            val pkName: String = pkNames.getString(0)

            import collection.JavaConverters._
            for ( data<- dataArr.asScala ) {
              val dataJsonObj: JSONObject = data.asInstanceOf[JSONObject]
              val pk: String = dataJsonObj.getString(pkName)

              //将数据主键1先补上0，位数预计数数据的上限，再反转
              val rowkey: String =HbaseUtil.getDimRowkey(pk)
              val hbaseTable: String = "DIM_" + table.toUpperCase
              val dataMap: java.util.Map[String, AnyRef] = dataJsonObj.getInnerMap //key= columnName ,value= value

              HbaseUtil.put(hbaseTable, rowkey, dataMap)
            }
          }

          //将事实表写入kafka
          if (factTable.contains(table)){
            //确定操作类型
            var opt:String=null
            if (optType.equals("INSERT")){
              opt = "I"
            }else if(optType.equals("UPDATE")){
              opt = "U"
            }else if(optType.equals("DELETE")){
              opt = "D"
            }

            //topic:ODS+table+操作类型 message：data
            val topic = "DWD_"+table.toUpperCase()+"_"+opt
            //有可能一条canal的数据有多行操作，要将dataArr遍历
            import collection.JavaConverters._
            for ( data<- dataArr.asScala ) {
              val dataJsonObj: JSONObject = data.asInstanceOf[JSONObject]
              Thread.sleep(50)
              MykafkaSink.send(topic,dataJsonObj.toJSONString)
            }

          }
          //println(jsonObj)
        }

      }
      OffsetManagerUtil.saveOffset(topic,groupid,offsetRanges)

    }

    ssc.start()
    ssc.awaitTermination()
  }

}
