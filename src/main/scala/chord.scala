import java.math.BigInteger
import java.util.concurrent.TimeUnit
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.util.{Success, Failure}

case class updateFingerTable(identifier: String)
case class join(arbitraryNode: ActorRef)
case class getSuccessor(id: Int)
case class getPredecessor(id: Int)
case class receiveSuccessor(receivedSuccessor: ActorRef)
case class receivePredecessor(receivedPredecessor: ActorRef)


class FingerTable(var start: Int, var interval: Array[Int], var succ: ActorRef){}

object Main extends App {
  var numNodes: Int = 0
  var numRequests: Int = 0
  var truncateLimit: Int = 0
  var maxNetworkSize: Int = 0

  //Actor System, Actor references & index array declarations
  var allActors: Array[Int] = null
  var actorsAllFull: Array[ActorRef] = null
  val chordActorSystem = ActorSystem("ChordP2PNetwork")
  var nodes: Array[ActorRef] = null

  //Check Command-line args
  if (args.length == 0 || args.length != 2) {
    println("Arguments are not proper.")
    chordActorSystem.shutdown()
  } else {
    //Read Command-line args
    numNodes = args(0).toInt
    numRequests = args(1).toInt

    //Set maximum size of network
    truncateLimit = (math.log(numNodes)/math.log(2)).ceil.toInt*3
    maxNetworkSize =  math.pow(2, truncateLimit).toInt
    nodes = Array.ofDim[ActorRef](maxNetworkSize)

    //Initializing Nodes
    for (i <- 0 to numNodes-1) {
      nodes(i) = initializeNode(i)
      implicit var timeout = Timeout.apply(6000, TimeUnit.SECONDS)
      var future: Future[Any] = ask(nodes(i), join(getArbitraryNode(i)))

      var result = Await.result(future, timeout.duration)
    }
  }

  def initializeNode(i: Int): ActorRef = {

    //Based on IP:PORT of the Node, calculate the consistent hash
    val inputStringToHash = (Random.nextInt(254)+1).toString+"."+
                            (Random.nextInt(254)+1).toString+"."+
                            (Random.nextInt(254)+1).toString+"."+
                            (Random.nextInt(254)+1).toString+":"+
                            (Random.nextInt(i+1)+1).toString

    val identifier = getConsistentHash(inputStringToHash).toString

    var node = chordActorSystem.actorOf(Props[Node],name = identifier)

    return node
  }

  def getArbitraryNode(i:Int): ActorRef = {
    var pick:ActorRef = null
    if (i == 0) {
      pick = nodes(i)
    } else {
       while (pick == null || pick == nodes(i)) {
        pick = nodes(Random.nextInt(i + 1))
      }
    }
    return pick
  }

  def getConsistentHash(inputString: String): Integer = {

    //Calculate SHA-1 of input string
    var hashInput = java.security.MessageDigest.getInstance("SHA-1")
      .digest(inputString.getBytes("UTF-8"))
      .map("%02x".format(_)).mkString

    //Convert hash to binary and truncate to first 'm' bits and convert to decimal
    val output = Integer.parseInt(
      (new BigInteger(hashInput,16).toString(2))
        .substring(0,truncateLimit) ,2)

    return output
  }
}

class Node() extends Actor {
  var identifier: Int = 0
  var predecessor: ActorRef = null
  var successor: ActorRef = null
  var resultSuccessor: ActorRef = null
  var resultPredecessor: ActorRef = null


  //Fingertable
  var start: Array[Int] = null
  var interval = Array.ofDim[Int](0, 0)
  var succ: Array[ActorRef] = null
  var fingerTable: Array[FingerTable] = null

