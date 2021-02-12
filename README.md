# bigdata-spark
## 一、项目说明

* 该项目主要分析电商用户行为数据和电商业务数据，通过大数据技术对电商网站的数据进行实时计算并展示，为运营和管理人员提供数据支持，极大增强数据的可读性。
* 本项目原则是尽可能使用较多的开源技术框架，加深对大数据中各种技术栈的理解和使用，在使用过程中体验各框架的差异和优劣，为以后的项目开发技术选型做基础。

项目演示地址：

* 日活分时趋势：http://47.92.54.9:8089/index
* 订单查询：http://47.92.54.9:8089/table

## 二、数据源

### 1、用户行为日志

app启动数据如下：

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/datas/%E7%94%A8%E6%88%B7%E8%A1%8C%E4%B8%BA%E6%97%A5%E5%BF%97.jpg)

![](E:\code\gmall-realtime-spark\image\datas\用户行为日志.jpg)

事件数据如下：

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/datas/%E4%BA%8B%E4%BB%B6%E6%95%B0%E6%8D%AE.jpg)

![](E:\code\gmall-realtime-spark\image\datas\事件数据.jpg)

### 2、业务数据





## 三、需求分析

### 需求一：当日用户首次登陆（日活）分时趋势图，与昨日对比

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA/%E9%9C%80%E6%B1%82%E4%B8%80.png)

![](E:\code\gmall-realtime-spark\image\大数据平台搭建\需求一.png)



本项目以kafka作为数据来源，kafka中的日志有两种类型分别为启动和事件，这里需要统计日活，无法直接获取，所以先要思考以下几个问题。

- 用户活跃如何界定？
- 用户一天内多次登陆，如何获取首次登陆？
- 清洗过日活数据如何保存？方便分析和可视化展示
- 实时项目如何解决数据丢失和数据重复问题？

解决方案：

1. 首先过滤掉用户其他行为，计算日活单纯考虑用户进入app即可。以用户进入的第一个页面为准，在page信息下，看last_page_id是否为空，只留下值为空的。
2. 利用redis保存今天访问过系统的用户清单，redis的五大数据类型中，String和Set都可以完成去重功能，本项目中使用的是Set类型，好处是可以根据sadd的返回结果判断用户是否已经存在。
3. 将每批次新增的日活保存至elasticsearch，OLAP作为专门分析统计的数据库，相对传统数据库，容量大，计算能力强，集群扩展方便。
4. 通过手动**提交偏移量+幂等性**处理，实现精准一次性消费。将偏移量保存在redis，最后将数据写进elasticsearch，指定index的id完成幂等性操作。



### 需求二：实时抓取业务数据，实现订单的灵活查询

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E9%9C%80%E6%B1%82%E5%88%86%E6%9E%90/%E9%9C%80%E6%B1%82%E4%BA%8C.jpg)

![](E:\code\gmall-realtime-spark\image\需求分析\需求二.jpg)





#### 1、业务数据采集

- 业务数据库的采集主要是基于对数据库的变化的实时监控。目前市面上的开源产品主要是Canal和Maxwell。要利用这些工具实时采集数据到kafka，以备后续处理。为了响应阿里开源号召，这里选用Canal实现业务数据采集。
- Canal采集的数据，默认是放在一个统一的kafka 的Topic中，为了后续方便处理要进行以表为单位拆分到不同kafka的Topic中。
- 因为后续还需要对kafka中的事实数据和维度数据进行join操作，而维度表的中的数据不经常变更，所以针对维度数据，要单独保存。通常考虑用redis、hbase、mysql、kudu等通过唯一键查询性能较快的数据库中。我们选择Hbase保存维度数据。

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E9%9C%80%E6%B1%82%E5%88%86%E6%9E%90/%E9%9C%80%E6%B1%822%E4%B8%9A%E5%8A%A1%E6%95%B0%E6%8D%AE%E9%87%87%E9%9B%86.png)

![](E:\code\gmall-realtime-spark\image\需求分析\需求2业务数据采集.png)

#### 2、订单灵活查询

想要实现订单的灵活查询，数据最后一定会进入OLAP数据库，这里我们将最后的数据写进ElasticSearch，方便快速查询和对接可视化模块。

> 那么问题来了，Kafka中事实数据和维度数据何时关联？是在写进OLAP前面，还是在写入之后？

由于在很多OLAP数据库对聚合过滤都是非常强大，但是大表间的关联都不是长项。所以在数据进入OLAP前，尽量提前把数据关联组合好，不要在查询的时候临时进行Join操作。也就是说在实时计算中进行流join。

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E9%9C%80%E6%B1%82%E5%88%86%E6%9E%90/%E9%9C%80%E6%B1%822%E8%AE%A2%E5%8D%95%E6%9F%A5%E8%AF%A2.png)

![](E:\code\gmall-realtime-spark\image\需求分析\需求2订单查询.png)







## 四、集群环境搭建

