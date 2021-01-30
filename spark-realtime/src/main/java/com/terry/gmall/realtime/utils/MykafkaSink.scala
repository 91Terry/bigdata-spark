package com.terry.gmall.realtime.utils

import java.util.Properties

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

object MykafkaSink {
  private val properties:Properties = PropertiesUtil.load("config.properties")
  val broker_list: String = properties.getProperty("kafka.broker.list")
  var kafkaProducer:KafkaProducer[String,String] = null

  def createKafkaProducer: KafkaProducer[String, String] = {
    val properties = new Properties
    properties.put("bootstrap.servers", broker_list)
    properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    properties.put("enable.idompotence",(true: java.lang.Boolean))
    var producer: KafkaProducer[String, String] = null
    try
      producer = new KafkaProducer[String, String](properties)
    catch {
      case e: Exception =>
        e.printStackTrace()
    }
    producer
  }

  // 轮询分区 2.x  黏性分区3.x
  def send(topic: String, msg: String): Unit = {
    if (kafkaProducer == null) kafkaProducer = createKafkaProducer
    kafkaProducer.send(new ProducerRecord[String, String](topic, msg))

  }

  // 根据 分区键分区
  def send(topic: String,key:String, msg: String): Unit = {
    if (kafkaProducer == null) kafkaProducer = createKafkaProducer
    kafkaProducer.send(new ProducerRecord[String, String](topic,key, msg))

  }

  def close(): Unit ={
    kafkaProducer.close()
  }

}
