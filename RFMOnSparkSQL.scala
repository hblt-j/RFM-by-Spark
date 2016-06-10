package com.spark.app

import org.apache.log4j.{Level, Logger}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SQLContext

//case class Person(name: String, age: Int)
case class Sales(cu_id: Int, price: Int, date: String, days_ago: Int)
case class RFM(cu_id: Int, r: Int, f: Int, m: Int)

object RFMOnSparkSQL {
  def main(args: Array[String]) {
    //屏蔽日志
    Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
    Logger.getLogger("org.eclipse.jetty.server").setLevel(Level.OFF)

    //配置
    val conf = new SparkConf().setAppName("SQLOnSpark").setMaster("local")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)

    // this is used to implicitly convert an RDD to a DataFrame.
    import sqlContext.implicits._

    //register table sales
    sc.textFile("/test/sales.txt").map(_.split(" "))
      .map(s => Sales(s(0).trim.toInt, s(1).trim.toInt, s(2), s(3).trim.toInt))
      .toDF().registerTempTable("sales")

    //register table rfm
    val rfm = sqlContext.sql("SELECT cu_id as cuid,min(days_ago) as r, count(price) as f,sum(price) as m FROM sales" +
      " group By cu_id").map(t => RFM(t(0).toString.toInt, t(1).toString.toInt, t(2).toString.toInt, t(3)
      .toString.toInt)).toDF()
    rfm.registerTempTable("rfm")

    //avgr avgf avgm
    val vls:Map[String,Double] = sqlContext.sql("select avg(r) as avgr,avg(f) as avgf,avg(m) as avgm from rfm ")
      .map(_.getValuesMap[Double](List("avgr", "avgf", "avgm"))).collect()(0)

    //sparksql udf:flag()
    sqlContext.udf.register("flag", (r: Int) => if(r>=vls("avgr")) "1" else "0")

    /*//java.lang.UnsupportedOperationException: Schema for type Unit is not supported
    sqlContext.udf.register("flag", (r: Int,f: Int,m: Int) => {
      var flag=""
      if(r>=vls("avgr")) flag+="1" else flag+="0"
      if(f>=vls("avgf")) flag+="1" else flag+="0"
      if(m>=vls("avgm")) flag+="1" else flag+="0"
    })*/

    //show cu_id flag
    val vfmwithflag=sqlContext.sql("select cu_id as cuid,flag(r) as flagr,flag(f) as flagf,flag(m) as flagm " +
      "from rfm")
    vfmwithflag.show()
    
    
        sc.stop()
  }
}
/*
j@j-hblt:~$ su 
密码： 
root@j-hblt:/home/j# clear

root@j-hblt:/home/j# $HADOOP_HOME/sbin/start-dfs.sh 
16/06/11 06:28:51 WARN util.NativeCodeLoader: Unable to load native-hadoop library for your platform... using builtin-java classes where applicable
Starting namenodes on [j-hblt]
j-hblt: starting namenode, logging to /opt/hadoop/hadoop-2.6.4/logs/hadoop-root-namenode-j-hblt.out
localhost: starting datanode, logging to /opt/hadoop/hadoop-2.6.4/logs/hadoop-root-datanode-j-hblt.out
Starting secondary namenodes [j-hblt]
j-hblt: starting secondarynamenode, logging to /opt/hadoop/hadoop-2.6.4/logs/hadoop-root-secondarynamenode-j-hblt.out
16/06/11 06:29:17 WARN util.NativeCodeLoader: Unable to load native-hadoop library for your platform... using builtin-java classes where applicable

root@j-hblt:/home/j# $SPARK_HOME/sbin/start-all.sh 
starting org.apache.spark.deploy.master.Master, logging to /opt/spark/spark-1.6.1-bin-hadoop2.6/logs/spark-root-org.apache.spark.deploy.master.Master-1-j-hblt.out
localhost: starting org.apache.spark.deploy.worker.Worker, logging to /opt/spark/spark-1.6.1-bin-hadoop2.6/logs/spark-root-org.apache.spark.deploy.worker.Worker-1-j-hblt.out
root@j-hblt:/home/j# jps
2865 Launcher
2833 NailgunRunner
3623 Master
2362 Main
3260 DataNode
3485 SecondaryNameNode
3133 NameNode
3823 Jps
3727 Worker

root@j-hblt:/home/j# $SPARK_HOME/bin/spark-submit --class com.spark.app.RFMOnSparkSQL /home/j/文档/FirstSparkApp/out/artifacts/rfmonsparksql/rfmonsparksql.jar

+----+-----+-----+-----+                                                          
|cuid|flagr|flagf|flagm|
+----+-----+-----+-----+
|1631|    0|    0|    1|
|1831|    0|    0|    1|
|1231|    0|    0|    1|
|1431|    1|    0|    1|
|1031|    0|    0|    1|
|1632|    0|    0|    1|
|1432|    1|    0|    1|
|1832|    0|    0|    1|
|1232|    1|    0|    1|
|1032|    1|    0|    1|
+----+-----+-----+-----+



