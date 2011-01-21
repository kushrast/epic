package scalanlp.parser
/*
 Copyright 2010 David Hall

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

import scalanlp.collection.mutable.TriangularArray
import scalala.tensor.counters.Counters.DoubleCounter
import scalanlp.trees.Span

import scalanlp.util.Encoder;

@serializable
@SerialVersionUID(2)
abstract class ParseChart[L](val grammar: Encoder[L], val length: Int) {
  final val top = new ChartScores();
  final val bot = new ChartScores();

  final class ChartScores private[ParseChart]() extends LabelScoreArray(length,grammar.index.size,zero) {

    private[ParseChart] def scoreArray = score;
    def labelScore(begin: Int, end: Int, label: L):Double = labelScore(begin,end,grammar.index(label));

    def enter(begin: Int, end: Int, parent: Int, w: Double) = {
      val oldScore = score(TriangularArray.index(begin,end))(parent);
      val newScore = sum(oldScore, w);
      score(TriangularArray.index(begin,end))(parent) = newScore;

      if(oldScore == zero) {
        enteredLabels(TriangularArray.index(begin,end))(parent) = true;
        narrowLeft(end)(parent) = begin max narrowLeft(end)(parent);
        wideLeft(end)(parent) = begin min wideLeft(end)(parent);
        wideRight(begin)(parent) = end max wideRight(begin)(parent);
        narrowRight(begin)(parent) = end min narrowRight(begin)(parent);
      }
      newScore > oldScore
    }

    /** Can a constituent with this label start here and end before end
    def canStartHere(begin: Int, end: Int, child: Int):Boolean = {
      narrowRight(begin)(child) <= end;
    }*/

    /**
     * returns all the possible split points for a span over (begin,end) with left child and right child
     */
    def feasibleSpan(begin: Int, end: Int, leftState: Int, rightState: Int): Span = {
      val narrowR = narrowRight(begin)(leftState);
      val narrowL = narrowLeft(end)(rightState);

      if (narrowR >= end || narrowL < narrowR) {
        emptySpan
      } else {
        val trueMin = narrowR max wideLeft(end)(rightState);
        val trueMax = wideRight(begin)(leftState) min narrowL;
        if(trueMin > narrowL || trueMin > trueMax) emptySpan
        else Span(trueMin,trueMax+1)
      }
    }

    // right most place a left constituent with label l can start and end at position i
    private val narrowLeft = Array.fill(length+1)(grammar.fillArray[Int](-1));
    // left most place a left constituent with label l can start and end at position i
    private val wideLeft = Array.fill(length+1)(grammar.fillArray[Int](length+1));
    // left most place a right constituent with label l--which starts at position i--can end.
    private val narrowRight = Array.fill(length+1)(grammar.fillArray[Int](length+1));
    // right-most place a right constituent with label l--which starts at position i--can end.
    private val wideRight = Array.fill(length+1)(grammar.fillArray[Int](-1));
  }



  // requirements: sum(a,b) >= a, \forall b that might be used.
  def sum(a:Double,b: Double):Double
  protected def zero: Double;

  private val emptySpan = Span(length+1,length+1)

  override def toString = {
    val data = new TriangularArray[DoubleCounter[L]](length+1, (i:Int,j:Int)=>grammar.decode(top.scoreArray(TriangularArray.index(i,j)))).toString;
    "ParseChart[" + data + "]";
  }

}


object ParseChart {
  def apply[L](g: Grammar[L], length: Int) = viterbi(g,length);

  trait Viterbi {
    final def zero = Double.NegativeInfinity;
    final def sum(a: Double, b: Double) = {
      math.max(a,b);
    }
  }
  type ViterbiParseChart[L] = ParseChart[L] with Viterbi;

  trait LogProbability {
    final def zero = Double.NegativeInfinity;
    final def sum(a: Double, b: Double) = scalanlp.math.Numerics.logSum(a,b);
  }
  type LogProbabilityParseChart[L] = ParseChart[L] with LogProbability;


  @serializable
  @SerialVersionUID(1)
  trait Factory[Chart[X]<:ParseChart[X]] {
    def apply[L](g: Grammar[L], length: Int):Chart[L];
    def computeUnaryClosure[L](g: Grammar[L]):UnaryRuleClosure;
  }


  // concrete factories:
  object viterbi extends Factory[ViterbiParseChart] {
    def apply[L](g: Grammar[L], length: Int) = new ParseChart(g,length) with Viterbi;
    def computeUnaryClosure[L](grammar: Grammar[L]):UnaryRuleClosure = {
      import scalanlp.math.Semiring.Viterbi._;
      UnaryRuleClosure.computeClosure(grammar)
    }
  }

  object logProb extends Factory[LogProbabilityParseChart] {
    def apply[L](g: Grammar[L], length: Int) = new ParseChart(g,length) with LogProbability;
    def computeUnaryClosure[L](grammar: Grammar[L]):UnaryRuleClosure = {
      import scalanlp.math.Semiring.LogSpace._;
      UnaryRuleClosure.computeClosure(grammar)
    }
  }


}
