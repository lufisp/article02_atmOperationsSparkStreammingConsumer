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
    
    val conf = new SparkConf().setAppName("AtmConsumer").setMaster("local[*]");
    val ssc = new StreamingContext(conf,Seconds(5));
    
    Logger.getRootLogger.setLevel(Level.ERROR);
    
    val kafkaParams = Map[String,String]("metadata.broker.list" -> "localhost:9092");
    val topic = Set("atmOperations") ;
    
    val directKafkaStream = KafkaUtils.createDirectStream[String, Array[Byte],StringDecoder,DefaultDecoder](ssc,kafkaParams,topic);
    
   
    directKafkaStream.foreachRDD( rdd =>{
      println("New Micro-Batch");
      rdd.reduceByKey((a ,b) => ({
          val parser = new Schema.Parser();
          val schema = parser.parse(USER_SCHEMA);
          val recordInjection: Injection[GenericRecord, Array[Byte]] = GenericAvroCodecs.toBinary(schema);          
          val recordA:GenericRecord = recordInjection.invert(a).get;
          val recordB:GenericRecord = recordInjection.invert(b).get;
          val operValueA = Integer.parseInt(recordA.get("operValue").toString());
          val operValueB = Integer.parseInt(recordB.get("operValue").toString());          
          val avroRecord:GenericData.Record = new GenericData.Record(schema);
          avroRecord.put("id", recordA.get("id"));
          avroRecord.put("operValue", operValueA + operValueB);          
          recordInjection.apply(avroRecord);
      })).foreach(avroRecord => {
        val parser = new Schema.Parser();
        val schema = parser.parse(USER_SCHEMA);
        val recordInjection: Injection[GenericRecord, Array[Byte]] = GenericAvroCodecs.toBinary(schema);
        val record:GenericRecord = recordInjection.invert(avroRecord._2).get;
        System.out.println("Atm_id = " + record.get("id") + ", operValue= " + record.get("operValue"));
      })
    });
    
    ssc.start();
    ssc.awaitTermination();
    
  }

}