本项目的大数据集群环境采用Apache开源组件搭建，目的是熟悉各组件的功能和用法，加深对开源框架的理解。当然也可以使用CDH全家桶搭建，以业务理解和数据处理为重点。

### 1、Hadoop集群搭建

首先准备三台虚拟机，我买的是阿里云的三台Centos7.5实例，配置均为2核8G。购买虚拟机可以有多种套餐选择，我选择按量付费的模式，就是说用多少就扣费多少，关机不收费。（PS：一个月不到一百块）

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA/%E8%99%9A%E6%8B%9F%E6%9C%BA%E5%AE%9E%E4%BE%8B.jpg)

![](E:\code\gmall-realtime-spark\image\大数据平台搭建\虚拟机实例.jpg)

可以看到每台虚拟机都分别分配一个公网ip和私有ip：

* 公网ip：客户端远程访问虚拟机使用
* 私有ip：集群间各个组件通信使用

因为需要远程访问虚拟机，所以需在安全组添加各组件默认端口

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA/%E5%AE%89%E5%85%A8%E7%BB%84%E7%AB%AF%E5%8F%A3.jpg)

![](E:\code\gmall-realtime-spark\image\大数据平台搭建\安全组端口.jpg)



集群搭建可以参照：[阿里云环境下大数据集群搭建](http://hadoop.love/#/info?blogOid=79)，或者参照本项目中的大数据平台搭建文档。

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA/%E7%BB%84%E4%BB%B6.jpg)



![](E:\code\gmall-realtime-spark\image\大数据平台搭建\组件.jpg)

### 2、Mysql业务数据的实时采集

通过canal实现对mysql业务数据的采集，原理：canal伪装成mysql Slave，采集master中的binlog，将数据数据实时写进kafka。

关于实时采集系统搭建请参考：[Canal实时采集Mysql业务数据](http://hadoop.love/#/info?blogOid=81)



## 五、开发进度

### 1、日活

1.1 思路：

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%BC%80%E5%8F%91%E8%BF%9B%E5%BA%A6/%E9%9C%80%E6%B1%82%E4%B8%80%E6%80%9D%E8%B7%AF.png)

![](E:\code\gmall-realtime-spark\image\开发进度\需求一思路.png)

1.2 程序流程图： `com.terry.gmall.realtime.app.DauApp`

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%BC%80%E5%8F%91%E8%BF%9B%E5%BA%A6/%E9%9C%80%E6%B1%82%E4%B8%80%E7%A8%8B%E5%BA%8F%E6%B5%81%E7%A8%8B%E5%9B%BE.png)

![](E:\code\gmall-realtime-spark\image\开发进度\需求一程序流程图.png)

1.3 启动 `com.terry.gmall.realtime.app.DauApp`程序，设置5s从 kafka拉取一次数据，会不断读取上次消费数据结束的偏移结束点。

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%BC%80%E5%8F%91%E8%BF%9B%E5%BA%A6/%E9%9C%80%E6%B1%82%E4%B8%80%E5%90%AF%E5%8A%A8.jpg)

![](E:\code\gmall-realtime-spark\image\开发进度\需求一启动.jpg)

在hadoop102上生成日志数据发送到kafka

~~~
[terry@hadoop102 gmall]$ java -jar gmall-kafka.jar 
~~~

控制台可以看到数据流打印，并且已经保存至es中，提交偏移量

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%BC%80%E5%8F%91%E8%BF%9B%E5%BA%A6/%E9%9C%80%E6%B1%82%E4%B8%80%E6%89%A7%E8%A1%8C%E7%BB%93%E6%9E%9C.jpg)

![](E:\code\gmall-realtime-spark\image\开发进度\需求一执行结果.jpg)

可以分别到redis和es中查看保存的数据

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%BC%80%E5%8F%91%E8%BF%9B%E5%BA%A6/%E9%9C%80%E6%B1%82%E4%B8%80redis%E7%BB%93%E6%9E%9C.jpg)

![](E:\code\gmall-realtime-spark\image\开发进度\需求一redis结果.jpg)

运行 `com.terry.gmall.publisher.GmallPublisherApplication` ，在浏览器中输入：

* http://hadoop102:8070/realtime-total?date=2021-02-11 测试日活统计接口
* http://hadoop102:8070/realtime-hour?id=dau&date=2021-02-11 测试分时接口

~~~
[{"id": "dau","name": "新增日活","value": 96},{"id": "new_mid","name": "新增设备","value": 233}]
~~~

~~~
{"yesterday": {"15": 93,"17": 61,"18": 10,"23": 61},"today": {"00": 84}}
~~~

运行可视化程序`com.demo.DemoApplication`浏览可视化页面，访问 http://hadoop102:8089/index

![image](https://github.com/91Terry/bigdata-spark/blob/master/image/%E5%BC%80%E5%8F%91%E8%BF%9B%E5%BA%A6/%E9%9C%80%E6%B1%82%E4%B8%80%E5%8F%AF%E8%A7%86%E5%8C%96.jpg)

![](E:\code\gmall-realtime-spark\image\开发进度\需求一可视化.jpg)