  def receive = {
    case join(arbitraryNode: ActorRef) => {
      //Populate finger table
      start =  Array.ofDim[Int](Main.truncateLimit)
      interval =  Array.ofDim[Int](Main.truncateLimit, 2)
      succ =  Array.ofDim[ActorRef](Main.truncateLimit)
println("--------------------------------- | "+self)
      if (self == arbitraryNode) {
        successor = self
        predecessor = self

        populateFingerTable(self)
      } else {
        populateFingerTable(arbitraryNode)
      }
      sender ! Unit
    }
def populateFingerTable(arbitraryNode: ActorRef): Unit ={

    identifier = findIdentifierFromActorRef(self)
    //populate 'start'
    for (i <- 0 to Main.truncateLimit-1) {
      start(i) = (identifier + Math.pow(2,i).toInt) % (Math.pow(2,Main.truncateLimit).toInt)
    }

    //populate 'interval'
    for (i <- 0 to Main.truncateLimit-1) {
      interval(i)(0) = start(i)
      if (i+1 == Main.truncateLimit) {
        interval(i)(1) = (identifier + Math.pow(2,i+1).toInt) % (Math.pow(2,Main.truncateLimit).toInt)
      } else {
        interval(i)(1) = start(i + 1)
      }
    }

    fingerTable = Array.ofDim[FingerTable](Main.truncateLimit)
    //populate 'succ'
    if (arbitraryNode == self) {
      for (i <- 0 to Main.truncateLimit-1) {
        succ(i) = self

        fingerTable(i) = new FingerTable(start(i), Array(interval(i)(0), interval(i)(1)), succ(i))
        println("---finger table of ---"+self)
        println(fingerTable(i).start.toString+" | "+fingerTable(i).interval(0).toString+" - "+fingerTable(i).interval(1).toString+" | "+fingerTable(i).succ.toString())

      }
    } else {
      println("---------------- |"+self+" | ANODE: | "+arbitraryNode)
      for (i <- 0 to Main.truncateLimit-1) {
        println(i.toString+" | "+start(i).toString+" | "+arbitraryNode.toString)
//        var result = findUsingFutures(arbitraryNode, start(i), "Successor")
        //while(resultSuccessor == null) {
          arbitraryNode ! getSuccessor(start(i))
        //}
        Thread.sleep(25000)
        println(resultSuccessor.toString())
        succ(i) = resultSuccessor
        resultSuccessor = null
        println(succ(i))
        fingerTable(i) = new FingerTable(start(i), Array(interval(i)(0), interval(i)(1)), succ(i))
        println("---finger table of ---"+self)
        println(fingerTable(i).start.toString+" | "+fingerTable(i).interval(0).toString+" - "+fingerTable(i).interval(1).toString+" | "+fingerTable(i).succ.toString())

//        Thread.sleep(500)
      }
    }
  }
    case getSuccessor(id: Int) => {

      if (id == identifier) {
        println("getSuccessor called for self| "+id)
        sender ! receiveSuccessor(successor)
      } else {
        println("getSuccessor called for | "+id)
        var successorRef: ActorRef = findSuccessor(id)
        sender ! receiveSuccessor(successorRef)
      }
    }

    case getPredecessor(id: Int) => {
        println("getPredecessor called for | "+id)
        var predecessorRef: ActorRef = closestPrecedingFinger(id)
        sender ! receivePredecessor(predecessorRef)
    }

    case receiveSuccessor(receivedSuccessor: ActorRef) => {
      println("receivedSuccessor called for - "+self.toString()+"Received value - "+receivedSuccessor.toString())
      resultSuccessor = receivedSuccessor
    }
    case receivePredecessor(receivedPredecessor: ActorRef) => {
      resultPredecessor = receivedPredecessor
    }

  }
  def findSuccessor(searchIdentifier: Int): ActorRef = {
    var tempNode: ActorRef = findPredecessor(searchIdentifier)
    val tempNodeIdentifier: Int = findIdentifierFromActorRef(tempNode)
println(searchIdentifier.toString+" | "+tempNode.toString()+" - "+tempNodeIdentifier.toString)
//    tempNode = findUsingFutures(tempNode, tempNodeIdentifier, "Successor")

    //while(resultSuccessor == null) {
      if (tempNode == self) {
        resultSuccessor = successor
      }else{
        tempNode ! getSuccessor(tempNodeIdentifier)
      }
    //}
    Thread.sleep(25000)
    tempNode = resultSuccessor
    //resultSuccessor = null
    return tempNode
  }

  def findPredecessor(searchIdentifier: Int): ActorRef = {
    var tempNode: ActorRef = self
    var keepLooking: Boolean = true

    while (keepLooking) {
      var boundLeftIdentifier = findIdentifierFromActorRef(tempNode)
      var tempNodeSuccessor: ActorRef = null
      if (tempNode == self) {
        resultSuccessor = successor
      } else {
//        tempNodeSuccessor = findUsingFutures(tempNode, boundLeftIdentifier, "Successor")
        //while(resultSuccessor == null) {
           tempNode ! getSuccessor(boundLeftIdentifier)
        //}
        Thread.sleep(25000)
      }

      tempNodeSuccessor = resultSuccessor
      resultSuccessor = null
      var boundRightIdentifier = findIdentifierFromActorRef(tempNodeSuccessor)
println(tempNode.toString()+" - "+boundLeftIdentifier.toString+" , "+boundRightIdentifier.toString)
      if ((searchIdentifier > boundLeftIdentifier && searchIdentifier <= boundRightIdentifier) || (boundLeftIdentifier == boundRightIdentifier))  {
        keepLooking = false
      }
//lookout
      if (keepLooking) {
//        tempNode = findUsingFutures(tempNode, searchIdentifier, "Predecessor")
//        while(resultPredecessor == null) {
//          if (tempNode == self) {
//            resultPredecessor = predecessor
//          }else {
            tempNode ! getPredecessor(searchIdentifier)
//          }
//        }
        Thread.sleep(15000)
        tempNode = resultPredecessor
        resultPredecessor = null
      }
    }
    return tempNode
  }

  def closestPrecedingFinger(searchIdentifier: Int): ActorRef = {
    for (i <- Main.truncateLimit-1 to 0) {
      println("------- inside CPF | "+searchIdentifier+" | ")
      var successorIdentifier = findIdentifierFromActorRef(fingerTable(i).succ)
      if (successorIdentifier > identifier && successorIdentifier < searchIdentifier) {
        return fingerTable(i).succ
      }
    }
    return self
  }

  def findIdentifierFromActorRef(actorRef: ActorRef): Int = {
    return actorRef.toString().split("user/")(1).split("#")(0).toInt
  }
}
