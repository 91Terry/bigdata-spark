package com.terry.gmall.realtime.utils

import java.io.IOException
import java.util
import java.util.Properties

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Put, Table}
import org.apache.hadoop.hbase.util.Bytes

object HbaseUtil {

  var connection:Connection = null

  private val properties: Properties = PropertiesUtil.load("config.properties")
  val HBASE_SERVER: String = properties.getProperty("hbase.server")
  val NAMESPACE: String = properties.getProperty("hbase.namespace")
  val DEFAULT_FAMILY: String = properties.getProperty("hbase.default.family")

  def put(tableName:String,rowKey:String,columnValueMap:java.util.Map[String,AnyRef]): Unit ={

    try{
      if (connection == null) init() //连接
      val table: Table = connection.getTable(TableName.valueOf(NAMESPACE + ":" + tableName))
      //
      val puts: util.List[Put] = new util.ArrayList[Put]()
      import collection.JavaConverters._
      for (colValue <- columnValueMap.asScala) {
        val col: String = colValue._1
        val value: AnyRef = colValue._2
        if (value != null && value.toString.length > 0) {
          val put: Put = new Put(Bytes.toBytes(rowKey)).addColumn(Bytes.toBytes(DEFAULT_FAMILY), Bytes.toBytes(col), Bytes.toBytes(value.toString))
          puts.add(put)
        }
      }
      table.put(puts)
    }catch {
      case e: IOException =>
        e.printStackTrace()
        throw new RuntimeException("写入数据失败")
    }

  }

  def init(): Unit ={
    val configuration: Configuration = HBaseConfiguration.create()
    configuration.set("hbase.zookeeper.quorum",HBASE_SERVER)
    connection=ConnectionFactory.createConnection(configuration)
  }

}
