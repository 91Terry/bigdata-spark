package com.terry.gmall.realtime.utils

import java.io.IOException
import java.util
import java.util.Properties

import com.alibaba.fastjson.JSONObject
import org.apache.commons.lang3.StringUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.{Cell, CellUtil, HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Get, Put, Result, ResultScanner, Scan, Table}
import org.apache.hadoop.hbase.util.Bytes

import scala.collection.mutable

object HbaseUtil {

  var connection:Connection = null
  //加载配置参数
  private val properties: Properties = PropertiesUtil.load("config.properties")
  val HBASE_SERVER: String = properties.getProperty("hbase.server")
  val NAMESPACE: String = properties.getProperty("hbase.namespace")
  val DEFAULT_FAMILY: String = properties.getProperty("hbase.default.family")

  //一条一条添加数据
  def put(tableName:String,rowKey:String,columnValueMap:java.util.Map[String,AnyRef]): Unit ={

    try{
      if (connection == null) init() //连接
      val table: Table = connection.getTable(TableName.valueOf(NAMESPACE + ":" + tableName))
      //把columnValueMap制作成一个 Put列表   一起交给table提交执行
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

  //获取数据
  def get(tableName:String,rowKey:String):JSONObject ={
    if(connection == null) init()
    val table: Table = connection.getTable(TableName.valueOf(NAMESPACE + ":" + tableName))
    //创建查询动作
    val get = new Get(Bytes.toBytes(rowKey))
    //执行查询
    val result: Result = table.get(get)
    //转换查询结果
    convertToJSONObj(result)
  }

    //json格式转换
  def convertToJSONObj(result: Result):JSONObject ={
    val cells: Array[Cell] = result.rawCells()
    val jsonObj = new JSONObject()
    for ( cell<- cells ) {
      jsonObj.put(Bytes.toString(CellUtil.cloneQualifier(cell)),Bytes.toString(CellUtil.cloneValue(cell)))
    }
    jsonObj
  }

  //Hbase扫描数据
  def scanTable(tableName:String):mutable.Map[String,JSONObject]={
    if (connection == null) init() //连接
    val table: Table = connection.getTable(TableName.valueOf(NAMESPACE + ":" + tableName))
    val scan = new Scan()
    val resultScanner: ResultScanner = table.getScanner(scan)
    //把整表的结果存入Map中
    val resultMap: mutable.Map[String, JSONObject] = mutable.Map[String,JSONObject]()
    import collection.JavaConverters._
    for (result <- resultScanner.iterator().asScala ) {
      val rowKey: String = Bytes.toString(result.getRow)
      val jsonObj: JSONObject = convertToJSONObj(result)
      resultMap.put(rowKey,jsonObj)
    }
    resultMap
  }

  //维表rowkey还原
  def getDimRowkey(id:String) :String={
    StringUtils.leftPad(id,10,"0").reverse
  }

  //初始化连接
  def init(): Unit ={
    val configuration: Configuration = HBaseConfiguration.create()
    configuration.set("hbase.zookeeper.quorum",HBASE_SERVER)
    connection=ConnectionFactory.createConnection(configuration)
  }

}
