package cs224n.wordaligner;

import cs224n.util.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
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
  public static final String SEP = "<+>";
  public static final double INCREASE_RATIO = 1.0005;
	
  public static final double EXTREMELY_LARGE = 99999999;
  public static final int MAX_ATTEMPTS = 100;
  public static final double MIN_CHANGE = .01;

  private static final long serialVersionUID = 1315751943476440515L;

  // I want to know the prob of an t word given an s word (or NULL)
  private CounterMap<String, String> probTgivenS;
  
  // Note that we choose A_I = length of sentence (appending NULL) to the training set
  private CounterMap<String, String> qA_IgivenINM;

public IBM2Model() {
	super();

	probTgivenS = new CounterMap<String, String>();
	qA_IgivenINM = new CounterMap<String, String>();
  }


  public Alignment align(SentencePair sentencePair) {
    Alignment alignment = new Alignment();
    List<String> targetWords = sentencePair.getTargetWords();
    List<String> sourceWords = sentencePair.getSourceWords();

    // Let's see...
    // We probably want to estimate P(a_i = j | t, s)
    // And we'll probably want to pick the j that makes the largest P

    for (int i = 0; i < targetWords.size(); i++) {
      // Start by assuming the best is NULL_WORD, then improve on this
	  String jStr = "" + sourceWords.size();
	  String inmStr = "" + i + SEP + sourceWords.size() + SEP + targetWords.size();
	  String targetWord = targetWords.get(i);
	  
	  int bestJ = -1; // null
	  double bestAlignProb = qA_IgivenINM.getCount(jStr, inmStr) * probTgivenS.getCount(NULL_WORD, targetWord);
      for (int j = 0; j < sourceWords.size(); j++) {
    	jStr = "" + j;
        double prob = qA_IgivenINM.getCount(jStr, inmStr) * probTgivenS.getCount(sourceWords.get(j), targetWords.get(i));

        if (prob > bestAlignProb) {
	      bestJ = j;
	      bestAlignProb = prob;
		}
	  }

	  if (bestJ != -1) {
	    alignment.addPredictedAlignment(i, bestJ);
      }
	}
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
	
	System.out.println("Selecting random starting probs for qA_IgivenINM");
    for (SentencePair pair : trainingPairs) {
      List<String> targetWords = pair.getTargetWords();
      List<String> sourceWords = pair.getSourceWords();
      for(int i = 0; i < targetWords.size(); i++){
      	String inmStr = "" + i + SEP + sourceWords.size() + SEP + targetWords.size();
        for(int j = 0; j < sourceWords.size(); j++) {
        	String jStr = "" + j;
        	qA_IgivenINM.setCount(jStr, inmStr, 1./(sourceWords.size() + 1));//Math.random());
        }
        qA_IgivenINM.setCount("" + sourceWords.size(), inmStr, 1./(sourceWords.size() + 1));//Math.random()); // also deal with NULL
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
  }

  // Performs 1 iteration of the IBM 1 Model
  // Returns the maximum change to the stored P's
  private double subtrain(List<SentencePair> trainingPairs, int attempts) {
	CounterMap<String, String> stAlignmentCounts = new CounterMap<String, String>();
	CounterMap<String, String> jilmAlignmentCounts = new CounterMap<String, String>();

	// For each SentencePair...
    for(SentencePair pair : trainingPairs) {
      List<String> targetWords = pair.getTargetWords();
      List<String> sourceWords = pair.getSourceWords();

      for(int i = 0; i < targetWords.size(); ++i) {
		String t = targetWords.get(i);
		
		String inmStr = "" + i + SEP + sourceWords.size() +SEP + targetWords.size();

		// We need to find d_kij, which we'll find by computing the denominator, then numerator
		// To do so, first compute the sum of q(j | i, n, m) * P(t_j | s_i) for all j
		double sum = 0;
		for (int j = 0; j < sourceWords.size(); ++j) {
			String s = sourceWords.get(j);
			String jStr = "" + j;
			sum += qA_IgivenINM.getCount(jStr, inmStr) * probTgivenS.getCount(s, t);
		}
		// and NULL
		sum += qA_IgivenINM.getCount("" + sourceWords.size(), inmStr) * probTgivenS.getCount(NULL_WORD, t);
		
		for (int j = 0; j < sourceWords.size(); ++j) {
			String s = sourceWords.get(j);
			String jStr = "" + j;
			
			double p = qA_IgivenINM.getCount(jStr, inmStr) * probTgivenS.getCount(s, t);
			double d_kij = p / sum;
			
			if (Double.isNaN(d_kij))
			  d_kij = 0;

			stAlignmentCounts.incrementCount(s, t, d_kij);
			jilmAlignmentCounts.incrementCount(jStr, inmStr, d_kij);
		}

		// Handle NULL
		double p = qA_IgivenINM.getCount("" + sourceWords.size(), inmStr) * probTgivenS.getCount(NULL_WORD, t);
		double d_kij = p / sum;
		if (Double.isNaN(d_kij))
		  d_kij = 0;
		stAlignmentCounts.incrementCount(NULL_WORD, t, d_kij);
		jilmAlignmentCounts.incrementCount("" + sourceWords.size(), inmStr, d_kij);
      }
    }

    // Maximum change (this is an absolute value)
    double maxChange = 0;

    // Now renormalize the P
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
        
        if (sum == 0) {
          newProb = 1./probTgivenS.getCounter(source).keySet().size();
        }
        if (Double.isNaN(newProb))
          System.out.println("We have a problem with NaN in P");

        // Update the maximum change value
        double change = Math.abs(probTgivenS.getCount(source, target) - newProb);
        if (change > maxChange) {
	      maxChange = change;
		}

        // Set the new probability!
        probTgivenS.setCount(source, target, newProb);
	  }
	}
    // Now renormalize the q, by finding c(inm), which means counting over all source indexes
    for (String inmStr : qA_IgivenINM.getCounter("0").keySet()) {
        // find the denominator
        // a sum over all target given this source
      double sum = 0;
      for (String jStr : qA_IgivenINM.keySet()) {
  		sum += jilmAlignmentCounts.getCount(jStr, inmStr);
  	  }

  	  // The numerator is just a single stAlignmentCount
  	  for (String jStr : qA_IgivenINM.keySet()) {
  		// Avoid storing a value for a mismatched jStr and inmStr
  		// You also do not need to update if the value will end up being 0 anyway.
  		if (jilmAlignmentCounts.getCount(jStr, inmStr) == 0) {
  		  continue;
  		}
  		  
        double newProb = jilmAlignmentCounts.getCount(jStr, inmStr) / sum;
        if (sum == 0)
        	newProb = 1./qA_IgivenINM.getCounter(jStr).keySet().size();
        if (Double.isNaN(newProb)) {
          System.out.println("NaN problem");
        }

        // Update the maximum change value (except not for q)
        double change = Math.abs(qA_IgivenINM.getCount(jStr, inmStr) - newProb);
        if (change > maxChange) {
          //maxChange = change;
  		}
        
        if (Math.random() < .00001)
          System.out.println("New Q: " + newProb + " Old Q: " + qA_IgivenINM.getCount(jStr, inmStr) + " " + jStr + "," + inmStr + " " + sum);

        // Set the new probability!
        qA_IgivenINM.setCount(jStr, inmStr, newProb);
  	  }
  	}

    System.out.println("Attempt #" + attempts + " Max change: " + maxChange);
    
    // Debug printout that is really only meaningful for French -> English
    System.out.println(probTgivenS.getCount("le", "the"));
    
    // Debug checking how likely the 1st word is still the 1st word in size-7 source and target sentences
    System.out.println(qA_IgivenINM.getCount("0", "0" + SEP + "7" + SEP + "7"));
    
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
	    String jStr = "" + sourceWords.size();
	    String inmStr = "" + i + SEP + sourceWords.size() + SEP + targetWords.size();
	    String targetWord = targetWords.get(i);
	  
	    double bestAlignProb = qA_IgivenINM.getCount(jStr, inmStr) * probTgivenS.getCount(NULL_WORD, targetWord);
        for (int j = 0; j < sourceWords.size(); j++) {
          jStr = "" + j;
          double prob = qA_IgivenINM.getCount(jStr, inmStr) * probTgivenS.getCount(sourceWords.get(j), targetWords.get(i));

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
