package com.terry.gmall.realtime.app

import java.util.Date

import com.alibaba.fastjson.{JSON, JSONObject}
import com.terry.gmall.realtime.bean.{OrderDetail, OrderInfo}
import com.terry.gmall.realtime.utils.{HbaseUtil, MykafkaUtil, OffsetManagerUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkConf
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}

import scala.collection.mutable

object OrderWideApp {
  def main(args: Array[String]): Unit = {

    val sparkConf: SparkConf = new SparkConf().setMaster("local[4]").setAppName("order_wide_app")
    val ssc = new StreamingContext(sparkConf,Seconds(5))
    val orderInfoTopic = "DWD_ORDER_INFO_I"
    val orderDetailTopic = "DWD_ORDER_DETAIL_I"
    val groupid = "order_wide_group"

    //1、读取偏移量 的初始值
    val orderInfoOffsetMap: Map[TopicPartition, Long] = OffsetManagerUtil.getOffset(orderInfoTopic,groupid)
    val orderDetailOffsetMap: Map[TopicPartition, Long] = OffsetManagerUtil.getOffset(orderDetailTopic,groupid)

    var orderInfoInputDStream:InputDStream[ConsumerRecord[String, String]] = null
    var orderDetailInputDStream:InputDStream[ConsumerRecord[String, String]] = null

    //2、如果offsetMap有值，则从指定位置获取数据流，否则从最新位置获取数据流
    if (orderInfoOffsetMap == null){
      orderInfoInputDStream =  MykafkaUtil.getKafkaStream(orderInfoTopic,ssc,groupid)
    } else {
      orderInfoInputDStream = MykafkaUtil.getKafkaStream(orderInfoTopic,ssc,orderInfoOffsetMap,groupid)
    }

    if (orderDetailOffsetMap == null){
      orderDetailInputDStream =  MykafkaUtil.getKafkaStream(orderDetailTopic,ssc,groupid)
    } else {
      orderDetailInputDStream = MykafkaUtil.getKafkaStream(orderDetailTopic,ssc,orderDetailOffsetMap,groupid)
    }

    //3、获取偏移量结束点 OffsetRange[]
    var orderInfoOffsetRanges:Array[OffsetRange] = null  //主表
    //从数据流中顺带把本批次的偏移量结束点存入全局变量中
    val orderInfoInputDstreamWithOffsetDstream: DStream[ConsumerRecord[String, String]] = orderInfoInputDStream.transform { rdd =>
      val hasOffsetRanges: HasOffsetRanges = rdd.asInstanceOf[HasOffsetRanges]
      orderInfoOffsetRanges = hasOffsetRanges.offsetRanges
      rdd
    }

    var orderDetailOffsetRanges:Array[OffsetRange] = null  //明细表
    val orderDetailInputDstreamWithOffsetDstream: DStream[ConsumerRecord[String, String]] = orderDetailInputDStream.transform { rdd =>
      val hasOffsetRanges: HasOffsetRanges = rdd.asInstanceOf[HasOffsetRanges]
      orderDetailOffsetRanges = hasOffsetRanges.offsetRanges
      rdd
    }

    //4、把流转化成便于处理的格式
    //主表
    val orderInfoDstream: DStream[OrderInfo] = orderInfoInputDstreamWithOffsetDstream.map { record =>
      val orderInfo: OrderInfo = JSON.parseObject(record.value(), classOf[OrderInfo])
      val create_time: String = orderInfo.create_time
      val createTimeArr: Array[String] = create_time.split(" ")
      orderInfo.create_date = createTimeArr(0)
      orderInfo.create_hour = createTimeArr(1).split(":")(0)
      orderInfo
    }
    //订单表
    val orderDetailDstream: DStream[OrderDetail] = orderDetailInputDstreamWithOffsetDstream.map { record =>
      val orderDetail: OrderDetail = JSON.parseObject(record.value(), classOf[OrderDetail])
      orderDetail
    }

    //5、用userid查询用户信息
    val orderInfoWithUserDstream: DStream[OrderInfo] = orderInfoDstream.map { orderInfo =>
      val rowKey: String = HbaseUtil.getDimRowkey(orderInfo.user_id.toString)
      val userInfoJsonObj: JSONObject = HbaseUtil.get("DIM_USER_INFO", rowKey)

      val date: Date = userInfoJsonObj.getDate("birthday")
      val userBirthMills: Long = date.getTime
      val curMills: Long = System.currentTimeMillis()
      orderInfo.user_age = ((curMills - userBirthMills) / 1000 / 60 / 60 / 24 / 365).toInt
      orderInfo.user_gender = userInfoJsonObj.getString("gender")
      orderInfo
    }

//    //合并省市信息
//    val provinceMap: mutable.Map[String, JSONObject] = HbaseUtil.scanTable("DIM_BASE_PROVINCE")
//    //封装入广播变量
//    val provinceBC: Broadcast[mutable.Map[String, JSONObject]] = ssc.sparkContext.broadcast(provinceMap)
//
//    orderInfoWithUserDstream.map{orderInfo =>
//      //展开广播变量
//      val provinceMap: mutable.Map[String, JSONObject] = provinceBC.value
//      val provinceObj: JSONObject = provinceMap.getOrElse(HbaseUtil.getDimRowkey(orderInfo.province_id.toString),null)
//      orderInfo.province_name = provinceObj.getString("name")
//    }

    //如果省市表该变，流数据也可周期性改变
    val orderInfoWithDimDstream: DStream[OrderInfo] = orderInfoWithUserDstream.transform { rdd =>
      //合并省市信息
      val provinceMap: mutable.Map[String, JSONObject] = HbaseUtil.scanTable("DIM_BASE_PROVINCE")
      //封装入广播变量
      val provinceBC: Broadcast[mutable.Map[String, JSONObject]] = ssc.sparkContext.broadcast(provinceMap)

      val orderInfoRDD: RDD[OrderInfo] = rdd.map { orderInfo =>
        //展开广播变量
        val provinceMap: mutable.Map[String, JSONObject] = provinceBC.value
        val provinceObj: JSONObject = provinceMap.getOrElse(HbaseUtil.getDimRowkey(orderInfo.province_id.toString), null)

        orderInfo.province_name = provinceObj.getString("name")
        orderInfo.province_area_code = provinceObj.getString("area_code") //省市行政区域码dataV
        orderInfo.province_iso_code = provinceObj.getString("iso_code") //国际编码（旧）superSet
        orderInfo.province_3166_2_code = provinceObj.getString("iso_3166_2") //国际编码（新）kibana
        orderInfo
      }
      orderInfoRDD

    }

    //流join
    //1 把流改为k-v tuple2结构 2 进行join操作  得到合并的元组
    val orderInfoWithIdDstream: DStream[(Long, OrderInfo)] = orderInfoWithDimDstream.map(orderInfo =>(orderInfo.id,orderInfo))
    val orderDetailWithIdDstream: DStream[(Long, OrderDetail)] = orderDetailDstream.map(orderDetail => (orderDetail.order_id,orderDetail))
    //shuffe
    val orderJoinDstream: DStream[(Long, (OrderInfo, OrderDetail))] = orderInfoWithIdDstream.join(orderDetailWithIdDstream)


    orderJoinDstream.print(1000)
    ssc.start()
    ssc.awaitTermination()

  }

}
