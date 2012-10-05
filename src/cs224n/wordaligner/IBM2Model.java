package cs224n.wordaligner;

import cs224n.util.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

/**
 * IBM 2 Model:
 * Attempt to learn probabilities of source words translating to
 * target words, depending on co-occurences found in the corpus.
 *
 * This time we also model q, a distortion-like value depending on
 * alignments found in the corpus.
 *
 * IMPORTANT: Make sure that you read the comments in the
 * cs224n.wordaligner.WordAligner interface.
 *
 * @author Dan Klein
 * @author Spence Green
 */
public class IBM2Model implements WordAligner {

  public static final double EXTREMELY_LARGE = 9999999;
  public static final int MAX_ATTEMPTS = 50;
  public static final double MIN_CHANGE = .01;

  private static final long serialVersionUID = 1315751943476440515L;

  // I want to know the prob of an t word given an s word (or NULL)
  // To initialize, I need to know the # of s words... (sources)
  // since it should be 1/(s+1)
  private CounterMap<String, String> probTgivenS;
  
  private CounterMap<String, String> qA_IgivenINM;

public IBM2Model() {
	super();

	System.out.println("HELLO");
	System.out.flush();
	probTgivenS = new CounterMap<String, String>();
	
	qA_IgivenINM = new CounterMap<String, String>();
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
  
  @SuppressWarnings("unchecked")
private void initialize(List<SentencePair> trainingPairs) {
	System.out.println("start loading IBM1 Model data");
	long start = System.currentTimeMillis();
	try {
	  FileInputStream fileIn = new FileInputStream("IBM1Model_probTgivenS.ser");
	  ObjectInputStream in = new ObjectInputStream(fileIn);
	  probTgivenS = (CounterMap<String, String>)in.readObject();
	  in.close();
	  fileIn.close();

	} catch (IOException e) {
		e.printStackTrace();
	} catch (ClassNotFoundException e) {
		e.printStackTrace();
	}
	
	System.out.println("Done loading IBM1 Model data in " + (System.currentTimeMillis() - start) + " ms");
  }

  public void train(List<SentencePair> trainingPairs) {
	initialize(trainingPairs);
   /*
    // Now for the real meat of the algorithm
    int attempts = 0;
    double maxDiff = EXTREMELY_LARGE;
    while (attempts < MAX_ATTEMPTS && maxDiff > MIN_CHANGE) {
      maxDiff = subtrain(trainingPairs, attempts);
      ++attempts;
	}*/
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
    return maxChange;
  }
}
