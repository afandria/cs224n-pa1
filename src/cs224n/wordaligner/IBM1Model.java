package cs224n.wordaligner;

import cs224n.util.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

/**
 * IBM 1 Model:
 * Attempt to learn probabilities of source words translating to
 * target words, depending on co-occurences found in the corpus.
 *
 * IMPORTANT: Make sure that you read the comments in the
 * cs224n.wordaligner.WordAligner interface.
 *
 * @author Dan Klein
 * @author Spence Green
 */
public class IBM1Model implements WordAligner {

  public static final double INCREASE_RATIO = 1.0005;

  public static final double EXTREMELY_LARGE = 9999999;
  public static final int MAX_ATTEMPTS = 50;
  public static final double MIN_CHANGE = .01;

  private static final long serialVersionUID = 1315751943476440515L;

  // I want to know the prob of an t word given an s word (or NULL)
  // To initialize, I need to know the # of s words... (sources)
  // since it should be 1/(s+1)
  private CounterMap<String, String> probTgivenS;

  // Counts co-occurrences, but currently unused for IBM1Model
  //private CounterMap<String,String> sourceTargetCounts;

  public IBM1Model() {
	super();
	
	probTgivenS = new CounterMap<String, String>();
  }


  public Alignment align(SentencePair sentencePair) {
    Alignment alignment = new Alignment();
    List<String> targetWords = sentencePair.getTargetWords();
    List<String> sourceWords = sentencePair.getSourceWords();

    // Let's see...
    // We probably want to estimate P(a_j = i | t, s)
    // And we'll probably want to pick the i that makes the largest P

    for (int j = 0; j < targetWords.size(); j++) {
	  int bestI = -1; // null
	  double bestAlignProb = probTgivenS.getCount(NULL_WORD, targetWords.get(j));
      for (int i = 0; i < sourceWords.size(); i++) {
        double prob = probTgivenS.getCount(sourceWords.get(i), targetWords.get(j));

        if (prob > bestAlignProb) {
	      bestI = i;
	      bestAlignProb = prob;
		}
	  }

	  if (bestI != -1) {
	    alignment.addPredictedAlignment(j, bestI);
      } else {
        // System.out.println("was null");
      }
	}
    return alignment;
  }
  
  private void initialize(List<SentencePair> trainingPairs) {
	for (SentencePair pair : trainingPairs) {
  	  List<String> targetWords = pair.getTargetWords();
      List<String> sourceWords = pair.getSourceWords();
      for(String target : targetWords){
        for(String source : sourceWords){
          probTgivenS.setCount(source, target, 1.0);
        }
        probTgivenS.setCount(NULL_WORD, target, 1.0); // also deal with NULL
      }
  	}
  }

  public void train(List<SentencePair> trainingPairs) {
    initialize(trainingPairs);

    // Now for the real meat of the algorithm
    int attempts = 0;
    double oldLLH = -1 * EXTREMELY_LARGE;
    while (attempts < MAX_ATTEMPTS) {
      double newLLH = subtrain(trainingPairs, attempts);
      
      System.out.println("Attempt #: " + attempts + " LLH: " + newLLH);
      System.out.flush();
      
      // Note that oldLLH < 0, so our INCREASE_RATIO just needs to make newLLH less negative.
      // If it is at least 5% less negative than before, we are making progress and can iterate.
      if (newLLH > oldLLH / INCREASE_RATIO) {
        oldLLH = newLLH;
      } else {
        break;
      }
      
      ++attempts;
	}
    
    // Now that we're done, serialize the data into a binary file.
    // This will be used for IBM2Model.java
    try
    {
      FileOutputStream fileOut = new FileOutputStream("IBM1Model_probTgivenS.ser");
      ObjectOutputStream out = new ObjectOutputStream(fileOut);
      out.writeObject(probTgivenS);
      out.flush();
      out.close();
      fileOut.close();
      
    } catch(IOException i) {
        i.printStackTrace();
    }
  }

  // Performs 1 iteration of the IBM 1 Model
  // Returns the maximum change to the stored P's
  private double subtrain(List<SentencePair> trainingPairs, int attempts) {
	CounterMap<String, String> stAlignmentCounts = new CounterMap<String, String>();

	// For each SentencePair...
    for(SentencePair pair : trainingPairs) {
      List<String> targetWords = pair.getTargetWords();
      List<String> sourceWords = pair.getSourceWords();

      for(int j = 0; j < targetWords.size(); ++j) {
		// We need to find P(a_j = i | t, s)
		String t = targetWords.get(j);

		// To do so, first compute the sum of P(t_j | s_i) for all i
		double sum = 0;
		for (int i = 0; i < sourceWords.size(); ++i) {
			sum += probTgivenS.getCount(sourceWords.get(i), t);
		}
		// And NULL
		sum += probTgivenS.getCount(NULL_WORD, t);
		
		for (int i = 0; i < sourceWords.size(); ++i) {
			double p = probTgivenS.getCount(sourceWords.get(i), t);
			stAlignmentCounts.incrementCount(sourceWords.get(i), t, p / sum);
		}

		// Handle NULL
		double p = probTgivenS.getCount(NULL_WORD, t);
		stAlignmentCounts.incrementCount(NULL_WORD, t, p / sum);
      }
    }

    // Maximum change (this is an absolute value)
    double maxChange = 0;

    // Now renormalize
    for (String source : probTgivenS.keySet()) {
      // find the denominator
      // a sum over all target given this source
      double sum = 0;
      for (String target : probTgivenS.getCounter(source).keySet()) {
		sum += stAlignmentCounts.getCount(source, target);
	  }

	  // The numerator is just a single stAlignmentCount
	  for (String target : probTgivenS.getCounter(source).keySet()) {
        double newProb = stAlignmentCounts.getCount(source, target) / sum;

        // Update the maximum change value
        double change = Math.abs(probTgivenS.getCount(source, target) - newProb);
        if (change > maxChange) {
	      maxChange = change;
		}

        // Set the new probability!
        probTgivenS.setCount(source, target, newProb);
	  }
	}

    System.out.println("Attempt #" + attempts + ": " + maxChange);
    System.out.println(probTgivenS.getCount("le", "the"));
    
    //return maxChange;
    return logLikelihood(trainingPairs);
  }

  // Compute the log likelihood of the training set given our current q and p parameters
  private double logLikelihood(List<SentencePair> trainingPairs) {
    // Log Likelihood = SUM[all pairs a]
	  
	double llh = 0;
	  
	for (SentencePair sentencePair : trainingPairs) {
      List<String> targetWords = sentencePair.getTargetWords();
      List<String> sourceWords = sentencePair.getSourceWords();

      // Let's see...
      // We probably want to estimate P(a_i = j | t, s)
      // And we'll probably want to pick the j that makes the largest P

      for (int i = 0; i < targetWords.size(); i++) {
        // Start by assuming the best is NULL_WORD, then improve on this
	    String targetWord = targetWords.get(i);
	  
	    double bestAlignProb = probTgivenS.getCount(NULL_WORD, targetWord);
        for (int j = 0; j < sourceWords.size(); j++) {
          double prob = probTgivenS.getCount(sourceWords.get(j), targetWords.get(i));

          if (prob > bestAlignProb) {
	        bestAlignProb = prob;
		  }
	    }
        
        llh += Math.log(bestAlignProb);
	  }
	}
    
	return llh;
  }
}
