# bigdata-spark
## 项目说明

* 该项目主要分析电商用户行为数据和电商业务数据，通过大数据技术对电商网站的数据进行实时计算并展示，为运营和管理人员提供数据支持，极大增强数据的可读性。
* 本项目原则是尽可能使用较多的开源技术框架，加深对大数据中各种技术栈的理解和使用，在使用过程中体验各框架的差异和优劣，为以后的项目开发技术选型做基础。

## 项目需求

* 需求一：当日用户首次登录（日活）分时图，与昨日对比
* 需求二：业务数据的采集以及分流
* 需求三：业务数据订单的灵活查询

## 数据源

### 1、用户行为日志

app启动数据如下：

![用户行为日志](E:\code\gmall-realtime-spark\image\datas\用户行为日志.jpg)

事件数据如下：

![事件数据](E:\code\gmall-realtime-spark\image\datas\事件数据.jpg)

### 2、业务数据





## 大数据平台搭建

* 





## 需求分析

### 需求一：当日用户首次登陆（日活）分时趋势图，与昨日对比

![需求一](E:\code\gmall-realtime-spark\image\需求一.png)



本项目以kafka作为数据来源，kafka中的日志有两种类型分别为启动和事件，这里需要统计日活，无法直接获取，所以先要思考以下几个问题。

* 用户活跃如何界定？

* 用户一天内多次登陆，如何获取首次登陆？
* 清洗过日活数据如何保存？方便分析和可视化展示
* 实时项目如何解决数据丢失和数据重复问题？

解决方案：

1. 首先过滤掉用户其他行为，计算日活单纯考虑用户进入app即可。以用户进入的第一个页面为准，在page信息下，看last_page_id是否为空，只留下值为空的。
2. 利用redis保存今天访问过系统的用户清单，redis的五大数据类型中，String和Set都可以完成去重功能，本项目中使用的是Set类型，好处是可以根据sadd的返回结果判断用户是否已经存在。
3. 将每批次新增的日活保存至elasticsearch，OLAP作为专门分析统计的数据库，相对传统数据库，容量大，计算能力强，集群扩展方便。
4. 通过手动**提交偏移量+幂等性**处理，实现精准一次性消费。将偏移量保存在redis，最后将数据写进elasticsearch，指定index的id完成幂等性操作。



## 开发进度

### 1、日活

1.1 思路：

![需求一思路](E:\code\gmall-realtime-spark\image\开发进度\需求一思路.png)

1.2 程序流程图： `com.terry.gmall.realtime.app.DauApp`

![需求一程序流程图](E:\code\gmall-realtime-spark\image\开发进度\需求一程序流程图.png)

1.3 启动 `com.terry.gmall.realtime.app.DauApp`程序，设置5s从kafka拉取一次数据，会不断读取上次消费数据结束的偏移结束点。

![需求一启动](E:\code\gmall-realtime-spark\image\开发进度\需求一启动.jpg)

在hadoop102上生成日志数据发送到kafka

~~~
[terry@hadoop102 gmall]$ java -jar gmall-kafka.jar 
~~~

控制台可以看到数据流打印，并且已经保存至es中，提交偏移量

![需求一执行结果](E:\code\gmall-realtime-spark\image\开发进度\需求一执行结果.jpg)

可以分别到redis和es中查看保存的数据

![需求一redis结果](E:\code\gmall-realtime-spark\image\开发进度\需求一redis结果.jpg)

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

![需求一可视化](E:\code\gmall-realtime-spark\image\开发进度\需求一可视化.jpg)