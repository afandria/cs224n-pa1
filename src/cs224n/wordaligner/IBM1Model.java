package cs224n.wordaligner;

import cs224n.util.*;
import java.util.List;

/**
 * IBM 1 Model:
 * Attempt to learn probabilities of source words translating to
 * target words, depending on alignments found in the corpus.
 *
 * IMPORTANT: Make sure that you read the comments in the
 * cs224n.wordaligner.WordAligner interface.
 *
 * @author Dan Klein
 * @author Spence Green
 */
public class IBM1Model implements WordAligner {

  public static final double EXTREMELY_LARGE = 9999999;
  public static final int MAX_ATTEMPTS = 50;
  public static final double MIN_CHANGE = .01;

  private static final long serialVersionUID = 1315751943476440515L;

  // All the source words
  private Counter<String> sourceCounts;
  // All the target words
  private Counter<String> targetCounts;

  // I want to know the prob of an t word given an s word (or NULL)
  // To initialize, I need to know the # of s words... (sources)
  // since it should be 1/(s+1)
  private CounterMap<String, String> probTgivenS;

  // Counts co-occurrences, but currently unused for IBM1Model
  private CounterMap<String,String> sourceTargetCounts;

  public IBM1Model() {
	super();
	
	sourceCounts = new Counter<String>();
	targetCounts = new Counter<String>();
	probTgivenS = new CounterMap<String, String>();
	sourceTargetCounts = new CounterMap<String, String>();

  }


  public Alignment align(SentencePair sentencePair) {
    // Placeholder code below.
    // TODO Implement an inference algorithm for Eq.1 in the assignment
    // handout to predict alignments based on the counts you collected with train().
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
        System.out.println("was null");
      }
	}

    /*for (int srcIndex = 0; srcIndex < numSourceWords; srcIndex++) {
      int tgtIndex = srcIndex;
      if (tgtIndex < numTargetWords) {
        // Discard null alignments
        alignment.addPredictedAlignment(tgtIndex, srcIndex);
      }
    }*/
    return alignment;
  }

  public void train(List<SentencePair> trainingPairs) {
	// Add NULL, so that it's in the source keyset
	sourceCounts.setCount(NULL_WORD, 0);

    // Begin by getting a rough count of the data
    for (SentencePair pair : trainingPairs) {
	  List<String> targetWords = pair.getTargetWords();
      List<String> sourceWords = pair.getSourceWords();
      for(String source : sourceWords){
        sourceCounts.incrementCount(source, 1.0);
        for(String target : targetWords){
          // Get basic statistics like co-occurrences
          targetCounts.incrementCount(target, 1.0);
          sourceTargetCounts.incrementCount(source, target, 1.0);
        }
      }
	}

	// Now we know all of the words that exist.
	// So we can initialize our probTgivenS CounterMap
	double initialValue = 1. / (sourceCounts.size() + 1);
	for (String source : sourceCounts.keySet()) {
      for (String target : targetCounts.keySet()) {
	    probTgivenS.setCount(source, target, initialValue);
	  }
	}

    // Now for the real meat of the algorithm
    int attempts = 0;
    double maxDiff = EXTREMELY_LARGE;
    while (attempts < MAX_ATTEMPTS && maxDiff > MIN_CHANGE) {
      maxDiff = subtrain(trainingPairs, attempts);
      ++attempts;
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

      // Add in a NULL for each sourceSentence
      // sourceWords.add(NULL_WORD); INSTeAD DO THE THING BELOW

      for(int j = 0; j < targetWords.size(); ++j) {
		// We need to find P(a_j = i | t, s)
		String t = targetWords.get(j);

		// To do so, first compute the sum of P(t_j | s_i) for all i
		double sum = 0;
		for (int i = 0; i < sourceWords.size(); ++i) {
			sum += probTgivenS.getCount(sourceWords.get(i), t);
		}
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
    for (String source : sourceCounts.keySet()) {
      // find the denominator
      // a sum over all target given this source
      double sum = 0;
      for (String target : targetCounts.keySet()) {
		sum += stAlignmentCounts.getCount(source, target);
	  }

	  // The numerator is just a single stAlignmentCount
	  for (String target : targetCounts.keySet()) {
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
    return maxChange;
  }
}
