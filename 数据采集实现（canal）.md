# 数据采集实现

## 一、MySql准备

### 1、创建业务数据库

![创建业务数据库](E:\code\gmall-realtime-spark\image\canal采集\创建业务数据库.jpg)

### 2、导入建表数据

导入gmall.sql脚本，生成业务数据

![生成数据库](E:\code\gmall-realtime-spark\image\canal采集\生成数据库.jpg)

### 3、修改配置文件

~~~
[terry@hadoop102 module]$ sudo vim /etc/my.cnf
~~~

修改如下：

~~~bash
server-id= 1
log-bin=mysql-bin
#行采集
binlog_format=row
#需要采集的数据库
binlog-do-db=gmall
~~~

重启mysql，使配置生效

~~~
[terry@hadoop102 module]$ sudo systemctl restart mysqld
~~~

### 4、赋权限

在mysql中执行

~~~
mysql> GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%' IDENTIFIED BY 'canal' ;
~~~

## 二、canal安装

### 1、解压安装

~~~
[terry@hadoop102 software]$ mkdir /opt/module/canal
[terry@hadoop102 software]$ tar -zxvf canal.deployer-1.1.4.tar.gz -C /opt/module/canal
~~~

### 2、修改配置文件

（1）修改conf/canal.properties的配置

~~~
[terry@hadoop102 conf]$ vim canal.properties
~~~

修改canal的输出model，默认tcp，改为输出到kafka

~~~bash
# tcp, kafka, RocketMQ
canal.serverMode = kafka
~~~

修改Kafka集群的地址

~~~~bash
canal.mq.servers = hadoop102:9092,hadoop103:9092,hadoop104:9092
~~~~

（2）修改instance.properties（单机模式）

~~~
[terry@hadoop102 example]$ pwd
/opt/module/canal/conf/example

[terry@hadoop102 example]$ vim instance.properties
~~~

修改如下：

~~~bash
#配置mysql服务器地址
canal.instance.master.address=102外网ip:3306
#修改输出到kafka主题
canal.mq.topic=ODS_BASE_DB_C
#修改分区数
canal.mq.partitionsNum=4
canal.mq.partitionHash=.*\\..*:$pk$
~~~

### 3、启动canal

~~~
[terry@hadoop102 example]$ cd /opt/module/canal/
[terry@hadoop102 canal]$ bin/startup.sh
~~~

![canal启动](E:\code\gmall-realtime-spark\image\canal采集\canal启动.jpg)

### 4、采集测试

 canal中的配置是将采集到的数据发送到Kafka的ODS_BASE_DB_C主题中，所以先开启消费端：

~~~
[terry@hadoop102 kafka]$ bin/kafka-console-consumer.sh --bootstrap-server  hadoop102:9092 --topic ODS_BASE_DB_C
~~~

然后在gmall数据库中修改或者插入一条数据，数据都会被实时采集到的kafka

![采集数据](E:\code\gmall-realtime-spark\image\canal采集\采集数据.jpg)