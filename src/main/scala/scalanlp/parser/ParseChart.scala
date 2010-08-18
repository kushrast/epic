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
import scalanlp.trees.BinarizedTree
import scalanlp.trees.BinaryTree
import scalanlp.trees.NullaryTree
import scalanlp.trees.UnaryTree
import scalanlp.trees.Span

import scalanlp.util.Implicits._;

 class ParseChart[L](grammar: Grammar[L], val length: Int) {

  private val score = TriangularArray.raw(length+1, grammar.mkDenseVector(Double.NegativeInfinity));
  // right most place a left constituent with label l can start and end at position i
  private val narrowLeft = Array.fill(length+1)(grammar.fillArray[Int](-1));
  // left most place a left constituent with label l can start and end at position i
  private val wideLeft = Array.fill(length+1)(grammar.fillArray[Int](length+1));
  // left most place a right constituent with label l--which starts at position i--can end.
  private val narrowRight = Array.fill(length+1)(grammar.fillArray[Int](length+1));
  // right-most place a right constituent with label l--which starts at position i--can end.
  private val wideRight = Array.fill(length+1)(grammar.fillArray[Int](-1));

  final def enter(begin: Int, end: Int, parent: Int, w: Double) = {
    score(TriangularArray.index(begin,end))(parent) = w;
    narrowLeft(end)(parent) = begin max narrowLeft(end)(parent);
    wideLeft(end)(parent) = begin min wideLeft(end)(parent);
    wideRight(begin)(parent) = end max wideRight(begin)(parent);
    narrowRight(begin)(parent) = end min narrowRight(begin)(parent);
  }

  /** Can a constituent with this label start here and end before end*/
  def canStartHere(begin: Int, end: Int, child: Int):Boolean = {
    narrowRight(begin)(child) <= end;
  }

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

  private val emptySpan = Span(length+1,length+1)

  def enteredLabelIndexes(begin: Int, end: Int) = {
    score(TriangularArray.index(begin, end)).activeDomain.iterator
  }

  def enteredLabelScores(begin: Int, end: Int) = {
    score(TriangularArray.index(begin, end)).activeElements
  }


  def labelScore(begin: Int, end: Int, label: L): Double = labelScore(begin,end,grammar.index(label));
  def labelScore(begin: Int, end: Int, label: Int): Double = {
    score(TriangularArray.index(begin, end))(label);
  }

  def dumpChart() {
    val myScore = new TriangularArray[DoubleCounter[L]](length+1,(i:Int,j:Int)=>grammar.decode(score(TriangularArray.index(i,j))));
    println(myScore);
  }

  def enterTerm(begin: Int, end: Int, label: L, w: Double): Unit = enterTerm(begin,end,grammar.index(label),w);
  def enterTerm(begin: Int, end: Int, label: Int, w: Double): Unit = {
    enter(begin,end,label,w);
  }

  def enterUnary(begin: Int, end: Int, parent: L, child: L, w: Double): Unit = {
    enterUnary(begin,end,grammar.index(parent),grammar.index(child),w);
  }

  def enterUnary(begin: Int, end: Int, parent: Int, child: Int, w: Double): Unit = {
    enter(begin,end,parent,w);
  }

  def enterBinary(begin: Int, split: Int, end: Int, parent: L, lchild: L, rchild: L, w: Double): Unit = {
    enterBinary(begin,split,end,grammar.index(parent),grammar.index(lchild), grammar.index(rchild), w);
  }
  def enterBinary(begin: Int, split: Int, end: Int, parent: Int, lchild: Int, rchild: Int, w: Double): Unit = {
    enter(begin,end,parent,w);
  }


  def buildTree(start: Int, end: Int, root: Int):BinarizedTree[L] = {
    val scoreToFind = labelScore(start,end,root);
    for {
      split <- (start + 1) until end;
      (b,childScores) <- grammar.binaryRulesByIndexedParent(root);
      (c,ruleScore) <- childScores
    } {
      val score = ruleScore + labelScore(start,split,b) + labelScore(split,end,c);
      if(score closeTo scoreToFind) {
        val left = buildTree(start,split,b);
        val right = buildTree(split,end,c);
        return BinaryTree(grammar.index.get(root),left,right)(Span(start,end));
      }
    }
    for {
      (b,ruleScore) <- grammar.unaryRulesByIndexedParent(root)
    } {
      val score = ruleScore + labelScore(start,end,b);
      if(score closeTo scoreToFind) {
        val child = buildTree(start,end,b);
        return UnaryTree(grammar.index.get(root),child)(Span(start,end));
      }
    }
    if(start +1 == end) // lexical
      NullaryTree(grammar.index.get(root))(Span(start,end));  
    else error("Couldn't find a tree!");
  }
}
