package epic.features

import epic.framework.Feature
import breeze.linalg.Counter
import breeze.util.Index
import epic.trees.TreeInstance
import scala.collection.mutable.ArrayBuffer
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.io.Source

/**
 *
 *
 * @author kushrast
 */
@SerialVersionUID(1L)
class ClusterSpanFeaturizer(bitstring:Map[String,String]) extends SurfaceFeaturizer[String] with Serializable {
  def anchor(words: IndexedSeq[String]): SurfaceFeatureAnchoring[String] = {
    new SurfaceFeatureAnchoring[String] {
      def featuresForSpan(begin: Int, end: Int): Array[Feature] = {
        val firstWord = bitstring(words(begin));
        val prefixFeats = Seq(firstWord.take(1),firstWord.take(2),firstWord.take(3),firstWord.take(4));
        prefixFeats.map(ClusterSpanFeature).toArray;
      }
    }
  }
}

object ClusterSpanFeaturizer {
	val bitstrings = scala.collection.mutable.Map[String,String]() 
	def apply(pathsToCluster: Seq[String])={
		for(line <- Source.fromFile(pathsToCluster(0)).getLines)
    	{
      		val words = line.split(" ");
  			bitstrings += (words(0) -> words(1));
    	}
		val bitstring = bitstrings.toMap;
    	new ClusterSpanFeaturizer(bitstring);	
	}
}

case class ClusterSpanFeature(cluster: String) extends Feature