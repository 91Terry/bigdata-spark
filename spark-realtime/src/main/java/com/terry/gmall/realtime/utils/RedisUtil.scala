package com.terry.gmall.realtime.utils


import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/**
 Redis连接池
 * date 2021/1/22
 * @since 1.8
 * @author terry
*/
object RedisUtil {
  var jedisPool:JedisPool = null

  def getJedisClient:Jedis = {
    if (jedisPool == null){
      //读取配置文件
      val config = PropertiesUtil.load("config.properties")
      //获取主机名和端口号
      val host: String = config.getProperty("redis.host")
      val port: String = config.getProperty("redis.port")

      //设置redis连接池
      val jedisPoolConfig = new JedisPoolConfig()
      jedisPoolConfig.setMaxTotal(10) //最大连接数
      jedisPoolConfig.setMaxIdle(4) //最大空闲
      jedisPoolConfig.setMinIdle(4) //最小空闲
      jedisPoolConfig.setBlockWhenExhausted(true) //忙碌时是否等待
      jedisPoolConfig.setMaxWaitMillis(5000) //忙碌时等待时长 毫秒
      jedisPoolConfig.setTestOnBorrow(true) //每次获得连接的进行测试

      jedisPool = new JedisPool(jedisPoolConfig,host,port.toInt)
    }
    jedisPool.getResource
  }

  def main(args: Array[String]): Unit = {
    val jedisClient = getJedisClient
    println(jedisClient.ping())
    println(jedisClient.get("k1"))
    jedisClient.close()
  }
}
