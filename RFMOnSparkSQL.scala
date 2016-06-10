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
