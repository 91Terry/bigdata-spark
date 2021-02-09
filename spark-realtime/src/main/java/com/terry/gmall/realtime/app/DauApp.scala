package com.terry.gmall.realtime.app


import java.lang
import java.text.SimpleDateFormat
import java.util.Date

import com.alibaba.fastjson.{JSON, JSONObject}
import com.terry.gmall.realtime.bean.DauInfo
import com.terry.gmall.realtime.utils.{MyEsUtil, MykafkaUtil, OffsetManagerUtil, RedisUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import redis.clients.jedis.Jedis

import scala.collection.mutable.ListBuffer

/*
  手动后置提交偏移量
  1、读取偏移量的初始值
  从redis中读取偏移量的数据，偏移量保存在redis中，格式：主题-消费者组-分区-offset
  2、加载数据
  3、获得偏移量的结束点
  4、存储偏移量
 */
object DauApp {
  def main(args: Array[String]): Unit = {
    val sparkConf: SparkConf = new SparkConf().setMaster("local[4]").setAppName("dua_app")
    val ssc = new StreamingContext(sparkConf,Seconds(5))
    val topic = "ods_log"
    val groupid = "dau_app_group"

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

    //把ts转换成日期和小时，后续便于处理
    val jsonStringDstream: DStream[JSONObject] = inputDstreamWithOffsetDstream.map{record =>
      val jsonString: String = record.value()
      val jSONIbject: JSONObject = JSON.parseObject(jsonString)
      //把时间戳转换成日期和小时
      val ts: lang.Long = jSONIbject.getLong("ts")
      val dateHourStr: String = new SimpleDateFormat("yyyy-MM-dd HH").format(new Date(ts))
      val dateHour: Array[String] = dateHourStr.split(" ")
      val date: String = dateHour(0)
      val hour: String = dateHour(1)

      jSONIbject.put("dt",date)
      jSONIbject.put("hr",hour)
      jSONIbject
    }

    //1、筛选出用户最基本的活跃行为（打开第一个页面 (page 项中,没有 last_page_id)）
    val firstPageJsonObjDstram: DStream[JSONObject] = jsonStringDstream.filter { jsonOBJ =>
      var ifFirst = false
      val pageObj: JSONObject = jsonOBJ.getJSONObject("page")
      if (pageObj != null) {
        val lastPageId: String = pageObj.getString("last_page_id")
        if (lastPageId == null) {
          ifFirst = true
        }
      }
      ifFirst
    }

    firstPageJsonObjDstram.cache()
    firstPageJsonObjDstram.count().print()

    /*
    //2、去重，以什么字段为准去重（mid），用redis来存储已访问的列表
    val duaDstream: DStream[JSONObject] = firstPageJsonObjDstram.filter { jsonObj =>
      val mid: String = jsonObj.getJSONObject("common").getString("mid")
      val dt: String = jsonObj.getString("dt")

      val jedis = new Jedis("hadoop102", 6379)
      val key = "dua:" + dt
      val isNew: lang.Long = jedis.sadd(key, mid)
      jedis.expire(key, 3600 * 24)
      jedis.close()

      if (isNew == 1L) {
        true
      } else {
        false
      }

    }
    duaDstream.print(1000)
     */


    //优化：减少redis创建（获取）连接的次数，做成每批次每分区执行一次
    val dauDstream: DStream[JSONObject] = firstPageJsonObjDstram.mapPartitions { jsonObjItr =>
      //按分区执行一次
      val jedis: Jedis = RedisUtil.getJedisClient
      val filteredList: ListBuffer[JSONObject] = ListBuffer[JSONObject]()

      //以条为单位处理
      for (jsonObj <- jsonObjItr) {
        //提取对象的mid
        val mid: String = jsonObj.getJSONObject("common").getString("mid")
        val dt: String = jsonObj.getString("dt")

        //通过redis set数据类型过滤去重
        val key = "dua" + dt
        val isNew: lang.Long = jedis.sadd(key, mid)
        jedis.expire(key, 3600 * 24)

        if (isNew == 1L) {
          filteredList.append(jsonObj)
        }
      }
      jedis.close()
      filteredList.toIterator
    }

    dauDstream.cache()
    dauDstream.print(1000)

    dauDstream.foreachRDD{rdd =>
      rdd.foreachPartition{ jsonObjItr =>
        //批量保存
        val docList: List[JSONObject] = jsonObjItr.toList
        if (docList.size > 0) {
          //获取当前时间戳，转为字符串
          val dateformat = new SimpleDateFormat("yyyyMMdd")
          val dt: String = dateformat.format(new Date())


          val docWithIdList: List[(String, DauInfo)] = docList.map { jsonObj =>
            val commonJsonObj: JSONObject = jsonObj.getJSONObject("common")
            val mid: String = commonJsonObj.getString("mid")
            val uid: String = commonJsonObj.getString("uid")
            val ar: String = commonJsonObj.getString("ar")
            val ch: String = commonJsonObj.getString("ch")
            val vc: String = commonJsonObj.getString("vc")
            val dt: String = jsonObj.getString("dt")
            val hr: String = jsonObj.getString("hr")
            val ts: Long = jsonObj.getLong("ts")
            val dauInfo = DauInfo(mid, uid, ar, ch, vc, dt, hr, ts)
            (mid, dauInfo)
          }
          //给每条数据提供一个id（mid），做到幂等性保存
          //批量保存数据
          MyEsUtil.saveBulk("dau_info" + dt, docWithIdList)
        }
      }

      //在driver存储driver全局数据，每个批次保存一次偏移量
      OffsetManagerUtil.saveOffset(topic,groupid,offsetRanges)
    }

    ssc.start()
    ssc.awaitTermination()
  }

}
