package atmConsumers;
import org.apache.spark._
import org.apache.spark.streaming.kafka._
import kafka.serializer.StringDecoder
import kafka.serializer.DefaultDecoder
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.Seconds
import org.apache.avro.Schema
import com.twitter.bijection.Injection
import org.apache.avro.generic.GenericRecord
import com.twitter.bijection.avro.GenericAvroCodecs
import org.apache.log4j.Logger
import org.apache.log4j.Level
import org.apache.avro.generic.GenericData
import java.util.Properties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord





/**
 * @author ${user.name}
 */
object AtmConsumer {
   
  
  def main(args : Array[String]) {
    
        
    val USER_SCHEMA = """{
             "type":"record",
             "name":"atmRecord",
             "fields":[
               { "name":"id", "type":"string" },
               { "name":"operValue", "type":"int" }
             ]}""";
    
    val sparkConf = new SparkConf().setAppName("AtmConsumer").setMaster("local[*]");
    val sc = new SparkContext(sparkConf)
    sc.setLogLevel("ERROR");
    val ssc = new StreamingContext(sc,Seconds(5));
    
    val kafkaParams = Map[String,String]("metadata.broker.list" -> "localhost:9092");
    val topic = Set("atmOperations") ;    
    val directKafkaStream = KafkaUtils.createDirectStream[String, Array[Byte],StringDecoder,DefaultDecoder](ssc,kafkaParams,topic);
    
    

    directKafkaStream.foreachRDD( rdd =>{
      println("New Micro-Batch");
      rdd.map(avroRecord => {
        val parser = new Schema.Parser();
        val schema = parser.parse(USER_SCHEMA);
        val recordInjection: Injection[GenericRecord, Array[Byte]] = GenericAvroCodecs.toBinary(schema);
        val record:GenericRecord = recordInjection.invert(avroRecord._2).get;        
        (record.get("id").toString(),Integer.parseInt(record.get("operValue").toString()))
        
      })
      .reduceByKey((a,b) => a + b)
      .foreachPartition(x => {       
       val props: Properties = new Properties;
       props.put("bootstrap.servers", "localhost:9092,localhost:9093");
       props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
       props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
       val producer : KafkaProducer[String, String] = new KafkaProducer[String, String](props);
       x.foreach({ row => ({
        //println("key: " + row._1  + "  value:" + row._2); 
        val recordToKafka: ProducerRecord[String, String]= new ProducerRecord[String, String]("atmOperationsGrouped", row._1, row._2.toString());
        producer.send(recordToKafka);
      })})})
        
    });
    
    ssc.start();
    ssc.awaitTermination();    
  }

}
