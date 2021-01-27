package com.terry.gmall.realtime.utils

import java.util

import org.apache.kafka.common.TopicPartition
import org.apache.spark.streaming.kafka010.OffsetRange
import redis.clients.jedis.Jedis

object OffsetManagerUtil {
  //读取redis的偏移量
  def getOffset(topic:String,groupId:String): Map[TopicPartition,Long] ={
    val jedis: Jedis = RedisUtil.getJedisClient
    //从redis读取偏移量，选择hash数据类型
    //key: topic+consumer_group
    //field: partition
    //value: offset
    val offsetKey = topic+":"+groupId
    val offsetMapOrgin: util.Map[String, String] = jedis.hgetAll(offsetKey) //k:分区，v:offset
    jedis.close() //关闭

    if (offsetMapOrgin != null && offsetMapOrgin.size()>0 ){
      import collection.JavaConverters._
      //把从redis取出的结构转换成kafka需求的结构（Map[TopicPartition,Long]）
      val offsetMapForKafka: Map[TopicPartition, Long] = offsetMapOrgin.asScala.map {
        case (partitionStr, offsetStr) =>
        val topicPartition: TopicPartition = new TopicPartition(topic, partitionStr.toInt)
        (topicPartition, offsetStr.toLong)
      }.toMap
      println("读取起始位置的偏移量："+offsetMapForKafka)
      offsetMapForKafka
    } else {
      null
    }

  }

  //把偏移量写入redis
  def saveOffset(topic:String,groupId:String,offsetRanges:Array[OffsetRange]): Unit ={
    val jedis: Jedis = RedisUtil.getJedisClient
    //把偏移量存储到redis，选择hash数据类型
    //key: topic+consumer_group
    //field: partition
    //value: offset
    val offsetKey = topic+":"+groupId
    //取分区和偏移量的Map集合
    val offsetMapForRedis = new util.HashMap[String,String]()
    for (offsetRange <- offsetRanges) {
      val partition: Int = offsetRange.partition //取分区
      val offset: Long = offsetRange.untilOffset //取偏移量的结束点
      offsetMapForRedis.put(partition.toString,offset.toString)
    }
    //写入redis
    println("写入偏移量的结束点："+offsetMapForRedis)
    jedis.hmset(offsetKey,offsetMapForRedis)
    jedis.close()
  }
}
