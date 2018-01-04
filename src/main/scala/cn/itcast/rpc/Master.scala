package cn.itcast.rpc

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.concurrent.duration._

class Master(val host: String, val port: Int) extends Actor {

  //workerId -> WorkerInfo
  val idToWorker = new mutable.HashMap[String, WorkerInfo]()
  //WorkerInfo
  val workers = new mutable.HashSet[WorkerInfo]()
  //超时检测的间隔
  val CHECK_INTERVAL = 15000

  //方法运行前被调用
  override def preStart(): Unit = {
    println("preStart invoked")

    //scheduler是一个增强的方法，导入隐式转换，
    import context.dispatcher
    context.system.scheduler.schedule(0 millis, CHECK_INTERVAL millis, self, CheckTimeOutWorker)
  }

  //用于接收消息
  override def receive: Receive = {
    case RegisterWorker(id, memory, cores) => {
      //判断一下，是不是注册过了
      if (!idToWorker.contains(id)) {
        //把Worker的信息封装起来保存到内存当中
        val workerInfo = new WorkerInfo(id, memory, cores)
        idToWorker(id) = workerInfo
        workers += workerInfo
        sender ! RegisteredWorker(s"akka.tcp://MasterSystem@$host:$port/user/Master")
      }
    }

    case Heartbeat(id) => {
      if (idToWorker.contains(id)) {
        val workerInfo = idToWorker(id)
        //报活
        val currentTime = System.currentTimeMillis()
        workerInfo.lastHeartbeatTime = currentTime
      }
    }

    //心跳超时的时候，去除注册的信息
    case CheckTimeOutWorker => {
      val currentTime = System.currentTimeMillis()
      val toRemove = workers.filter(x => currentTime - x.lastHeartbeatTime > CHECK_INTERVAL)
      for(w <- toRemove){
        workers -= w
        idToWorker -= w.id
      }
      println(workers.size)
    }
  }
}


object  Master {
  def main(args: Array[String]){

    val host = args(0)
    val port = args(1).toInt
    val configStr =
      s"""
         |akka.actor.provider = "akka.remote.RemoteActorRefProvider"
         |akka.remote.netty.tcp.hostname = "$host"
         |akka.remote.netty.tcp.port = "$port"
       """.stripMargin
    val config = ConfigFactory.parseString(configStr)
    //ActorSystem老大，负责创建和监控下面的Actor,他是单例的
    val actorSystem = ActorSystem("MasterSystem", config)
    //创建Actor
    val master = actorSystem.actorOf(Props(new Master(host, port)),"Master") //Props(new Master)
    actorSystem.awaitTermination()
  }
}