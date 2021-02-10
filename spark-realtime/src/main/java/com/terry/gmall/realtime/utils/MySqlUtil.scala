package com.terry.gmall.realtime.utils

import java.sql.{Connection, DriverManager, ResultSet, ResultSetMetaData, Statement}

import com.alibaba.fastjson.JSONObject

object  MySqlUtil {

  def main(args: Array[String]): Unit = {
    val list: java.util.List[JSONObject] = queryList("select * from user_info")
    println(list)
  }

  def queryList(sql: String): java.util.List[JSONObject] = {
    Class.forName("com.mysql.jdbc.Driver")
    val resultList: java.util.List[JSONObject] = new java.util.ArrayList[JSONObject]()
    val conn: Connection = DriverManager.getConnection("jdbc:mysql://hadoop102:3306/gmall?characterEncoding=utf-8&useSSL=false", "root", "admin")
    val stat: Statement = conn.createStatement
    println(sql)
    val rs: ResultSet = stat.executeQuery(sql)
    val md: ResultSetMetaData = rs.getMetaData
    while (rs.next) {
      val rowData = new JSONObject();
      for (i <- 1 to md.getColumnCount) {
        rowData.put(md.getColumnName(i), rs.getObject(i))
      }
      resultList.add(rowData)
    }

    stat.close()
    conn.close()
    resultList
  }
}

