package cs224n.wordaligner;  

import cs224n.util.*;

import java.util.List;

/**
 * Simple word alignment baseline model that maps source positions to target 
 * positions along the diagonal of the alignment grid.
 * 
 * IMPORTANT: Make sure that you read the comments in the
 * cs224n.wordaligner.WordAligner interface.
 * 
 * @author Dan Klein
 * @author Spence Green
 */
public class PMIModel implements WordAligner {

  private static final long serialVersionUID = 1315751943476440515L;
  
  // TODO: Use arrays or Counters for collecting sufficient statistics
  // from the training data.
  private CounterMap<String,String> sourceTargetCounts;
  private Counter<String> sourceCounts;
  private Counter<String> targetCounts;
 // private long  numberOfTrains;

  public Alignment align(SentencePair sentencePair) {
    // Placeholder code below. 
    // TODO Implement an inference algorithm for Eq.1 in the assignment
    // handout to predict alignments based on the counts you collected with train().
    Alignment alignment = new Alignment();
    int numSourceWords = sentencePair.getSourceWords().size();
    int numTargetWords = sentencePair.getTargetWords().size();
    sentencePair.sourceWords.add(NULL_WORD);
    for (int targetIndex = 0; targetIndex < numTargetWords; targetIndex++) {
      String targetWord = sentencePair.getTargetWords().get(targetIndex);
      double maxProb = -1;
      int bestIndex = -1;
      for(int srcIndex = 0; srcIndex < numSourceWords;srcIndex++){
    	  String srcWord = sentencePair.getSourceWords().get(srcIndex);
    	  double coOccurence = sourceTargetCounts.getCount(srcWord, targetWord);
    	  double sourceCount = sourceCounts.getCount(srcWord);
    	  double targetCount = targetCounts.getCount(targetWord);
    	  double prob;
    	  if(sourceCount == 0 || targetCount==0){
    		  prob = 0;
    	  }else{
    		  prob = (coOccurence)/(sourceCount*targetCount);
    	  }
    	  if(prob> maxProb){maxProb = prob; bestIndex=srcIndex; }
    	  
      }
      	
      if (bestIndex >=0 && bestIndex < numSourceWords) { // Coz we add the NULL after finding Numsrcwords
        // Discard null alignments
        alignment.addPredictedAlignment(targetIndex, bestIndex);
      }
    }
    return alignment;
  }

  public void train(List<SentencePair> trainingPairs) {
    sourceTargetCounts = new CounterMap<String,String>();
    sourceCounts = new Counter<String>();
    targetCounts = new Counter<String>();
   // numberOfTrains = trainingPairs.size();
    for(SentencePair pair : trainingPairs){
      List<String> targetWords = pair.getTargetWords();
      List<String> sourceWords = pair.getSourceWords();
      for (String source : sourceWords) {
	   	  sourceCounts.incrementCount(source,1);
      }
      
      for(String target : targetWords){
        for(String source : sourceWords){
          sourceTargetCounts.incrementCount(source, target, 1);
        }
        if(targetWords.size() > sourceWords.size()){
      	  sourceTargetCounts.incrementCount(NULL_WORD, target , 1); // If the source length is more than the target length then increment the count of each source word being mapped to NULL by one.
      	  sourceCounts.incrementCount(NULL_WORD, 1);
        }
    	targetCounts.incrementCount(target,1); 
      }
    }   
     
  }  
  
}
